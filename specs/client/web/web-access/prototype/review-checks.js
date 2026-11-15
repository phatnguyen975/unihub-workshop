// Run using the installed Playwright CLI: run-code --filename=<this file>.
// A design-preview regression replay, not production capability verification.
async (page) => {
  const base = "http://127.0.0.1:4181/";
  const passed = [];
  const errors = [];
  const external = [];
  const check = (condition, message) => {
    if (!condition) throw new Error(message);
  };
  const heading = async (text) =>
    page.getByRole("heading", { name: text, exact: true, level: 1 }).waitFor();
  const reset = async (account = "student") => {
    await page.getByRole("button", { name: "Reset", exact: true }).click();
    await page.locator("#sample").selectOption(account);
    await heading("Welcome back");
  };
  const login = async (
    email = "linh@example.test",
    password = "UniHubDemo1!",
  ) => {
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
  };
  const signOut = async () => {
    await page.getByRole("button", { name: "Account", exact: true }).click();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
  };
  const scenario = async (id) => page.locator("#scenario").selectOption(id);
  const replace = async (password) => {
    await page.getByLabel("New password", { exact: true }).fill(password);
    await page
      .getByLabel("Confirm new password", { exact: true })
      .fill(password);
    await page
      .getByRole("button", { name: "Update password & continue", exact: true })
      .click();
  };
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("request", (request) => {
    if (
      request.url().startsWith("http") &&
      new URL(request.url()).origin !== new URL(base).origin
    )
      external.push(request.url());
  });
  await page.context().setOffline(false);
  await page.goto(base);
  await page.reload();
  await reset();
  await page.getByRole("link", { name: "UniHub home", exact: true }).click();
  await heading("Your next step starts here.");
  await page
    .locator("main")
    .getByRole("link", { name: "Sign in", exact: true })
    .click();
  await heading("Welcome back");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  check(
    await page
      .locator("#email")
      .evaluate((el) => el === document.activeElement),
    "Invalid email must receive focus",
  );
  await page.getByLabel("Email", { exact: true }).fill("linh@example.test");
  await page.getByLabel("Password", { exact: true }).fill("not-correct");
  await page
    .getByRole("button", { name: "Show password", exact: true })
    .click();
  check(
    (await page.locator("#password").getAttribute("type")) === "text",
    "Password toggle",
  );
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page
    .getByText("The email or password is incorrect. Try again.", {
      exact: true,
    })
    .waitFor();
  check(
    (await page.locator("#password").inputValue()) === "",
    "Failed login clears secret",
  );
  passed.push(
    "WA-01/02/04: entry, validation focus, password visibility, credentials failure",
  );

  for (const [account, email, title, path] of [
    ["student", "linh@example.test", "Workshops", "/workshops"],
    ["organizer", "minh@example.test", "Workshops", "/organizer/workshops"],
    ["staff", "an@example.test", "Assigned workshops", "/staff/workshops"],
  ]) {
    await reset(account);
    await login(email);
    await heading(title);
    check(new URL(page.url()).hash === "#" + path, "Role landing: " + account);
    await page.reload();
    await heading(title);
    await signOut();
    await heading("Your next step starts here.");
  }
  passed.push(
    "WA-03/09/14/20: three roles, reload persistence and confirmed logout",
  );

  await reset("new-organizer");
  await login("new.organizer@example.test");
  await heading("Make this account yours");
  check(
    (await page.locator("#currentPassword").count()) === 0,
    "Uninterrupted temporary credential need not be re-entered",
  );
  await replace("NextWorkshop2!");
  await heading("Workshops");
  await page
    .getByText("Your password has been updated. You’re signed in.", {
      exact: true,
    })
    .waitFor();
  await page.reload();
  await heading("Workshops");
  await signOut();
  await heading("Your next step starts here.");
  await page
    .locator("main")
    .getByRole("link", { name: "Sign in", exact: true })
    .click();
  await login("new.organizer@example.test", "NextWorkshop2!");
  await heading("Workshops");
  passed.push(
    "WA-05/07: temporary password, direct session and new credentials after reload",
  );

  await reset("new-staff");
  await login("new.staff@example.test");
  await heading("Make this account yours");
  await page.reload();
  await heading("Make this account yours");
  await page.getByLabel("Temporary password", { exact: true }).waitFor();
  check(
    (await page
      .getByLabel("Temporary password", { exact: true })
      .inputValue()) === "",
    "Temporary secret not persisted",
  );
  await page
    .getByLabel("Email", { exact: true })
    .fill("new.staff@example.test");
  await page
    .getByLabel("Temporary password", { exact: true })
    .fill("UniHubDemo1!");
  await replace("weak");
  await page
    .getByText("Your new password must meet the requirements below.", {
      exact: true,
    })
    .waitFor();
  await scenario("lost-password");
  await replace("NextWorkshop2!");
  await page
    .getByText("Your password may already have changed", { exact: true })
    .waitFor();
  await page
    .getByRole("link", { name: "Sign in with new password", exact: true })
    .click();
  await login("new.staff@example.test", "NextWorkshop2!");
  await heading("Assigned workshops");
  passed.push(
    "WA-06/08: reload credential recovery, complexity and uncertain committed replacement",
  );

  await reset();
  await login();
  await heading("Workshops");
  await page
    .getByRole("button", { name: "Advance clock +61s", exact: true })
    .click();
  await heading("Workshops");
  await page
    .getByRole("button", { name: "Revoke session", exact: true })
    .click();
  await heading("Welcome back");
  await page
    .getByText("Your session has ended. Sign in again to continue.", {
      exact: true,
    })
    .waitFor();
  await page.goto(base + "#/staff/workshops");
  await heading("Welcome back");
  await reset();
  await scenario("offline");
  await login();
  await page
    .getByText(
      "We can’t connect right now. Check your connection and try again.",
      { exact: true },
    )
    .waitFor();
  await scenario("normal");
  await login();
  await heading("Workshops");
  await page.context().setOffline(true);
  await signOut();
  await heading("Sign out isn’t confirmed");
  await page.context().setOffline(false);
  await scenario("normal");
  await page.getByRole("button", { name: "Try again", exact: true }).click();
  await heading("Your next step starts here.");
  passed.push(
    "WA-09/10/11: renewal, revocation, injected offline and real browser offline logout",
  );

  await reset();
  await scenario("rate");
  await login();
  await page
    .getByRole("alert")
    .filter({
      hasText: "Too many attempts. Wait a moment before trying again.",
    })
    .waitFor();
  check(
    await page
      .getByRole("button", { name: "Sign in", exact: true })
      .isDisabled(),
    "Rate-limited submission disabled",
  );
  await scenario("normal");
  await page.waitForFunction(
    () => !document.querySelector("#auth-form [type=submit]").disabled,
  );
  await login();
  await heading("Workshops");
  await page.goto(base + "#/organizer/workshops");
  await heading("You don’t have access to this page");
  await page
    .getByText("Choose a page available to your account.", { exact: true })
    .waitFor();
  await page.goto(base + "#/no-such-route");
  await heading("We couldn’t find that page");
  passed.push("WA-12/13: bounded retry, denied route, not found");

  for (const mode of ["logout-failure", "logout-lost"]) {
    await reset();
    await login();
    await heading("Workshops");
    await scenario(mode);
    await signOut();
    await heading("Sign out isn’t confirmed");
    check(
      (await page
        .getByRole("button", { name: "Account", exact: true })
        .count()) === 0,
      "No identity menu while locked",
    );
    await page.reload();
    await heading("Sign out isn’t confirmed");
    if (mode === "logout-lost") {
      await page
        .getByRole("button", { name: "Try again", exact: true })
        .click();
      await heading("Your next step starts here.");
    } else {
      await page
        .getByRole("button", { name: "Remove local session", exact: true })
        .click();
      await page.getByRole("dialog").waitFor();
      await page.screenshot({
        path: "local-removal-dialog.png",
        fullPage: true,
      });
      await page.keyboard.press("Escape");
      check(
        await page
          .getByRole("button", { name: "Remove local session", exact: true })
          .evaluate((el) => el === document.activeElement),
        "Dialog restores focus",
      );
      await page
        .getByRole("button", { name: "Remove local session", exact: true })
        .click();
      await page
        .getByRole("button", { name: "Remove session", exact: true })
        .click();
      await heading("Your next step starts here.");
      await page
        .getByText(
          "Local session removed. Service sign out was not confirmed.",
          { exact: true },
        )
        .waitFor();
    }
  }
  passed.push(
    "WA-15/16: reload-safe logout lock, retry after lost response, local removal and dialog Escape/focus",
  );

  await reset();
  await scenario("slow");
  await login();
  await page.locator("#auth-form [type=submit]").waitFor({ state: "visible" });
  check(
    await page.locator("#auth-form [type=submit]").isDisabled(),
    "Duplicate submit blocked",
  );
  await page
    .getByRole("link", { name: "Forgot password?", exact: true })
    .click();
  await heading("Recover your account");
  await page.waitForTimeout(2700);
  check(
    (await page
      .getByRole("button", { name: "Account", exact: true })
      .count()) === 0,
    "Late login did not establish a session",
  );
  await page
    .getByRole("link", { name: "Back to sign in", exact: true })
    .click();
  await heading("Welcome back");
  await page.getByRole("link", { name: "Create account", exact: true }).click();
  await heading("Create your student account");
  passed.push(
    "WA-17/18: stale login cancellation and adjacent auth boundaries",
  );

  await reset();
  await login();
  await heading("Workshops");
  await scenario("offline");
  await page.reload();
  await heading("We couldn’t check your session");
  await scenario("normal");
  await page.getByRole("button", { name: "Try again", exact: true }).click();
  await heading("Workshops");
  await reset("missing-org");
  await login("unscoped@example.test");
  await heading("You don’t have access to this page");
  await page
    .getByText(
      "We couldn’t verify your organization. Contact your organization for help.",
      { exact: true },
    )
    .waitFor();
  await reset();
  await scenario("disabled");
  await login();
  await page
    .getByText(
      "This account is not active. Contact your organization for help.",
      { exact: true },
    )
    .waitFor();
  await reset();
  await scenario("storage");
  await login();
  await heading("Workshops");
  await page
    .getByText(
      "This tab can’t save your session. Reloading will require you to sign in again.",
      { exact: true },
    )
    .waitFor();
  await page.reload();
  await heading("Workshops");
  check(
    (await page
      .getByRole("button", { name: "Account", exact: true })
      .count()) === 0,
    "Denied persistence must not restore stale identity",
  );
  await reset();
  await page.goto(
    base + "#/login?next=" + encodeURIComponent("https://example.test/steal"),
  );
  await login();
  await heading("Workshops");
  check(new URL(page.url()).hash === "#/workshops", "Unsafe intent rejected");
  await reset();
  await page.goto(
    base + "#/login?next=" + encodeURIComponent("/workshops?q=design%20skills"),
  );
  await page.getByRole("link", { name: "Create account", exact: true }).click();
  await heading("Create your student account");
  await page
    .getByRole("link", { name: "Back to sign in", exact: true })
    .click();
  await heading("Welcome back");
  await page.reload();
  await heading("Welcome back");
  await login();
  await heading("Workshops");
  check(
    new URL(page.url()).hash === "#/workshops?q=design%20skills",
    "Registered query intent preserved",
  );
  passed.push(
    "WA-04/11/13/20: disabled account, restore recovery, missing organization, storage failure and safe return intent",
  );

  await reset();
  await page.getByLabel("Email", { exact: true }).fill("typed@example.test");
  await page.getByRole("button", { name: "Language", exact: true }).click();
  await page.getByRole("button", { name: "Vietnamese", exact: true }).click();
  await heading("Chào mừng trở lại");
  check(
    (await page.locator("#email").inputValue()) === "typed@example.test",
    "Locale preserves authored input",
  );
  await page.reload();
  await heading("Welcome back");
  await reset();
  await page.keyboard.press("Tab");
  check(
    await page
      .locator("#email")
      .evaluate((el) => el === document.activeElement),
    "Keyboard reaches email from heading",
  );
  await page.keyboard.type("linh@example.test");
  await page.keyboard.press("Tab");
  check(
    await page
      .locator("#password")
      .evaluate((el) => el === document.activeElement),
    "Keyboard reaches password",
  );
  await page.keyboard.type("UniHubDemo1!");
  await page.keyboard.press("Tab");
  await page.keyboard.press("Space");
  check(
    (await page.locator("#password").getAttribute("type")) === "text",
    "Keyboard toggles password visibility",
  );
  await page.keyboard.press("Tab");
  await page.keyboard.press("Tab");
  await page.keyboard.press("Enter");
  await heading("Workshops");
  await page.locator("#skip").focus();
  await page.keyboard.press("Enter");
  check(
    await page.locator("main").evaluate((el) => el === document.activeElement),
    "Skip link focuses main without changing route",
  );
  await reset("organizer");
  await login("minh@example.test");
  await heading("Workshops");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Menu", exact: true }).click();
  check(
    await page.locator("#organizer-nav").isVisible(),
    "Narrow organizer menu expands",
  );
  await page.screenshot({ path: "organizer-menu-390.png", fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await reset();
  const otherTab = await page.context().newPage();
  await otherTab.goto(base + "#/organizer/workshops");
  await otherTab
    .getByRole("heading", { name: "Welcome back", exact: true, level: 1 })
    .waitFor();
  await otherTab.close();
  check(errors.length === 0, "Browser errors: " + errors.join("; "));
  check(
    external.length === 0,
    "Unexpected external requests: " + external.join("; "),
  );
  passed.push(
    "WA-19/20: input-preserving locale, English reload, keyboard login/skip, narrow menu, independent tab and no external requests",
  );
  return { passed, errors, externalRequests: external };
};
