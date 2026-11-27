import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("home opens the catalog and the featured carousel works by keyboard", async ({
  page,
}, info) => {
  await page.goto("/");
  await page
    .locator("main")
    .getByRole("link", { name: /explore workshops/i })
    .click();
  await expect(
    page.getByRole("heading", { name: "Explore university workshops" }),
  ).toBeVisible();
  const carousel = page.locator(".discovery-hero");
  await expect(carousel).toContainText("1 / 3");
  await carousel.getByRole("button", { name: "Next" }).focus();
  await page.keyboard.press("Enter");
  await expect(carousel).toContainText("2 / 3");
  await expect(page.locator(".workshop-grid article").first()).toBeVisible();
  await page.screenshot({
    path: info.outputPath("workshop-catalog.png"),
    fullPage: true,
  });
});

test("catalog distinguishes an empty dataset and recovers from a failed read", async ({
  page,
}) => {
  await page.goto("/workshops");
  await page.evaluate(() =>
    window.dispatchEvent(
      new CustomEvent("unihub:workshop-scenario", { detail: "empty" }),
    ),
  );
  await expect(page.getByText("There are no workshops yet.")).toBeVisible();
  await expect(page.locator(".discovery-hero > div:nth-child(2)")).toHaveCount(
    0,
  );
});

test("catalog recovers from a failed read", async ({ page }) => {
  await page.goto("/workshops");
  await page.evaluate(() =>
    window.dispatchEvent(
      new CustomEvent("unihub:workshop-scenario", { detail: "list-error" }),
    ),
  );
  await expect(page.getByRole("alert")).toContainText(
    "Could not load workshops.",
  );
  await page.getByRole("button", { name: "Retry" }).click();
  await expect(
    page.getByRole("heading", { name: "Explore university workshops" }),
  ).toBeVisible();
});

test("search, filters, cursor navigation and reload preserve the applied set", async ({
  page,
}) => {
  await page.goto("/workshops");
  await page.getByRole("textbox", { name: "Search workshops" }).fill("design");
  await page.getByRole("button", { name: "Search" }).click();
  await page.getByText("Price", { exact: true }).click();
  await page.getByRole("button", { name: "Paid", exact: true }).click();
  await expect(page.locator(".workshop-grid article")).toHaveCount(1);
  await expect(page.locator(".workshop-grid")).toContainText("Design Clinic");
  await page.getByText("Price", { exact: true }).click();
  await page.getByRole("button", { name: "All prices", exact: true }).click();
  await page
    .getByRole("navigation", { name: "Pagination" })
    .getByRole("button", { name: "Next" })
    .click();
  await expect(page).toHaveURL(/cursor=/);
  await page.reload();
  await expect(
    page.getByRole("textbox", { name: "Search workshops" }),
  ).toHaveValue("design");
  await expect(page.locator(".workshop-grid article")).toHaveCount(2);
  await page.goto("/workshops?q=design&cursor=stale");
  await expect(page).toHaveURL(/q=design/);
  await expect(page).not.toHaveURL(/cursor=stale/);
});

test("cursor moves forward and backward across three result sets", async ({
  page,
}) => {
  await page.goto("/workshops");
  const paging = page.getByRole("navigation", { name: "Pagination" });
  const next = paging.getByRole("button", { name: "Next" });
  const previous = paging.getByRole("button", { name: "Previous" });
  await next.click();
  await expect(page.locator(".workshop-grid article")).toHaveCount(3);
  await next.click();
  await expect(page.locator(".workshop-grid article")).toHaveCount(1);
  await previous.click();
  await expect(page.locator(".workshop-grid article")).toHaveCount(3);
  await previous.click();
  await expect(page).not.toHaveURL(/cursor=/);
});

test("reversed inclusive dates normalize to the workshop local calendar range", async ({
  page,
}) => {
  await page.goto("/workshops");
  await page.getByText("Dates", { exact: true }).click();
  await page.getByLabel("From").fill("2026-11-30");
  await page.getByLabel("To").fill("2026-11-29");
  await expect(page.getByLabel("From")).toHaveValue("2026-11-29");
  await expect(page.getByLabel("To")).toHaveValue("2026-11-30");
  await expect(page).toHaveURL(/from=2026-11-29/);
  await expect(page.locator(".workshop-grid article")).toHaveCount(3);
});

test("public details support direct entry, optional content and unavailable identifiers", async ({
  page,
}, info) => {
  await page.goto("/workshops/design-sprint");
  await expect(
    page.getByRole("heading", { name: "Design Sprint" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "View brochure" }),
  ).toHaveAttribute("href", "/mock/design-sprint-brochure.html");
  await expect(
    page.getByRole("link", { name: "Download brochure" }),
  ).toHaveAttribute("download", "");
  await page.screenshot({
    path: info.outputPath("workshop-detail-wide.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: info.outputPath("workshop-detail-narrow.png"),
    fullPage: true,
  });
  await page.goto("/workshops/design-access");
  await expect(
    page.getByRole("heading", { name: "Accessible Design Workshop" }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "View brochure" })).toHaveCount(
    0,
  );
  await page.goto("/workshops/missing");
  await expect(
    page.getByRole("heading", { name: "Workshop unavailable" }),
  ).toBeVisible();
  await page.goto("/workshops/private-draft");
  await expect(
    page.getByRole("heading", { name: "Workshop unavailable" }),
  ).toBeVisible();
});

test("return link restores the catalog cursor and criteria", async ({
  page,
}) => {
  await page.goto("/workshops?q=design");
  const nextPage = page
    .getByRole("navigation", { name: "Pagination" })
    .getByRole("button", { name: "Next" });
  await nextPage.click();
  const catalogUrl = page.url();
  await page.locator(".workshop-grid a").first().click();
  await expect(page).toHaveURL(/workshops\/[a-z0-9-]+$/);
  await page.getByRole("link", { name: "Back to workshops" }).click();
  await expect(page).toHaveURL(catalogUrl);
  await expect(
    page.getByRole("textbox", { name: "Search workshops" }),
  ).toHaveValue("design");
});

test("guest sign in returns to the selected public detail and respects roles and availability", async ({
  page,
}) => {
  await page.goto("/workshops/design-sprint");
  await page.getByRole("link", { name: "Sign in to continue" }).click();
  await expect(page).toHaveURL(/login\?next=/);
  await page.getByLabel("Email", { exact: true }).fill("linh@example.test");
  await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/workshops\/design-sprint$/);
  await expect(
    page.getByText("Registration is not available in this preview."),
  ).toBeVisible();
  await page.goto("/workshops/career-studio");
  await expect(page.getByText("Full", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Sign in to continue" }),
  ).toHaveCount(0);
});

test("public workshop detail remains open after the signed-in session expires", async ({
  page,
}) => {
  await page.goto("/workshops/design-sprint");
  await page.getByRole("link", { name: "Sign in to continue" }).click();
  await page.getByLabel("Email", { exact: true }).fill("linh@example.test");
  await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/workshops\/design-sprint$/);
  await page.getByRole("button", { name: "Revoke session" }).click();
  await expect(
    page.getByRole("heading", { name: "Design Sprint" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Sign in to continue" }),
  ).toBeVisible();
});

test("catalog keeps applied criteria across locale and theme changes and exposes dev scenarios outside content", async ({
  page,
}) => {
  await page.goto("/workshops?q=design");
  await expect(
    page.getByRole("textbox", { name: "Search workshops" }),
  ).toHaveValue("design");
  await expect(
    page.getByRole("complementary", { name: "Workshop review scenarios" }),
  ).toBeVisible();
  await expect(
    page.locator("main").getByLabel("Workshop scenario"),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Language" }).click();
  await page.getByRole("button", { name: "Vietnamese" }).click();
  await expect(page).toHaveURL(/q=design/);
  await page.locator(".header-actions > button.icon-button").first().click();
  await expect(page).toHaveURL(/q=design/);
  await page.getByLabel("Workshop scenario").selectOption("no-featured");
  await expect(page.locator(".workshop-grid article")).toHaveCount(3);
  for (const width of [320, 390, 650, 651, 768, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(
      page.getByText(
        "Hệ thống thiết kế cho nội dung dài và dịch vụ đại học đa ngôn ngữ",
        { exact: true },
      ),
    ).toBeVisible();
    const dimensions = await page.locator("#main").evaluate((main) => ({
      clientWidth: main.clientWidth,
      scrollWidth: main.scrollWidth,
    }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
    await expect(page).toHaveURL(/q=design/);
  }
  await page.getByRole("button", { name: "Reset workshop scenario" }).click();
  await expect(page.getByLabel("Workshop scenario")).toHaveValue("normal");
});

test("catalog and detail pass automated accessibility checks", async ({
  page,
}) => {
  await page.goto("/workshops");
  const catalogA11y = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(catalogA11y.violations).toEqual([]);
  await page.goto("/workshops/design-sprint");
  const detailA11y = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(detailA11y.violations).toEqual([]);
});

test("temporary password replacement preserves the validated detail intent", async ({
  page,
}) => {
  await page.goto("/workshops/design-sprint");
  await page.getByRole("link", { name: "Sign in to continue" }).click();
  await page
    .getByLabel("Email", { exact: true })
    .fill("new.staff@example.test");
  await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Make this account yours" }),
  ).toBeVisible();
  await page.getByLabel("New password", { exact: true }).fill("NextWorkshop2!");
  await page
    .getByLabel("Confirm new password", { exact: true })
    .fill("NextWorkshop2!");
  await page
    .getByRole("button", { name: "Update password & continue", exact: true })
    .click();
  await expect(page).toHaveURL(/workshops\/design-sprint$/);
  await expect(
    page.getByRole("heading", { name: "Design Sprint" }),
  ).toBeVisible();
});

for (const email of ["minh@example.test", "an@example.test"]) {
  test(`nonstudent ${email} can view public detail without a student action`, async ({
    page,
  }) => {
    await page.goto("/workshops/design-sprint");
    await page.getByRole("link", { name: "Sign in to continue" }).click();
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page).toHaveURL(/workshops\/design-sprint$/);
    await expect(
      page.getByRole("heading", { name: "Design Sprint" }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Sign in to continue" }),
    ).toHaveCount(0);
    await expect(
      page.getByText("Registration is not available in this preview."),
    ).toHaveCount(0);
  });
}
