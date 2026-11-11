import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("minimal app, accessibility and navigation recovery", async ({
  page,
  baseURL,
}, testInfo) => {
  const errors: string[] = [];
  const externalRequests: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  await page.route("**/*", async (route) => {
    const url = route.request().url();
    if (
      url.startsWith("http") &&
      new URL(url).origin !== new URL(baseURL!).origin
    ) {
      externalRequests.push(new URL(url).origin);
      await route.abort();
    } else await route.continue();
  });
  await page.goto("/");
  await expect(page).toHaveTitle("UniHub Web");
  const heading = page.getByRole("heading", { name: "UniHub Web", level: 1 });
  await expect(heading).toBeVisible();
  await expect(page.getByRole("main")).toHaveCount(1);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(
    await heading.evaluate((element) => getComputedStyle(element).fontSize),
  ).toBe("24px");
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("home.png"),
    fullPage: true,
  });
  await page.goto("/unknown-route");
  await expect(
    page.getByRole("heading", { name: "Page not found" }),
  ).toBeVisible();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Return home" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(heading).toBeVisible();
  await page.reload();
  await expect(heading).toBeVisible();
  expect(externalRequests).toEqual([]);
  expect(errors).toEqual([]);
});
