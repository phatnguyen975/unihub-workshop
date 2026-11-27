import { describe, expect, it } from "vitest";
import {
  normalizeSearch,
  normalizeDateRange,
  pageByCursor,
  criteriaKey,
  findWorkshops,
} from "./discovery";

describe("workshop search", () => {
  it("trims search text before applying it", () => {
    expect(normalizeSearch("  design  ")).toBe("design");
  });
});

describe("date range", () => {
  it("normalizes reversed dates and leaves incomplete selections unapplied", () => {
    expect(normalizeDateRange("2026-11-30", "2026-11-29")).toEqual([
      "2026-11-29",
      "2026-11-30",
    ]);
    expect(normalizeDateRange("2026-11-29", "")).toBeNull();
    expect(normalizeDateRange("2026-11-29", "2026-11-29")).toEqual([
      "2026-11-29",
      "2026-11-29",
    ]);
  });
});

describe("cursor pages", () => {
  it("binds continuation to criteria and recovers invalid cursors", () => {
    const criteria = { query: "", price: "", from: "", to: "" };
    const all = findWorkshops(criteria, "en");
    const first = pageByCursor(all, null, criteria, 3);
    expect(first.items).toHaveLength(3);
    const next = pageByCursor(all, first.nextCursor, criteria, 3);
    expect(next.items[0].slug).not.toBe(first.items[2].slug);
    expect(pageByCursor(all, "bad-cursor", criteria, 3).items).toEqual(
      first.items,
    );
    const paidCriteria = { ...criteria, price: "paid" };
    const paid = findWorkshops(paidCriteria, "en");
    expect(pageByCursor(paid, first.nextCursor, paidCriteria, 3).items).toEqual(
      paid.slice(0, 3),
    );
    expect(criteriaKey(criteria)).toBe(criteriaKey({ ...criteria }));
  });
});
