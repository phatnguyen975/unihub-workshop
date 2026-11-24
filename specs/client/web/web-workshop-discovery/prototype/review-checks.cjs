// Run from the repository root with the static preview server running.
const assert = require("node:assert/strict");
const path = require("node:path");
const os = require("node:os");
const fs = require("node:fs");
const { chromium } = require("../../../../../apps/web/node_modules/playwright");
const {
  default: AxeBuilder,
} = require("../../../../../apps/web/node_modules/@axe-core/playwright");

const url =
  "http://127.0.0.1:4176/specs/client/web/web-workshop-discovery/prototype/index.html";
const evidence = fs.mkdtempSync(path.join(os.tmpdir(), "unihub-discovery-"));
const errors = [];
const external = [];

async function visible(locator) {
  await locator.waitFor({ state: "visible" });
}
async function scenario(page, value) {
  await page.locator("#scenario").selectOption(value);
}
async function fillQuery(page, value) {
  await page.locator("input[name=q]").fill(value);
  await page.locator("input[name=q]").press("Enter");
}
async function authenticate(page) {
  await page.getByLabel("Email").fill("student@example.test");
  await page.getByLabel("Password", { exact: true }).fill("Workshop26!");
  await page.locator("#login button[type=submit]").click();
}
async function inspect(page, name) {
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
    true,
    name + " overflow",
  );
  const results = await new AxeBuilder({ page }).analyze();
  assert.deepEqual(
    results.violations.map(({ id, nodes }) => [
      id,
      nodes.map((node) => node.target),
    ]),
    [],
    name,
  );
  await page.screenshot({
    path: path.join(evidence, name + ".png"),
    fullPage: true,
  });
}

(async () => {
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    page.setDefaultTimeout(6000);
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text());
    });
    page.on("response", (response) => {
      if (response.status() >= 400)
        errors.push(response.status() + " " + response.url());
    });
    page.on("request", (request) => {
      if (!request.url().startsWith("http://127.0.0.1:4176/"))
        external.push(request.url());
    });

    await page.goto(url);
    await page.getByRole("link", { name: "Explore workshops" }).click();
    await visible(page.getByRole("heading", { name: "Workshop catalog" }));
    await visible(page.locator(".card").first());
    await page
      .locator("#featured")
      .getByRole("button", { name: "Next" })
      .press("Enter");
    await visible(page.locator("#featured").getByText("2 / 3"));
    await inspect(page, "desktop-catalog");
    const pagination = page.getByRole("navigation", { name: "Page" });
    assert.equal(
      await pagination.getByRole("button", { name: "Previous" }).isDisabled(),
      true,
    );
    const dimensions = await pagination
      .locator(".page-control")
      .evaluateAll((nodes) =>
        nodes.map((node) => [node.offsetWidth, node.offsetHeight]),
      );
    assert.deepEqual(dimensions, [
      [44, 44],
      [44, 44],
    ]);
    await pagination.getByRole("link", { name: "Next" }).click();
    await visible(
      page.getByRole("link", { name: "Design Clinic", exact: true }),
    );
    const secondCursor = new URLSearchParams(page.url().split("?")[1]).get(
      "cursor",
    );
    await pagination.getByRole("link", { name: "Next" }).click();
    await visible(
      page.getByRole("link", {
        name: "Accessible Design Workshop",
        exact: true,
      }),
    );
    assert.equal(
      await pagination.getByRole("button", { name: "Next" }).isDisabled(),
      true,
    );
    await pagination.getByRole("link", { name: "Previous" }).click();
    await visible(
      page.getByRole("link", { name: "Design Clinic", exact: true }),
    );
    assert.equal(
      new URLSearchParams(page.url().split("?")[1]).get("cursor"),
      secondCursor,
    );
    await page.goto(
      url + "#/workshops?price=paid&cursor=" + encodeURIComponent(secondCursor),
    );
    await visible(
      page.getByRole("link", { name: "Applied AI Lab", exact: true }),
    );
    assert.ok(!page.url().includes("cursor="));
    await page.goto(url + "#/workshops");
    await visible(page.getByText("7 workshops"));
    for (const control of [
      page.locator("#account"),
      page.locator("#search button"),
    ]) {
      assert.equal(
        await control.evaluate(
          (node) => getComputedStyle(node).backgroundColor,
        ),
        "rgb(35, 75, 235)",
      );
      await control.hover();
      assert.equal(
        await control.evaluate(
          (node) => getComputedStyle(node).backgroundColor,
        ),
        "rgb(24, 56, 187)",
      );
    }
    await fillQuery(page, "x".repeat(201));
    await visible(page.getByText("Enter 200 characters or fewer."));
    await fillQuery(page, "design");
    await page.locator("#price-trigger").click();
    const positions = await page
      .locator("#price-popover button")
      .evaluateAll((nodes) =>
        nodes.map((node) => node.getBoundingClientRect().top),
      );
    assert.ok(
      positions.every((top, index) => !index || top > positions[index - 1]),
    );
    await page.getByRole("button", { name: "Free", exact: true }).click();
    await page
      .getByRole("navigation", { name: "Page" })
      .getByRole("link", { name: "Next" })
      .click();
    await visible(
      page.getByRole("link", {
        name: "Accessible Design Workshop",
        exact: true,
      }),
    );
    assert.ok(new URLSearchParams(page.url().split("?")[1]).has("cursor"));
    await page.locator(".card a").first().click();
    await visible(
      page.getByRole("heading", { name: "Accessible Design Workshop" }),
    );
    await page.goBack();
    await visible(
      page.getByRole("link", {
        name: "Accessible Design Workshop",
        exact: true,
      }),
    );
    await page.reload();
    await visible(
      page.getByRole("link", {
        name: "Accessible Design Workshop",
        exact: true,
      }),
    );
    await page.locator("#date-trigger").click();
    await page.getByRole("button", { name: /^29 ·/ }).click();
    await page.getByRole("button", { name: /^29 ·/ }).click();
    await visible(
      page.getByRole("link", { name: "Design Research After Hours" }),
    );
    assert.equal(await page.locator(".card").count(), 1);
    await fillQuery(page, "nothing matches");
    await page
      .getByRole("button", { name: "Clear search and filters" })
      .click();
    await visible(page.getByText("7 workshops"));
    await page.goto(url + "#/workshops?q=design&cursor=stale");
    await visible(page.getByText("5 workshops"));
    assert.ok(!page.url().includes("cursor="));
    await page.goto(url + "#/workshops/design-sprint");
    await visible(page.getByRole("link", { name: "View brochure" }));
    assert.ok(
      (await page
        .getByRole("link", { name: "Download brochure" })
        .getAttribute("download")) !== null,
    );
    await inspect(page, "desktop-detail");
    await page.getByRole("link", { name: "Sign in to continue" }).click();
    await inspect(page, "desktop-sign-in");
    await page.getByRole("button", { name: "Show password" }).click();
    assert.equal(await page.locator("#password").getAttribute("type"), "text");
    await page.getByRole("button", { name: "Hide password" }).click();
    await authenticate(page);
    await visible(
      page.getByText("Registration is not available in this preview."),
    );
    const avatar = page.getByRole("button", {
      name: "LN — Account: Linh Nguyen",
    });
    assert.equal(await page.locator("#account").isVisible(), false);
    await avatar.press("Enter");
    await visible(
      page.locator("#account-email").getByText("student@example.test"),
    );
    await inspect(page, "desktop-account-menu");
    await page.keyboard.press("Escape");
    assert.equal(await avatar.getAttribute("aria-expanded"), "false");
    assert.equal(
      await avatar.evaluate((node) => node === document.activeElement),
      true,
    );
    await avatar.click();
    await page
      .getByRole("heading", { name: "Design Sprint", exact: true })
      .click();
    assert.equal(await avatar.getAttribute("aria-expanded"), "false");
    await avatar.click();
    await page.getByRole("button", { name: "Sign out" }).click();
    await visible(page.getByRole("link", { name: "Sign in to continue" }));
    assert.equal(await avatar.count(), 0);
    await scenario(page, "temporary-password");
    await page.goto(url + "#/workshops/ai-lab");
    await page.getByRole("link", { name: "Sign in to continue" }).click();
    await authenticate(page);
    await page.getByLabel("New password").fill("Workshop27!");
    await page.getByRole("button", { name: "Continue" }).click();
    await visible(page.getByRole("heading", { name: "Applied AI Lab" }));
    await visible(
      page.getByText("Registration is not available in this preview."),
    );
    await scenario(page, "normal");
    await page.goto(
      url +
        "#/login?next=" +
        encodeURIComponent("https://example.test/workshops/ai-lab"),
    );
    await authenticate(page);
    await visible(page.getByRole("heading", { name: "Workshop catalog" }));
    await scenario(page, "normal");
    await page.goto(url + "#/workshops/career-studio");
    await visible(page.locator(".full").getByText("Full"));
    assert.equal(
      await page.getByRole("link", { name: "Sign in to continue" }).count(),
      0,
    );
    for (const identifier of ["missing", "private-draft"]) {
      await page.goto(url + "#/workshops/" + identifier);
      await visible(
        page.getByRole("heading", { name: "Workshop unavailable" }),
      );
    }
    await scenario(page, "empty");
    await visible(page.getByText("No workshops are available yet."));
    await scenario(page, "no-featured");
    await visible(page.locator(".card").first());
    assert.equal(await page.locator("#featured").isVisible(), false);
    await scenario(page, "list-error");
    await visible(page.getByText("Workshops couldn’t be loaded."));
    await page.getByRole("button", { name: "Try again" }).click();
    await visible(page.getByText("7 workshops"));
    await scenario(page, "detail-error");
    await page.goto(url + "#/workshops/design-sprint");
    await visible(page.getByText("This workshop couldn’t be loaded."));
    await page.getByRole("button", { name: "Try again" }).click();
    await visible(
      page.getByRole("heading", { name: "Design Sprint", exact: true }),
    );
    await scenario(page, "offline");
    await visible(
      page.getByText("Workshops are temporarily unavailable offline."),
    );
    await page.getByRole("button", { name: "Try again" }).click();
    await visible(page.getByText("7 workshops"));
    await scenario(page, "race");
    await fillQuery(page, "design");
    await page.waitForURL("**/index.html#/workshops?q=design");
    await visible(page.locator('input[name=q][value="design"]'));
    await fillQuery(page, "ai");
    await page.waitForURL("**/index.html#/workshops?q=ai");
    await visible(
      page.getByRole("link", { name: "Applied AI Lab", exact: true }),
    );
    await page.waitForTimeout(1600);
    assert.equal(await page.locator(".card").count(), 1);
    await scenario(page, "normal");
    await page.locator("#language").click();
    await page.getByRole("button", { name: "Vietnamese", exact: true }).click();
    await page.locator("#theme").click();
    for (const width of [320, 390, 650, 651, 768, 1440]) {
      await page.setViewportSize({ width, height: 844 });
      await visible(page.locator(".card").first());
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        true,
        "viewport " + width,
      );
    }
    await page.setViewportSize({ width: 320, height: 844 });
    await page.locator("#date-trigger").click();
    await inspect(page, "mobile-dark-calendar");
    await page.keyboard.press("Escape");
    assert.equal(
      await page
        .locator("#date-trigger")
        .evaluate((node) => node === document.activeElement),
      true,
    );
    await page.goto(url + "#/workshops/design-systems");
    await visible(
      page.getByRole("heading", {
        name: "Hệ thống thiết kế cho nội dung dài và dịch vụ đại học đa ngôn ngữ",
      }),
    );
    await inspect(page, "mobile-dark-detail");
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    console.log("PASS. Screenshots: " + evidence);
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
