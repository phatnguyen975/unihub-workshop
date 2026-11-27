import en from "../../src/shared/ui/en.json" with { type: "json" };
import vi from "../../src/shared/ui/vi.json" with { type: "json" };
import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
const password = "UniHubDemo1!";
async function login(
  page: import("@playwright/test").Page,
  email = "linh@example.test",
  secret = password,
) {
  await expect(
    page.getByRole("heading", { name: en.welcome, exact: true }),
  ).toBeVisible();
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(secret);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}
async function seed(
  page: import("@playwright/test").Page,
  scenario = "normal",
) {
  await page.addInitScript((value) => {
    if (!sessionStorage.getItem("unihub.access.mock.v1"))
      sessionStorage.setItem(
        "unihub.access.mock.v1",
        JSON.stringify({
          version: 1,
          scenario: value,
          now: 1794618000,
          session: null,
          lock: false,
          activated: {},
        }),
      );
  }, scenario);
}
test("public entry, keyboard validation, locale, appearance and navigation", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Your next step starts here." }),
  ).toBeVisible();
  await page
    .locator("main")
    .getByRole("link", { name: "Sign in", exact: true })
    .click();
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByLabel("Email", { exact: true })).toBeFocused();
  await page.getByLabel("Password", { exact: true }).fill("example");
  await page
    .getByRole("button", { name: "Show password", exact: true })
    .click();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute(
    "type",
    "text",
  );
  await expect(
    page.getByRole("button", { name: "Hide password", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Language", exact: true }).click();
  await page.getByRole("button", { name: "Vietnamese", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Chào mừng trở lại" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
  await page.goto("/not-found");
  await expect(
    page.getByRole("heading", { name: /find that page/ }),
  ).toBeVisible();
  expect(errors).toEqual([]);
});
for (const [email, path] of [
  ["linh@example.test", "/workshops"],
  ["minh@example.test", "/organizer/workshops"],
  ["an@example.test", "/staff/workshops"],
])
  test("role login and reload " + email, async ({ page }) => {
    await page.goto("/login");
    await login(page, email);
    await expect(page).toHaveURL(new RegExp(path + "$"));
    await expect(
      page.getByRole("button", { name: "Account", exact: true }),
    ).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole("button", { name: "Account", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Account", exact: true }).click();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(
      page.getByText("You’re signed out.", { exact: true }),
    ).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole("button", { name: "Account", exact: true }),
    ).toHaveCount(0);
  });
test("temporary replacement enters directly and survives reload", async ({
  page,
}) => {
  await page.goto("/login");
  await login(page, "new.staff@example.test");
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
  await expect(page).toHaveURL(/staff\/workshops$/);
  await expect(
    page.getByText("Your password has been updated. You’re signed in.", {
      exact: true,
    }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Account", exact: true }),
  ).toBeVisible();
});
test("logout lost response stays locked after reload, retry confirms", async ({
  page,
}) => {
  await seed(page, "logout-lost");
  await page.goto("/login");
  await login(page);
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Sign out isn’t confirmed" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Sign out isn’t confirmed" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Try again", exact: true }).click();
  await expect(
    page.getByText("You’re signed out.", { exact: true }),
  ).toBeVisible();
});
test("failed logout local removal is explicit, keyboard dialog restores focus", async ({
  page,
}) => {
  await seed(page, "logout-failure");
  await page.goto("/login");
  await login(page);
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page
    .getByRole("button", { name: "Remove local session", exact: true })
    .click();
  await expect(
    page.getByRole("dialog", { name: "Remove this local session?" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("button", { name: "Remove local session", exact: true }),
  ).toBeFocused();
  await page
    .getByRole("button", { name: "Remove local session", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Remove session", exact: true })
    .click();
  await expect(
    page.getByText(
      "Local session removed. Service sign out was not confirmed.",
      { exact: true },
    ),
  ).toBeVisible();
});
test("safe intent through adjacent boundary and encoded external rejection", async ({
  page,
}) => {
  await page.goto(
    "/login?next=" + encodeURIComponent("/workshops?q=design%20skills"),
  );
  await page.getByRole("link", { name: "Create account", exact: true }).click();
  await page
    .getByRole("link", { name: "Back to sign in", exact: true })
    .click();
  await page.reload();
  await login(page);
  await expect(page).toHaveURL(/workshops\?q=design%20skills$/);
});
test("responsive access forms respect both themes and locales", async ({
  page,
}, info) => {
  await page.goto("/login");
  for (const locale of ["en", "vi"]) {
    await page
      .getByRole("button", {
        name: locale === "en" ? "Language" : "Language",
        exact: true,
      })
      .click()
      .catch(async () => {
        await page
          .getByRole("button", { name: "Ngôn ngữ", exact: true })
          .click();
      });
    await page
      .getByRole("button", {
        name: locale === "en" ? "English" : "Vietnamese",
        exact: true,
      })
      .click();
    for (const dark of [false, true]) {
      const toggle = page.getByRole("button", {
        name: locale === "en" ? en.theme : vi.theme,
        exact: true,
      });
      if ((await toggle.getAttribute("aria-pressed")) !== String(dark))
        await toggle.click();
      for (const width of [320, 390, 768, 1440]) {
        await page.setViewportSize({ width, height: 900 });
        expect(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
        ).toBe(true);
        expect(
          (
            await new AxeBuilder({ page })
              .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
              .analyze()
          ).violations,
        ).toEqual([]);
        if (width === 1440 || width === 390)
          await page.screenshot({
            path: info.outputPath(
              `login-${locale}-${dark ? "dark" : "light"}-${width}.png`,
            ),
            fullPage: true,
          });
      }
    }
  }
});
for (const view of ["temporary", "logout", "denied", "not-found", "register"])
  test("state matrix " + view, async ({ page }, info) => {
    if (view === "logout") await seed(page, "logout-failure");
    await page.goto("/login");
    if (view === "temporary") {
      await login(page, "new.staff@example.test");
      await expect(
        page.getByRole("heading", { name: "Make this account yours" }),
      ).toBeVisible();
    }
    if (view === "logout" || view === "denied") {
      await login(page);
      await expect(
        page.getByRole("button", { name: "Account", exact: true }),
      ).toBeVisible();
    }
    if (view === "logout") {
      await page.getByRole("button", { name: "Account", exact: true }).click();
      await page.getByRole("button", { name: "Sign out", exact: true }).click();
      await expect(
        page.getByRole("heading", { name: en.logoutError }),
      ).toBeVisible();
    }
    if (view === "denied") {
      await page.goto("/organizer/workshops");
      await expect(
        page.getByRole("heading", { name: en.denied }),
      ).toBeVisible();
    }
    if (view === "not-found") await page.goto("/unknown");
    if (view === "register")
      await page
        .getByRole("link", { name: "Create account", exact: true })
        .click();
    for (const locale of ["en", "vi"]) {
      await page.locator(".locale-control > button").click();
      await page
        .getByRole("button", {
          name: locale === "en" ? "English" : "Vietnamese",
          exact: true,
        })
        .click();
      for (const dark of [false, true]) {
        const toggle = page.getByRole("button", {
          name: locale === "en" ? en.theme : vi.theme,
          exact: true,
        });
        if ((await toggle.getAttribute("aria-pressed")) !== String(dark))
          await toggle.click();
        for (const width of [320, 390, 768, 1440]) {
          await page.setViewportSize({ width, height: 900 });
          expect(
            await page.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth,
            ),
          ).toBe(true);
          expect(
            (
              await new AxeBuilder({ page })
                .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
                .analyze()
            ).violations,
          ).toEqual([]);
          if (
            (width === 390 && dark && locale === "vi") ||
            (width === 1440 && !dark && locale === "en")
          )
            await page.screenshot({
              path: info.outputPath(
                `${view}-${locale}-${dark ? "dark" : "light"}-${width}.png`,
              ),
              fullPage: true,
            });
        }
      }
    }
  });
for (const mode of ["invalid", "disabled", "offline", "unavailable", "rate"])
  test("visible typed failure " + mode, async ({ page }) => {
    await seed(page, mode);
    await page.goto("/login");
    await login(page);
    await expect(page.getByRole("alert")).toContainText(
      en[mode === "offline" ? "unavailable" : (mode as "invalid")],
    );
    await expect(
      page.getByRole("button", { name: "Account", exact: true }),
    ).toHaveCount(0);
    if (mode === "rate")
      await expect(
        page.getByRole("button", { name: "Sign in", exact: true }),
      ).toBeDisabled();
  });
test("fresh session has no misleading expiry notice and native browser offline rejects logout", async ({
  page,
}) => {
  await page.goto("/login");
  await expect(page.getByText(en.expired, { exact: true })).toHaveCount(0);
  await login(page);
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.context().setOffline(true);
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: en.logoutError }),
  ).toBeVisible();
  await page.context().setOffline(false);
  await page.getByRole("button", { name: "Try again", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
});
test("lost password response recovers with new credentials", async ({
  page,
}) => {
  await seed(page, "lost-password");
  await page.goto("/login");
  await login(page, "new.organizer@example.test");
  await page.getByLabel("New password", { exact: true }).fill("NextWorkshop2!");
  await page
    .getByLabel("Confirm new password", { exact: true })
    .fill("NextWorkshop2!");
  await page
    .getByRole("button", { name: "Update password & continue", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(en.unknownPassword);
  await page.getByRole("link", { name: en.signInNew, exact: true }).click();
  await login(page, "new.organizer@example.test", "NextWorkshop2!");
  await expect(page).toHaveURL(/organizer\/workshops$/);
});
test("temporary direct entry and reload scrub credentials", async ({
  page,
}) => {
  await page.goto("/login");
  await login(page, "new.staff@example.test");
  await expect(
    page.getByRole("heading", { name: en.temporaryTitle }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByLabel("Temporary password", { exact: true }),
  ).toHaveValue("");
  await expect(page.getByLabel("New password", { exact: true })).toHaveValue(
    "",
  );
});
