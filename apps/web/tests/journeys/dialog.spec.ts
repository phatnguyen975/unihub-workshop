import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import en from "../../src/shared/ui/en.json" with { type: "json" };
import vi from "../../src/shared/ui/vi.json" with { type: "json" };
test("local removal dialog at desktop and narrow dark viewport", async ({
  page,
}, info) => {
  await page.addInitScript(() =>
    sessionStorage.setItem(
      "unihub.access.mock.v1",
      JSON.stringify({
        version: 1,
        scenario: "logout-failure",
        now: 1794618000,
        session: null,
        lock: false,
        activated: {},
      }),
    ),
  );
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("linh@example.test");
  await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  for (const [locale, width] of [
    ["en", 1440],
    ["vi", 320],
  ] as const) {
    await page.locator(".locale-control > button").click();
    await page
      .getByRole("button", {
        name: locale === "en" ? "English" : "Vietnamese",
        exact: true,
      })
      .click();
    if (locale === "vi")
      await page.getByRole("button", { name: vi.theme, exact: true }).click();
    await page.setViewportSize({ width, height: 900 });
    await page
      .getByRole("button", {
        name: locale === "en" ? en.removeLocal : vi.removeLocal,
        exact: true,
      })
      .click();
    await expect(page.getByRole("dialog")).toBeVisible();
    const bounds = await page.getByRole("dialog").boundingBox();
    expect(bounds?.x).toBeGreaterThan(0);
    expect(bounds?.y).toBeGreaterThan(0);
    expect(
      (
        await new AxeBuilder({ page })
          .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
          .analyze()
      ).violations,
    ).toEqual([]);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: info.outputPath("local-removal-" + locale + ".png"),
      fullPage: true,
    });
    await page.keyboard.press("Escape");
  }
});
