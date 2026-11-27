import { describe, expect, it } from "vitest";
import { createWorkshopService } from "./service";

describe("workshop mock service", () => {
  it("returns the same unavailable result for missing and unpublished workshops", async () => {
    const service = createWorkshopService();
    expect(await service.detail("missing")).toBeNull();
    expect(await service.detail("private-draft")).toBeNull();
  });
  it("keeps optional detail fields optional and exposes the local brochure only when present", async () => {
    const service = createWorkshopService();
    const withoutOptions = await service.detail("design-access");
    const withBrochure = await service.detail("design-sprint");
    expect(withoutOptions?.description).toBeNull();
    expect(withoutOptions?.speakers).toEqual([]);
    expect(withoutOptions?.brochure).toBeNull();
    expect(withBrochure?.brochure).toBe("assets/design-sprint-brochure.html");
  });
  it("retries a detail read using the requested slug", async () => {
    const service = createWorkshopService();
    service.scenario("detail-error");
    await expect(service.detail("design-sprint")).rejects.toThrow();
    expect((await service.detail("design-sprint"))?.slug).toBe("design-sprint");
  });
  it("recovers a transient list failure on retry with the same criteria", async () => {
    const service = createWorkshopService();
    service.scenario("list-error");
    const criteria = { query: "design", price: "", from: "", to: "" };
    await expect(service.list(criteria, "en")).rejects.toThrow();
    const result = await service.list(criteria, "en");
    expect(result.items.length).toBeGreaterThan(0);
    expect(result.items.every((item) => item.published)).toBe(true);
  });
});
