import { expect, test } from "@playwright/test";

test("production catalog and detail load without review controls or external requests", async ({
  page,
}) => {
  const errors: string[] = [];
  const externalRequests: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => {
    if (new URL(request.url()).origin !== "http://127.0.0.1:4174") {
      externalRequests.push(request.url());
    }
  });
  await page.goto("/workshops");
  await expect(
    page.getByRole("heading", { name: "Explore university workshops" }),
  ).toBeVisible();
  await expect(
    page.getByRole("complementary", { name: "Workshop review scenarios" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "Design Sprint" }).last().click();
  await expect(
    page.getByRole("heading", { name: "Design Sprint" }),
  ).toBeVisible();
  expect(errors).toEqual([]);
  expect(externalRequests).toEqual([]);
});
