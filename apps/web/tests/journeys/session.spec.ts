import { test, expect } from "@playwright/test";
import en from "../../src/shared/ui/en.json" with { type: "json" };
function state(
  session: { id: string; access: number; renew: number } | null,
  scenario = "normal",
) {
  return {
    version: 1,
    scenario,
    now: 1794618000,
    session,
    lock: false,
    activated: {},
  };
}
test("expired stored identity is cleared before protected content", async ({
  page,
}) => {
  await page.addInitScript(
    (value) =>
      sessionStorage.setItem("unihub.access.mock.v1", JSON.stringify(value)),
    state({ id: "student", access: 0, renew: 0 }),
  );
  await page.goto("/organizer/workshops");
  await expect(
    page.getByRole("heading", { name: en.welcome, exact: true }),
  ).toBeVisible();
  await expect(page.getByText(en.expired, { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: en.account, exact: true }),
  ).toHaveCount(0);
});
test("stored identity renews and fresh independent tab stays anonymous", async ({
  page,
}) => {
  await page.addInitScript(
    (value) =>
      sessionStorage.setItem("unihub.access.mock.v1", JSON.stringify(value)),
    state({ id: "student", access: 0, renew: 1794618600 }),
  );
  await page.goto("/login");
  await expect(
    page.getByRole("button", { name: en.account, exact: true }),
  ).toBeVisible();
  const other = await page.context().newPage();
  await other.addInitScript(() =>
    sessionStorage.removeItem("unihub.access.mock.v1"),
  );
  await other.goto("/organizer/workshops");
  await expect(
    other.getByRole("heading", { name: en.welcome, exact: true }),
  ).toBeVisible();
  await other.close();
});
test("offline restoration stays locked and explicit retry recovers", async ({
  page,
}) => {
  await page.addInitScript(
    (value) =>
      sessionStorage.setItem("unihub.access.mock.v1", JSON.stringify(value)),
    state({ id: "student", access: 0, renew: 1794618600 }, "slow"),
  );
  await page.goto("/workshops");
  await expect(
    page.getByRole("heading", { name: en.restoring, exact: true }),
  ).toBeVisible();
  await page.context().setOffline(true);
  await expect(
    page.getByRole("heading", { name: en.restoreError, exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: en.account, exact: true }),
  ).toHaveCount(0);
  await page.context().setOffline(false);
  await page.getByRole("button", { name: en.retry, exact: true }).click();
  await expect(
    page.getByRole("button", { name: en.account, exact: true }),
  ).toBeVisible();
});
test("corrupt tab state fails closed", async ({ page }) => {
  await page.addInitScript(() =>
    sessionStorage.setItem("unihub.access.mock.v1", "invalid json"),
  );
  await page.goto("/staff/workshops");
  await expect(
    page.getByRole("heading", { name: en.welcome, exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: en.account, exact: true }),
  ).toHaveCount(0);
});
