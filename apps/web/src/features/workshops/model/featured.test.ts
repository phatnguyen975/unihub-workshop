import { describe, expect, it } from "vitest";
import { featuredWorkshops } from "./discovery";

describe("featured workshops", () => {
  it("uses the fixture order and exposes only published entries", () => {
    const featured = featuredWorkshops();
    expect(featured.length).toBeGreaterThan(0);
    expect(featured.length).toBeLessThanOrEqual(3);
    expect(featured.every((workshop) => workshop.published)).toBe(true);
    expect(featured.map((workshop) => workshop.slug)).toEqual([
      "design-sprint",
      "ai-lab",
      "career-studio",
    ]);
  });
});
