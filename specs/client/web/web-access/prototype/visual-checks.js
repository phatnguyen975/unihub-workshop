// Install the repo's axe-core script with context.addInitScript before running.
// Run with Playwright CLI; screenshots are written relative to its evidence cwd.
async (page) => {
  const base = "http://127.0.0.1:4181/";
  const results = [];
  const screenshots = [];
  const heading = async (text) =>
    page.getByRole("heading", { name: text, exact: true, level: 1 }).waitFor();
  const login = async () => {
    await page.getByLabel("Email", { exact: true }).fill("linh@example.test");
    await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await heading("Workshops");
  };
  for (const view of [
    "login",
    "temporary",
    "logout",
    "denied",
    "not-found",
    "register",
  ]) {
    await page.goto(base + "#/login");
    await page.getByRole("button", { name: "Reset", exact: true }).click();
    await page.locator("#sample").selectOption("student");
    await page.reload();
    await heading("Welcome back");
    if (view === "temporary") {
      await page.locator("#sample").selectOption("new-staff");
      await page
        .getByLabel("Email", { exact: true })
        .fill("new.staff@example.test");
      await page.getByLabel("Password", { exact: true }).fill("UniHubDemo1!");
      await page.getByRole("button", { name: "Sign in", exact: true }).click();
      await heading("Make this account yours");
    }
    if (view === "logout" || view === "denied") await login();
    if (view === "logout") {
      await page.locator("#scenario").selectOption("logout-failure");
      await page.getByRole("button", { name: "Account", exact: true }).click();
      await page.getByRole("button", { name: "Sign out", exact: true }).click();
      await heading("Sign out isn’t confirmed");
    }
    if (view === "denied") {
      await page
        .getByRole("link", { name: "Protected entry", exact: true })
        .click();
      await heading("You don’t have access to this page");
    }
    if (view === "not-found") {
      await page.goto(base + "#/unknown");
      await heading("We couldn’t find that page");
    }
    if (view === "register") {
      await page
        .getByRole("link", { name: "Create account", exact: true })
        .click();
      await heading("Create your student account");
    }
    for (const locale of ["en", "vi"]) {
      await page.locator("#locale").click();
      await page.locator(`[data-locale="${locale}"]`).click();
      for (const theme of ["light", "dark"]) {
        if ((await page.locator("html").getAttribute("data-theme")) !== theme)
          await page.locator("#theme").click();
        for (const width of [320, 390, 768, 1440]) {
          await page.setViewportSize({ width, height: 1000 });
          const audit = await page.evaluate(async () => {
            const overflow =
              document.documentElement.scrollWidth > window.innerWidth;
            const result = await axe.run(document, {
              runOnly: {
                type: "tag",
                values: ["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"],
              },
            });
            return {
              overflow,
              violations: result.violations.map((v) => ({
                id: v.id,
                nodes: v.nodes.map((n) => n.target),
              })),
            };
          });
          if (audit.overflow || audit.violations.length)
            throw new Error(
              JSON.stringify({ view, locale, theme, width, ...audit }),
            );
          results.push({ view, locale, theme, width });
          const selected =
            (view === "login" &&
              locale === "en" &&
              theme === "light" &&
              width === 1440) ||
            (view === "temporary" &&
              locale === "vi" &&
              theme === "dark" &&
              width === 390) ||
            (view === "logout" &&
              locale === "en" &&
              theme === "light" &&
              width === 768) ||
            (view === "denied" &&
              locale === "vi" &&
              theme === "dark" &&
              width === 320) ||
            (view === "not-found" &&
              locale === "vi" &&
              theme === "light" &&
              width === 390) ||
            (view === "register" &&
              locale === "en" &&
              theme === "dark" &&
              width === 1440);
          if (selected) {
            const filename = `${view}-${locale}-${theme}-${width}.png`;
            await page.screenshot({ path: filename, fullPage: true });
            screenshots.push(filename);
          }
        }
      }
    }
  }
  await page.getByRole("button", { name: "Reset", exact: true }).click();
  await page.reload();
  await heading("Welcome back");
  await login();
  await page.locator("#scenario").selectOption("logout-failure");
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await heading("Sign out isn’t confirmed");
  for (const [locale, theme, width] of [
    ["en", "light", 1440],
    ["vi", "dark", 320],
  ]) {
    await page.locator("#locale").click();
    await page.locator(`[data-locale="${locale}"]`).click();
    if ((await page.locator("html").getAttribute("data-theme")) !== theme)
      await page.locator("#theme").click();
    await page.setViewportSize({ width, height: 1000 });
    await page.locator("#remove-local").click();
    await page.getByRole("dialog").waitFor();
    const audit = await page.evaluate(async () => ({
      overflow: document.documentElement.scrollWidth > innerWidth,
      violations: (
        await axe.run(document, {
          runOnly: {
            type: "tag",
            values: ["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"],
          },
        })
      ).violations.map((v) => v.id),
    }));
    if (audit.overflow || audit.violations.length)
      throw new Error(
        JSON.stringify({
          view: "local-removal-dialog",
          locale,
          theme,
          width,
          ...audit,
        }),
      );
    const filename =
      locale === "en"
        ? "local-removal-dialog.png"
        : "local-removal-vi-dark-320.png";
    await page.screenshot({ path: filename, fullPage: true });
    screenshots.push(filename);
    await page.keyboard.press("Escape");
  }
  return {
    matrix: results.length,
    dialogChecks: 2,
    axeViolations: 0,
    pageOverflows: 0,
    screenshots,
  };
};
