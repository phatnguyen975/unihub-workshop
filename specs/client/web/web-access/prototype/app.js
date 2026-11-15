import { createFake } from "./service.js";

const $ = (selector) => document.querySelector(selector);
const escape = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
const icons = {
  eyeOff:
    '<path d="m3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.8 5.2A11 11 0 0 1 12 5c6.5 0 10 7 10 7a18 18 0 0 1-3.2 4M6.3 6.3A19 19 0 0 0 2 12s3.5 7 10 7a11 11 0 0 0 5.7-1.7"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>',
  moon: '<path d="M20 15.5A9 9 0 0 1 8.5 4 9 9 0 1 0 20 15.5Z"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3"/>',
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  question:
    '<circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 1 1 4 3c-1 .5-1 1-1 2m0 3v1"/>',
};
const svg = (name) =>
  `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[name]}</svg>`;

async function start() {
  const [fixtures, en, vi] = await Promise.all(
    ["scenarios", "en", "vi"].map(async (name) => {
      const response = await fetch(`fixtures/${name}.json`, {
        cache: "no-store",
      });
      if (!response.ok) throw new Error("Fixture unavailable");
      return response.json();
    }),
  );
  const languages = { en, vi };
  const fake = createFake(fixtures);
  let locale = "en";
  let theme = "light";
  let phase = "anonymous";
  let generation = 0;
  let message = "";
  let fields = {};
  let draft = {};
  let temporary = "";
  let temporaryEmail = "";
  let intent = "";
  let busy = false;
  let rateUntil = 0;
  let retryTimer;
  let activePath = "";
  let dialogReturn;
  let mobileExpanded = false;
  const t = (key) => languages[locale][key] ?? key;
  const route = () => {
    try {
      const value = location.hash.slice(1) || "/";
      if (!value.startsWith("/") || value.startsWith("//"))
        return { path: "/invalid", next: "" };
      const url = new URL(value, "http://prototype.invalid");
      if (url.origin !== "http://prototype.invalid")
        return { path: "/invalid", next: "" };
      return { path: url.pathname, next: url.searchParams.get("next") || "" };
    } catch {
      return { path: "/invalid", next: "" };
    }
  };
  const protectedRole = (path) =>
    path === "/organizer/workshops"
      ? "ORGANIZER"
      : path === "/staff/workshops"
        ? "STAFF"
        : null;
  const landing = (a) =>
    a?.role === "ORGANIZER"
      ? "/organizer/workshops"
      : a?.role === "STAFF"
        ? "/staff/workshops"
        : "/workshops";
  const safeIntent = (value, identity) => {
    try {
      if (
        !value.startsWith("/") ||
        value.startsWith("//") ||
        /[\\\u0000-\u0020]/.test(value)
      )
        return "";
      const url = new URL(value, "http://prototype.invalid");
      if (
        url.origin !== "http://prototype.invalid" ||
        url.pathname.includes("%") ||
        !["/workshops", "/organizer/workshops", "/staff/workshops"].includes(
          url.pathname,
        )
      )
        return "";
      const role = protectedRole(url.pathname);
      if (
        identity &&
        role &&
        (role !== identity.role || !identity.organization)
      )
        return "";
      // Only q is registered as inert, preserved review context; never executes an action.
      if ([...url.searchParams.keys()].some((key) => key !== "q") || url.hash)
        return "";
      return url.pathname + url.search;
    } catch {
      return "";
    }
  };
  const href = (path) => `#${path}`;
  const authHref = (path) =>
    href(path + (intent ? `?next=${encodeURIComponent(intent)}` : ""));
  const isPrivate = (path) => !!protectedRole(path);
  const currentIdentity = () =>
    phase === "authenticated" ? fake.identity() : null;
  const clearSecrets = () => {
    draft = {
      ...draft,
      password: "",
      currentPassword: "",
      newPassword: "",
      confirmation: "",
    };
    temporary = "";
  };
  const announce = (value) => {
    $("#announcer").textContent = value;
  };
  const go = (path, replace = false) => {
    if (replace) {
      history.replaceState(null, "", href(path));
      handleRoute();
    } else if (location.hash === href(path)) handleRoute();
    else location.hash = path;
  };
  const link = (path, key, className = "") =>
    `<a href="${escape(path === "/login" ? authHref(path) : href(path))}" class="${className}">${escape(t(key))}</a>`;
  const button = (id, key, secondary = false) =>
    `<button type="button" id="${id}" class="btn${secondary ? " secondary" : ""}">${escape(t(key))}</button>`;
  const notice = () => {
    const storage = fake.storageFailed
      ? `<div class="notice" role="status">${escape(t("storage"))}</div>`
      : "";
    if (!message) return storage;
    const uncertain = message === "unknownPassword";
    return `${storage}<div class="notice ${["signedOut", "localRemoved", "passwordUpdated"].includes(message) ? "success" : "error"}" role="${message === "passwordUpdated" ? "status" : "alert"}">${uncertain ? `<strong>${escape(t(message))}</strong><p>${escape(t("unknownPasswordText"))}</p><a href="${escape(authHref("/login"))}">${escape(t("signInNew"))}</a>` : escape(t(message))}${message === "rate" ? `<p id="rate-countdown"></p>` : ""}</div>`;
  };
  function field(name, key, type, autocomplete, hint = "") {
    const secret = type === "password";
    return `<div class="field"><label for="${name}">${escape(t(key))}</label><div class="${secret ? "password-wrap" : ""}"><input id="${name}" name="${name}" type="${type}" autocomplete="${autocomplete}" ${type === "email" ? 'inputmode="email" maxlength="254"' : 'maxlength="255"'} value="${escape(draft[name] || "")}" aria-invalid="${!!fields[name]}" aria-describedby="${name}-error${hint ? ` ${name}-help` : ""}" ${busy ? "readonly" : ""}>${secret ? `<button type="button" class="password-toggle" data-password="${name}" aria-label="${escape(t("showPassword"))}" aria-pressed="false">${svg("eye")}</button>` : ""}</div>${hint ? `<p class="requirements" id="${name}-help">${escape(t(hint))}</p>` : ""}<span id="${name}-error" class="field-error">${fields[name] ? escape(t(fields[name])) : ""}</span></div>`;
  }
  function welcome() {
    return `<aside class="welcome"><div class="art"><img src="assets/poster-design.svg" width="800" height="500" alt=""></div><h2>${escape(t("welcomeTitle"))}</h2><p>${escape(t("welcomeText"))}</p></aside>`;
  }
  function authForm(isTemporary) {
    return `<div class="auth-layout">${welcome()}<section class="auth-card" aria-labelledby="page-title"><h1 id="page-title" tabindex="-1">${escape(t(isTemporary ? "temporaryTitle" : "welcome"))}</h1><p class="subtitle">${escape(t(isTemporary ? "temporaryText" : "loginText"))}</p>${notice()}<form id="auth-form" novalidate aria-busy="${busy}">${field("email", "email", "email", "username")}${isTemporary && !temporary ? `<p class="requirements">${escape(t("missingCredentials"))}</p>${field("currentPassword", "currentPassword", "password", "current-password")}` : ""}${isTemporary ? `${field("newPassword", "newPassword", "password", "new-password", "passwordHelp")}${field("confirmation", "confirmPassword", "password", "new-password")}` : field("password", "password", "password", "current-password")}${!isTemporary ? `<div class="form-links"><a href="${escape(authHref("/forgot-password"))}">${escape(t("forgot"))}</a></div>` : '<div class="form-links"></div>'}<button class="btn wide" type="submit" ${busy || rateUntil > Date.now() ? "disabled" : ""}>${busy ? '<span class="spinner" aria-hidden="true"></span>' : ""}${escape(t(busy ? (isTemporary ? "saving" : "working") : isTemporary ? "updatePassword" : "signIn"))}</button></form>${isTemporary ? `<a class="back-link" href="${escape(authHref("/login"))}">${escape(t("backLogin"))}</a>` : `<p class="join">${escape(t("noAccount"))} <a href="${escape(authHref("/register"))}">${escape(t("createAccount"))}</a></p>`}</section></div>`;
  }
  function panel(title, text, actions, icon = "lock") {
    return `<section class="status-panel" aria-labelledby="page-title"><div class="status-symbol">${svg(icon)}</div><h1 id="page-title" tabindex="-1">${escape(t(title))}</h1><p>${escape(t(text))}</p>${notice()}<div class="actions">${actions}</div></section>`;
  }
  function shell() {
    const a = currentIdentity();
    $("#logo").src = `assets/unihub-logo${theme === "dark" ? "-dark" : ""}.svg`;
    $("#theme").innerHTML = svg(theme === "dark" ? "sun" : "moon");
    $("#theme").setAttribute("aria-label", t("theme"));
    $("#theme").setAttribute("aria-pressed", String(theme === "dark"));
    $("#locale").textContent = locale === "en" ? "EN" : "VN";
    $("#locale").setAttribute("aria-label", t("language"));
    document.querySelectorAll("[data-locale]").forEach((el) => {
      el.textContent = t(el.dataset.locale);
      el.setAttribute("aria-pressed", String(el.dataset.locale === locale));
    });
    $("#skip").textContent = t("skip");
    $(".brand").setAttribute(
      "aria-label",
      locale === "en" ? "UniHub home" : "UniHub trang chủ",
    );
    $("#navigation").setAttribute(
      "aria-label",
      locale === "en" ? "Main navigation" : "Điều hướng chính",
    );
    $("#navigation").innerHTML =
      a?.role === "ORGANIZER"
        ? ""
        : a?.role === "STAFF"
          ? link("/staff/workshops", "staffWorkshops")
          : link("/workshops", "discover");
    $("#navigation a")?.classList.toggle(
      "active",
      route().path ===
        (a?.role === "STAFF" ? "/staff/workshops" : "/workshops"),
    );
    const locked = [
      "logging-out",
      "logout-unconfirmed",
      "restoring",
      "renewing",
      "restore-unavailable",
    ].includes(phase);
    $("#account").innerHTML = a
      ? `<div class="account-control"><button type="button" class="avatar" id="account-toggle" aria-label="${escape(t("account"))}" aria-expanded="false" aria-controls="account-options">${escape(
          a.name
            .split(" ")
            .map((part) => part[0])
            .slice(0, 2)
            .join(""),
        )}</button><div class="popup" id="account-options" hidden><div class="identity">${escape(a.name)}<span>${escape(a.email)}</span></div><button type="button" id="logout">${escape(t("signOut"))}</button></div></div>`
      : locked
        ? ""
        : link("/login", "signIn", "btn");
    $("#footer-copy").textContent = t("footer");
    $("#footer-links").innerHTML = ["about", "gettingStarted", "accessibility"]
      .map(
        (key) =>
          `<button type="button" data-help="${key}">${escape(t(key))}</button>`,
      )
      .join("");
    $("#dialog-title").textContent = t("removeTitle");
    $("#dialog-description").textContent = t("removeText");
    $("#dialog-cancel").textContent = t("cancel");
    $("#dialog-confirm").textContent = t("removeConfirm");
    $("#dialog-close").setAttribute("aria-label", t("close"));
    $("#review-state").textContent =
      `State: ${phase} · route: ${route().path} · clock: +${fake.data.now - fixtures.clockOrigin}s${isBoundary(route().path) ? " · adjacent capability boundary" : ""}`;
    $("#fixture-note").textContent =
      `${fixtures.accounts.find((a) => a.id === fake.data.sample).email} / ${fixtures.demoPassword} · New password example: ${fixtures.suggestedNewPassword}. These are public synthetic examples.`;
  }
  function isBoundary(path) {
    return [
      "/register",
      "/forgot-password",
      "/workshops",
      "/organizer/workshops",
      "/staff/workshops",
    ].includes(path);
  }
  function render(focus = false) {
    if (!busy) announce("");
    shell();
    const { path } = route();
    const a = currentIdentity();
    const missingOrganization = a && a.role !== "STUDENT" && !a.organization;
    let html;
    if (["restoring", "renewing", "logging-out"].includes(phase))
      html = panel(
        phase === "logging-out"
          ? "signingOut"
          : phase === "renewing"
            ? "renewing"
            : "restoring",
        phase === "logging-out" ? "signingOutText" : "restoringText",
        '<span class="spinner" role="status" aria-label="Loading"></span>',
      );
    else if (phase === "restore-unavailable")
      html = panel(
        "restoreError",
        "restoreErrorText",
        button("retry-restore", "retry") +
          button("remove-local", "removeLocal", true),
      );
    else if (phase === "logout-unconfirmed")
      html = panel(
        "logoutError",
        "logoutErrorText",
        button("retry-logout", "retry") +
          button("remove-local", "removeLocal", true),
      );
    else if (path === "/login" || path === "/change-temporary-password")
      html = authForm(path !== "/login");
    else if (
      isPrivate(path) &&
      a &&
      (a.role !== protectedRole(path) || missingOrganization)
    )
      html = panel(
        "denied",
        missingOrganization ? "missingOrg" : "deniedText",
        missingOrganization
          ? link("/", "backHome", "btn secondary")
          : link(landing(a), "goToWorkshops", "btn"),
      );
    else if (path === "/")
      html = `<section class="home"><div><h1 id="page-title" tabindex="-1">${escape(t("homeTitle"))}</h1><p>${escape(t("homeText"))}</p>${notice()}<div class="actions">${link("/workshops", "explore", "btn")}${!a ? link("/login", "signIn", "btn secondary") : ""}</div></div><img src="assets/poster-design.svg" width="800" height="500" alt=""></section>`;
    else if (isBoundary(path)) {
      const key =
        path === "/register"
          ? "registerTitle"
          : path === "/forgot-password"
            ? "recoveryTitle"
            : path === "/staff/workshops"
              ? "staffWorkshops"
              : "workshops";
      const content = `<section class="boundary">${a ? `<p class="eyebrow">${escape(t("signedIn"))} · ${escape(a.name)} · ${escape(t(`role${a.role === "STUDENT" ? "Student" : a.role === "STAFF" ? "Staff" : "Organizer"}`))}</p>` : ""}<h1 id="page-title" tabindex="-1">${escape(t(key))}</h1>${notice()}${link(["/register", "/forgot-password"].includes(path) ? "/login" : "/", ["/register", "/forgot-password"].includes(path) ? "backLogin" : "backHome", "back-link")}</section>`;
      html =
        a?.role === "ORGANIZER"
          ? `<div class="organizer-layout"><aside class="sidebar"><p class="organization">${escape(a.organization || "")}</p><button class="btn secondary mobile-menu" id="mobile-menu" aria-expanded="${mobileExpanded}" aria-controls="organizer-nav">${escape(t("menu"))}</button><nav id="organizer-nav" class="${mobileExpanded ? "expanded" : ""}" aria-label="Organizer">${link("/organizer/workshops", "workshops")}</nav></aside>${content}</div>`
          : content;
    } else
      html = panel(
        "notFound",
        "notFoundText",
        link("/", "backHome", "btn"),
        "question",
      );
    $("#main").innerHTML = html;
    document.title = `${$("#page-title")?.textContent || t("signIn")} · UniHub`;
    if (focus) $("#page-title")?.focus();
    updateCountdown();
  }
  function updateCountdown() {
    const remaining = Math.ceil((rateUntil - Date.now()) / 1000);
    if ($("#rate-countdown"))
      $("#rate-countdown").textContent =
        remaining > 0 ? t("wait").replace("{seconds}", remaining) : t("retry");
    const submit = $('#auth-form [type="submit"]');
    if (submit) submit.disabled = busy || remaining > 0;
  }
  function handleRoute() {
    const { path, next } = route();
    if (path !== activePath) {
      if (!["restoring", "renewing", "logging-out"].includes(phase))
        generation++;
      busy = false;
      fields = {};
      if (!(activePath === "/login" && path === "/change-temporary-password"))
        clearSecrets();
      activePath = path;
    }
    if (next) intent = safeIntent(next);
    if (fake.data.logoutLock) {
      phase = "logout-unconfirmed";
      render(true);
      return;
    }
    if (
      phase === "authenticated" &&
      ["/login", "/change-temporary-password"].includes(path)
    ) {
      go(safeIntent(intent, fake.identity()) || landing(fake.identity()), true);
      return;
    }
    if (isPrivate(path) && phase === "anonymous") {
      intent = safeIntent(path);
      go(`/login?next=${encodeURIComponent(intent)}`, true);
      return;
    }
    render(true);
  }
  async function restore() {
    if (fake.data.logoutLock) {
      phase = "logout-unconfirmed";
      render(true);
      return;
    }
    const token = ++generation;
    phase =
      fake.data.session?.accessUntil <= fake.data.now
        ? "renewing"
        : "restoring";
    message = "";
    clearSecrets();
    render();
    try {
      const result = await fake.restore(() => token === generation);
      if (!result || token !== generation) return;
      phase = result.kind === "authenticated" ? "authenticated" : "anonymous";
      if (result.kind === "expired") {
        message = "expired";
        intent = safeIntent(location.hash.slice(1));
        go(authHref("/login").slice(1), true);
        return;
      }
      handleRoute();
    } catch {
      if (token === generation) {
        phase = "restore-unavailable";
        render(true);
      }
    }
  }
  async function submit(form) {
    if (busy || rateUntil > Date.now()) return;
    const data = Object.fromEntries(new FormData(form));
    draft = data;
    const isTemporary = route().path === "/change-temporary-password";
    const current = temporary || data.currentPassword || "";
    fields = {};
    message = "";
    if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email?.trim() || "") ||
      data.email.trim().length > 254
    )
      fields.email = "emailInvalid";
    if (!isTemporary && (!data.password?.trim() || data.password.length > 255))
      fields.password = "passwordInvalid";
    if (isTemporary) {
      if (!current.trim()) fields.currentPassword = "required";
      const next = data.newPassword || "";
      if (
        next.length < 8 ||
        next.length > 255 ||
        !/[A-Z]/.test(next) ||
        !/[a-z]/.test(next) ||
        !/\d/.test(next) ||
        !/[!@#$%^&*(),.?":{}|<>]/.test(next)
      )
        fields.newPassword = "newInvalid";
      else if (next === current) fields.newPassword = "samePassword";
      if (!data.confirmation || next !== data.confirmation)
        fields.confirmation = "mismatch";
    }
    if (Object.keys(fields).length) {
      render();
      $(`#${Object.keys(fields)[0]}`)?.focus();
      return;
    }
    const token = ++generation;
    busy = true;
    render();
    announce(t(isTemporary ? "saving" : "working"));
    try {
      const result = isTemporary
        ? await fake.replace(
            data.email,
            current,
            data.newPassword,
            () => token === generation,
          )
        : await fake.login(
            data.email,
            data.password,
            () => token === generation,
          );
      if (!result || token !== generation) return;
      busy = false;
      if (result.kind === "temporary") {
        temporary = data.password;
        temporaryEmail = data.email.trim();
        draft = { email: temporaryEmail };
        phase = "anonymous";
        go(authHref("/change-temporary-password").slice(1));
      } else {
        clearSecrets();
        phase = "authenticated";
        message = isTemporary ? "passwordUpdated" : "";
        go(safeIntent(intent, result.account) || landing(result.account), true);
        intent = "";
      }
    } catch (error) {
      if (token !== generation) return;
      busy = false;
      message = error.kind || "unavailable";
      draft = { email: data.email };
      temporary = "";
      if (message === "rate") {
        rateUntil = Date.now() + 3000;
        clearInterval(retryTimer);
        retryTimer = setInterval(() => {
          updateCountdown();
          if (Date.now() >= rateUntil) clearInterval(retryTimer);
        }, 250);
      }
      render();
    }
  }
  async function logout() {
    const token = ++generation;
    fake.lockLogout();
    clearSecrets();
    busy = false;
    message = "";
    phase = "logging-out";
    render(true);
    try {
      const result = await fake.logout(() => token === generation);
      if (!result || token !== generation) return;
      phase = "anonymous";
      intent = "";
      message = "signedOut";
      go("/", true);
    } catch {
      if (token === generation) {
        phase = "logout-unconfirmed";
        render(true);
      }
    }
  }
  function reset() {
    generation++;
    clearInterval(retryTimer);
    rateUntil = 0;
    const sample = $("#sample").value;
    fake.reset(sample);
    $("#scenario").value = "normal";
    phase = "anonymous";
    busy = false;
    message = "";
    intent = "";
    temporaryEmail = "";
    clearSecrets();
    draft = {};
    $("#confirm-local").close();
    go("/login", true);
  }
  $("#sample").innerHTML = fixtures.accounts
    .map((a) => `<option value="${a.id}">${escape(a.label)}</option>`)
    .join("");
  $("#sample").value = fake.data.sample;
  $("#scenario").innerHTML = fixtures.scenarios
    .map((s) => `<option value="${s.id}">${escape(s.label)}</option>`)
    .join("");
  $("#scenario").value = fake.data.scenario;
  $("#sample").addEventListener("change", reset);
  $("#scenario").addEventListener("change", () => {
    fake.setScenario($("#scenario").value);
    shell();
  });
  $("#reset").addEventListener("click", reset);
  $("#expire").addEventListener("click", () => {
    fake.advance();
    restore();
  });
  $("#revoke").addEventListener("click", () => {
    fake.revoke();
    restore();
  });
  $("#theme").addEventListener("click", () => {
    theme = theme === "light" ? "dark" : "light";
    document.documentElement.dataset.theme = theme;
    shell();
  });
  $("#locale").addEventListener("click", () => {
    const open = $("#language-options").hidden;
    $("#language-options").hidden = !open;
    $("#locale").setAttribute("aria-expanded", String(open));
    if (open) $("[data-locale]")?.focus();
  });
  document.addEventListener("click", (event) => {
    if (event.target.closest("#skip")) {
      event.preventDefault();
      $("#main").focus();
      return;
    }
    const localeButton = event.target.closest("[data-locale]");
    if (localeButton) {
      locale = localeButton.dataset.locale;
      document.documentElement.lang = locale;
      render();
      $("#language-options").hidden = true;
      $("#locale").setAttribute("aria-expanded", "false");
      $("#locale").focus();
    }
    const show = event.target.closest("[data-password]");
    if (show) {
      const input = $(`#${show.dataset.password}`);
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      show.innerHTML = svg(visible ? "eyeOff" : "eye");
      show.setAttribute("aria-pressed", String(visible));
      show.setAttribute(
        "aria-label",
        t(visible ? "hidePassword" : "showPassword"),
      );
    }
    if (event.target.closest("#account-toggle")) {
      const open = $("#account-options").hidden;
      $("#account-options").hidden = !open;
      $("#account-toggle").setAttribute("aria-expanded", String(open));
    }
    if (!event.target.closest(".account-control") && $("#account-options")) {
      $("#account-options").hidden = true;
      $("#account-toggle").setAttribute("aria-expanded", "false");
    }
    if (!event.target.closest(".locale-control")) {
      $("#language-options").hidden = true;
      $("#locale").setAttribute("aria-expanded", "false");
    }
    if (event.target.closest("#logout,#retry-logout")) logout();
    if (event.target.closest("#retry-restore")) restore();
    if (event.target.closest("#remove-local")) {
      dialogReturn = event.target.closest("#remove-local");
      $("#dialog-title").textContent = t("removeTitle");
      $("#dialog-description").textContent = t("removeText");
      $("#dialog-confirm").hidden = false;
      $("#dialog-cancel").hidden = false;
      $("#confirm-local").showModal();
      $("#dialog-cancel").focus();
    }
    if (event.target.closest("#dialog-close,#dialog-cancel"))
      $("#confirm-local").close();
    if (event.target.closest("#dialog-confirm")) {
      generation++;
      fake.clear();
      clearSecrets();
      intent = "";
      phase = "anonymous";
      $("#confirm-local").close();
      message = "localRemoved";
      go("/", true);
    }
    const help = event.target.closest("[data-help]");
    if (help) {
      dialogReturn = help;
      $("#dialog-title").textContent = t(help.dataset.help);
      $("#dialog-description").textContent = t(`${help.dataset.help}Text`);
      $("#dialog-confirm").hidden = true;
      $("#dialog-cancel").hidden = true;
      $("#confirm-local").showModal();
      $("#dialog-close").focus();
    }
    if (event.target.closest("#mobile-menu")) {
      mobileExpanded = !mobileExpanded;
      $("#mobile-menu").setAttribute("aria-expanded", String(mobileExpanded));
      $("#organizer-nav").classList.toggle("expanded", mobileExpanded);
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    if (!$("#language-options").hidden) {
      $("#language-options").hidden = true;
      $("#locale").setAttribute("aria-expanded", "false");
      $("#locale").focus();
    }
    if ($("#account-options") && !$("#account-options").hidden) {
      $("#account-options").hidden = true;
      $("#account-toggle").setAttribute("aria-expanded", "false");
      $("#account-toggle").focus();
    }
  });
  $("#confirm-local").addEventListener(
    "close",
    () => dialogReturn?.isConnected && dialogReturn.focus(),
  );
  document.addEventListener("input", (event) => {
    if (event.target.closest("#auth-form"))
      draft[event.target.name] = event.target.value;
  });
  document.addEventListener("submit", (event) => {
    if (event.target.id === "auth-form") {
      event.preventDefault();
      submit(event.target);
    }
  });
  window.addEventListener("hashchange", handleRoute);
  window.addEventListener("pagehide", () => {
    generation++;
    clearSecrets();
  });
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) restore();
  });
  const initial = route();
  intent = safeIntent(initial.next);
  activePath = initial.path;
  await restore();
}

start().catch(() => {
  $("#main").textContent =
    "The preview could not load. Reload after checking the local HTTP server.";
});
