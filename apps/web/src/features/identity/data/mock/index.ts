import { z } from "zod";
import fixtures from "./fixtures.json";
import type { Identity, Role } from "../../model/access";
import {
  AccessError,
  type IdentityService,
  type StoragePort,
} from "../../services/contract";
const schema = z.object({
  version: z.literal(1),
  scenario: z.string(),
  now: z.number(),
  session: z
    .object({ id: z.string(), access: z.number(), renew: z.number() })
    .nullable(),
  lock: z.boolean(),
  activated: z.record(z.string(), z.string()),
});
const key = "unihub.access.mock.v1";
export interface MockOptions {
  storage?: StoragePort;
  delay?: (ms: number) => Promise<void>;
  online?: () => boolean;
}
export function createMockIdentity(options: MockOptions = {}) {
  const storage = options.storage ?? {
    getItem: (k: string) => sessionStorage.getItem(k),
    setItem: (k: string, v: string) => sessionStorage.setItem(k, v),
    removeItem: (k: string) => sessionStorage.removeItem(k),
  };
  const blank = () => ({
    version: 1 as const,
    scenario: "normal",
    now: fixtures.clockOrigin,
    session: null as {
      id: string;
      access: number;
      renew: number;
    } | null,
    lock: false,
    activated: {} as Record<string, string>,
  });
  let db = blank();
  let failed = false;
  try {
    const saved = schema.safeParse(JSON.parse(storage.getItem(key) ?? "null"));
    if (saved.success) db = saved.data;
  } catch {
    failed = true;
  }
  const accounts = fixtures.accounts as Identity[];
  if (db.session && !accounts.some((a) => a.id === db.session?.id))
    db.session = null;
  const save = () => {
    try {
      if (db.scenario === "storage") throw Error("Synthetic failure");
      storage.setItem(key, JSON.stringify(db));
      failed = false;
    } catch {
      failed = true;
      try {
        storage.removeItem(key);
      } catch {
        /* In-memory fallback. */
      }
    }
  };
  const issue = (a: Identity) => {
    db.session = {
      id: a.id,
      access: db.now + fixtures.accessSeconds,
      renew: db.now + fixtures.renewalSeconds,
    };
    db.lock = false;
    save();
  };
  const current = () => accounts.find((a) => a.id === db.session?.id) ?? null;
  const delay =
    options.delay ??
    ((ms) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  const wait = async () => {
    const scenario = db.scenario;
    await delay(scenario === "slow" ? 2500 : fixtures.latencyMs);
    if (
      !(options.online?.() ?? navigator.onLine) ||
      ["offline", "unavailable"].includes(scenario)
    )
      throw new AccessError("unavailable");
    if (scenario === "rate") throw new AccessError("rate");
    return scenario;
  };
  const digest = async (value: string) =>
    Array.from(
      new Uint8Array(
        await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)),
      ),
      (b) => b.toString(16).padStart(2, "0"),
    ).join("");
  const check = async (email: string, password: string) => {
    const a = accounts.find((a) => a.email === email);
    if (
      !a ||
      (db.activated[a.id]
        ? (await digest(password)) !== db.activated[a.id]
        : password !== fixtures.demoPassword)
    )
      throw new AccessError("invalid");
    return a;
  };
  const service: IdentityService = {
    current,
    locked: () => db.lock,
    storageFailed: () => failed,
    async login(email, password, valid) {
      const mode = await wait();
      if (!valid()) return null;
      const a = await check(email, password);
      if (!valid()) return null;
      if (mode === "invalid") throw new AccessError("invalid");
      if (mode === "disabled") throw new AccessError("disabled");
      const identity = {
        ...a,
        role: a.role as Role,
        temporary: a.temporary && !db.activated[a.id],
      };
      if (!identity.temporary) issue(identity);
      return identity;
    },
    async replace(email, password, next, valid) {
      const mode = await wait();
      if (!valid()) return null;
      const a = await check(email, password);
      if (!a.temporary || db.activated[a.id])
        throw new AccessError("notTemporary");
      const verifier = await digest(next);
      if (!valid()) return null;
      db.activated[a.id] = verifier;
      save();
      if (mode === "lost-password") throw new AccessError("unknownPassword");
      issue(a);
      return { ...a, temporary: false };
    },
    async restore(valid) {
      if (!db.session) return null;
      await wait();
      if (!valid()) return null;
      if (db.session.renew <= db.now) {
        this.clear();
        return null;
      }
      if (db.session.access <= db.now) {
        db.session.access = db.now + fixtures.accessSeconds;
        save();
      }
      const a = current();
      return a ? { ...a, temporary: false } : null;
    },
    async logout(valid) {
      const mode = await wait();
      if (!valid()) return;
      if (mode === "logout-failure") throw new AccessError("unavailable");
      if (mode === "logout-lost" && db.session) {
        db.session = null;
        save();
        throw new AccessError("unavailable");
      }
      this.clear();
    },
    lock() {
      db.lock = true;
      save();
    },
    clear() {
      db.session = null;
      db.lock = false;
      save();
    },
  };
  return Object.assign(service, {
    scenario(value: string) {
      db.scenario = value;
      save();
    },
    advance() {
      db.now += 61;
      save();
    },
    revoke() {
      if (db.session) {
        db.session.renew = db.now - 1;
        db.session.access = db.now - 1;
        save();
      }
    },
    reset() {
      db = blank();
      save();
    },
    accounts: fixtures.accounts,
    scenarios: fixtures.scenarios,
  });
}
export type MockIdentityService = ReturnType<typeof createMockIdentity>;
