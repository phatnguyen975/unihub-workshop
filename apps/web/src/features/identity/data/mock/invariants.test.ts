import { describe, it, expect, vi } from "vitest";
import { createMockIdentity } from "./index";
import { AccessController } from "../../services/controller";
import type { StoragePort } from "../../services/contract";
const store = (): StoragePort => {
  const data = new Map<string, string>();
  return {
    getItem: (k) => data.get(k) ?? null,
    setItem: (k, v) => {
      data.set(k, v);
    },
    removeItem: (k) => {
      data.delete(k);
    },
  };
};
const create = (storage = store()) =>
  createMockIdentity({ storage, delay: async () => {} });
describe("failure, persistence and renewal invariants", () => {
  it.each(["offline", "unavailable", "rate", "disabled", "invalid"])(
    "does not authenticate on %s",
    async (scenario) => {
      const service = create();
      service.scenario(scenario);
      const c = new AccessController(service, () => {});
      await expect(
        c.login("linh@example.test", "UniHubDemo1!"),
      ).rejects.toThrow();
      expect(service.current()).toBeNull();
    },
  );
  it("generic credential failures do not distinguish missing email", async () => {
    const c = new AccessController(create(), () => {});
    await expect(c.login("missing@example.test", "anything")).rejects.toThrow(
      "invalid",
    );
    await expect(c.login("linh@example.test", "wrong")).rejects.toThrow(
      "invalid",
    );
  });
  it("restores and renews through one coordinated operation", async () => {
    const service = create();
    const clear = vi.fn();
    const c = new AccessController(service, clear);
    await c.login("linh@example.test", "UniHubDemo1!");
    service.advance();
    const restore = vi.spyOn(service, "restore");
    await Promise.all([c.restore(), c.restore()]);
    expect(restore).toHaveBeenCalledTimes(1);
    expect(c.getSnapshot().kind).toBe("authenticated");
    service.revoke();
    await c.restore();
    expect(c.getSnapshot().kind).toBe("anonymous");
    expect(clear).toHaveBeenCalled();
  });
  it("keeps unavailable restore locked until explicit recovery", async () => {
    const service = create();
    const c = new AccessController(service, () => {});
    await c.login("linh@example.test", "UniHubDemo1!");
    service.scenario("offline");
    await c.restore();
    expect(c.getSnapshot().kind).toBe("restore-unavailable");
    service.scenario("normal");
    await c.restore();
    expect(c.getSnapshot().kind).toBe("authenticated");
  });
  it("never restores malformed or unknown-account storage", async () => {
    for (const value of [
      "not json",
      JSON.stringify({ version: 99 }),
      JSON.stringify({
        version: 1,
        scenario: "normal",
        now: 1,
        lock: false,
        activated: {},
        session: { id: "intruder", access: 10, renew: 20 },
      }),
    ]) {
      const storage = store();
      storage.setItem("unihub.access.mock.v1", value);
      const c = new AccessController(create(storage), () => {});
      await c.restore();
      expect(c.getSnapshot().kind).toBe("anonymous");
    }
  });
  it("uses memory safely when storage operations throw", async () => {
    const storage: StoragePort = {
      getItem: () => {
        throw Error("Denied");
      },
      setItem: () => {
        throw Error("Denied");
      },
      removeItem: () => {
        throw Error("Denied");
      },
    };
    const service = create(storage);
    const c = new AccessController(service, () => {});
    await c.login("linh@example.test", "UniHubDemo1!");
    expect(c.getSnapshot().kind).toBe("authenticated");
    expect(service.storageFailed()).toBe(true);
    c.clearLocal();
    expect(service.current()).toBeNull();
  });
  it("isolates fresh tab storage", async () => {
    const c = new AccessController(create(), () => {});
    await c.login("linh@example.test", "UniHubDemo1!");
    const next = new AccessController(create(), () => {});
    await next.restore();
    expect(next.getSnapshot().kind).toBe("anonymous");
  });
  it("completed replacement is single use and immediately authenticated", async () => {
    const service = create();
    const c = new AccessController(service, () => {});
    await c.login("new.staff@example.test", "UniHubDemo1!");
    expect(c.getSnapshot().kind).toBe("temporary");
    await c.replace("new.staff@example.test", "UniHubDemo1!", "NextWorkshop2!");
    expect(c.getSnapshot().kind).toBe("authenticated");
    await expect(
      c.replace("new.staff@example.test", "UniHubDemo1!", "OtherPass3!"),
    ).rejects.toThrow();
    await c.login("new.staff@example.test", "NextWorkshop2!");
    expect(c.getSnapshot().kind).toBe("authenticated");
  });
  it("recovers committed replacement after response is lost", async () => {
    const service = create();
    service.scenario("lost-password");
    const c = new AccessController(service, () => {});
    await c.login("new.organizer@example.test", "UniHubDemo1!");
    await expect(
      c.replace("new.organizer@example.test", "UniHubDemo1!", "NextWorkshop2!"),
    ).rejects.toThrow("unknownPassword");
    expect(service.current()).toBeNull();
    await c.login("new.organizer@example.test", "NextWorkshop2!");
    expect(c.getSnapshot().kind).toBe("authenticated");
  });
});
