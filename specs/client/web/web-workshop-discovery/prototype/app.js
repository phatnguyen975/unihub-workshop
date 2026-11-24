const [data, en, vi] = await Promise.all(
  ["scenarios", "en", "vi"].map(async (name) => {
    const response = await fetch("fixtures/" + name + ".json");
    if (!response.ok) throw new Error("Could not load fixture " + name);
    return response.json();
  }),
);
const art = "../../../../../docs/client/web/foundation-prototype/assets/";
const main = document.querySelector("main");
const scenarioSelect = document.querySelector("#scenario");
const copy = { en, vi };
let locale = sessionStorage.getItem("discovery-locale") === "vi" ? "vi" : "en";
let dark = sessionStorage.getItem("discovery-theme") === "dark";
let role = sessionStorage.getItem("discovery-role") || "";
let pendingIntent = "";
let scenario = "normal";
let faultConsumed = false;
let featuredIndex = 0;
let generation = 0;
let focusRoute = false;
const t = (key) => copy[locale][key];
const escape = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (char) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      })[char],
  );
const link = (route, text, classes = "") =>
  `<a class="${classes}" href="#${escape(route)}">${escape(text)}</a>`;
const button = (label, action, extra = "") =>
  `<button type="button" data-action="${action}" ${extra}>${escape(label)}</button>`;
const glyphs = {
  moon: "M20.8 14.1A9 9 0 0 1 9.9 3.2 9 9 0 1 0 20.8 14.1Z",
  sun: "M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M19 5l-1.5 1.5m-11 11L5 19M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z",
  clock: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2",
  pin: "M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Zm-5 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z",
  calendar: "M4 5h16v15H4zM4 10h16M8 3v4m8-4v4",
  price: "M3 4h9l9 9-8 8-10-9zm5 4h.01",
  eye: "M2 12s3-6 10-6 10 6 10 6-3 6-10 6S2 12 2 12Zm13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z",
};
const icon = (name) =>
  `<svg class="icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="${glyphs[name]}"/></svg>`;
const chevron = (direction) =>
  `<svg class="chevron" aria-hidden="true" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="${direction === "left" ? "M10 3 5 8l5 5" : "m6 3 5 5-5 5"}"/></svg>`;
const arrow = (direction, action, label, disabled = false) =>
  `<button type="button" data-action="${action}" aria-label="${escape(label)}" ${disabled ? "disabled" : ""}>${chevron(direction)}</button>`;
const pageControl = (direction, label, route) =>
  route
    ? `<a class="button page-control" href="#${escape(route)}" aria-label="${escape(label)}">${chevron(direction)}</a>`
    : `<button class="page-control" type="button" aria-label="${escape(label)}" disabled>${chevron(direction)}</button>`;
const detailRoute = (item) => "/workshops/" + item.slug;
const safeIntent = (value) =>
  /^\/workshops\/[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value || "")
    ? value
    : "/workshops";
const options = () => data.scenarios[scenario];
const published = () =>
  options().empty ? [] : data.workshops.filter((item) => item.published);
const resultOrder = (a, b) =>
  a.startsAt.localeCompare(b.startsAt) || a.slug.localeCompare(b.slug);
const localDay = (item) =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: item.timezone }).format(
    new Date(item.startsAt),
  );
const formatDate = (value, timeZone, format) =>
  new Intl.DateTimeFormat(locale, { timeZone, ...format }).format(
    new Date(value),
  );
const title = (item) => escape(item.title[locale]);
const money = (item) =>
  item.price === 0
    ? t("free")
    : new Intl.NumberFormat(locale, {
        style: "currency",
        currency: item.currency,
      }).format(item.price);
const time = (item, value) =>
  formatDate(value, item.timezone, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });
const date = (item) =>
  formatDate(item.startsAt, item.timezone, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
const priceText = (price) => t(price || "any");
const room = (item) => t("room") + " " + item.location;
const availability = (item) =>
  item.seats ? item.seats + " " + t("seats") : t("full");
const poster = (item) =>
  item.poster
    ? `<img class="poster" alt="" src="${art}poster-${item.poster}.svg" width="640" height="320">`
    : "";
const current = () => {
  const [path, query = ""] = (location.hash.slice(1) || "/").split("?", 2);
  return { path, params: new URLSearchParams(query) };
};
function navigate(route, replace = false) {
  if (location.hash === "#" + route) return render(false);
  if (replace) {
    history.replaceState(history.state, "", "#" + route);
    return render(false);
  }
  location.hash = route;
}
function syncShell(path) {
  document.documentElement.lang = locale;
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  document.querySelector("#brand img").src =
    art + (dark ? "unihub-logo-dark.svg" : "unihub-logo.svg");
  document.querySelector(".skip").textContent = t("skip");
  const discover = document.querySelector("#discover");
  discover.textContent = t("discover");
  if (path.startsWith("/workshops"))
    discover.setAttribute(
      "aria-current",
      path === "/workshops" ? "page" : "location",
    );
  else discover.removeAttribute("aria-current");
  const theme = document.querySelector("#theme");
  theme.setAttribute("aria-label", t("theme"));
  theme.setAttribute("aria-pressed", dark);
  theme.innerHTML = icon(dark ? "sun" : "moon");
  const language = document.querySelector("#language");
  language.textContent = locale.toUpperCase();
  language.setAttribute(
    "aria-label",
    t("language") + " · " + locale.toUpperCase(),
  );
  for (const choice of document.querySelectorAll("[data-lang]")) {
    choice.textContent =
      locale === "vi"
        ? choice.dataset.lang === "vi"
          ? "Tiếng Việt"
          : "Tiếng Anh"
        : choice.dataset.lang === "vi"
          ? "Vietnamese"
          : "English";
    choice.setAttribute("aria-pressed", locale === choice.dataset.lang);
  }
  const account = document.querySelector("#account");
  const identity = data.accounts[role];
  const avatar = document.querySelector("#account-trigger");
  account.textContent = t("signin");
  account.hidden = Boolean(identity);
  avatar.hidden = !identity;
  if (identity) {
    avatar.textContent = identity.name
      .split(" ")
      .slice(0, 2)
      .map((word) => word[0])
      .join("");
    avatar.setAttribute(
      "aria-label",
      avatar.textContent + " — " + t("account") + ": " + identity.name,
    );
    document.querySelector("#account-name").textContent = identity.name;
    document.querySelector("#account-email").textContent = identity.email;
    document.querySelector("#signout").textContent = t("signout");
  }
  document.title =
    t(
      path.startsWith("/workshops")
        ? "discover"
        : path === "/"
          ? "home"
          : "signin",
    ) + " · UniHub";
}
function applyFocus() {
  if (focusRoute) {
    focusRoute = false;
    main.focus();
  }
}
function reset() {
  faultConsumed = false;
  featuredIndex = 0;
  pendingIntent = "";
  role = options().role || "";
  sessionStorage.removeItem("discovery-role");
  navigate("/workshops");
}
function parseCriteria(params) {
  const query = (params.get("q") || "").trim();
  const price = params.get("price") || "";
  const from = params.get("from") || "";
  const to = params.get("to") || "";
  const validDay = (value) =>
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(value)) &&
    new Date(value).toISOString().slice(0, 10) === value;
  const validDates =
    (!from && !to) || (validDay(from) && validDay(to) && from <= to);
  const validPrice = ["", "free", "paid"].includes(price);
  return {
    q: query.length <= 200 ? query : "",
    price: validPrice ? price : "",
    from: validDates ? from : "",
    to: validDates ? to : "",
    token: params.get("cursor") || params.get("page") || "",
    invalid: query.length > 200 || !validPrice || !validDates,
  };
}
function catalogRoute(criteria, cursor = "") {
  const params = new URLSearchParams();
  for (const key of ["q", "price", "from", "to"]) {
    if (criteria[key]) params.set(key, criteria[key]);
  }
  if (cursor) params.set("cursor", cursor);
  return "/workshops" + (params.size ? "?" + params : "");
}
function cursorFor(criteria, item) {
  return btoa(
    new TextEncoder()
      .encode(
        JSON.stringify([catalogRoute(criteria), item.startsAt, item.slug]),
      )
      .reduce((bytes, byte) => bytes + String.fromCharCode(byte), ""),
  );
}
async function read(kind, getValue, allowDelay = true, query = "") {
  const configuration = options();
  const failing =
    !faultConsumed && [kind, "offline"].includes(configuration.fault);
  if (failing) faultConsumed = true;
  const delay =
    scenario === "race" && query !== "ai"
      ? configuration.delay
      : scenario === "slow"
        ? configuration.delay
        : 160;
  const value = getValue();
  if (allowDelay) await new Promise((resolve) => setTimeout(resolve, delay));
  return failing ? { fault: configuration.fault } : { value };
}
function panel(message, action = "", roleName = "status") {
  return `<div class="panel" role="${roleName}"><p>${escape(message)}</p>${action}</div>`;
}
function cover(item) {
  return `<article class="card"><div class="card-poster">${poster(item)}<span class="date-chip">
    <strong>${formatDate(item.startsAt, item.timezone, { day: "2-digit" })}</strong>
    ${formatDate(item.startsAt, item.timezone, { month: "short" })}</span></div><div class="card-body">
    <h3>${link(detailRoute(item), item.title[locale])}</h3>
    <p class="facts icon-label">${icon("clock")}${time(item, item.startsAt)}–${time(item, item.endsAt)}</p>
    <p class="facts icon-label">${icon("pin")}${escape(room(item))}</p>
    <div class="card-bottom"><strong>${money(item)}</strong><span class="seats ${item.seats ? "" : "full"}">${availability(item)}</span></div>
  </div></article>`;
}
function showFeatured() {
  const target = document.querySelector("#featured");
  if (!target) return;
  const items = options().noFeatured
    ? []
    : data.featured
        .map((slug) => published().find((item) => item.slug === slug))
        .filter(Boolean);
  target.hidden = !items.length;
  if (!items.length) return;
  featuredIndex = Math.min(featuredIndex, items.length - 1);
  const item = items[featuredIndex];
  target.innerHTML = `<a class="hero-poster" href="#${detailRoute(item)}">${poster(item)}<div class="featured-body">
    <p class="featured-label">${t("featured")}</p><h2>${title(item)}</h2>
    <p class="facts">${date(item)} · ${time(item, item.startsAt)}–${time(item, item.endsAt)}</p>
    </div></a>
    <div class="carousel-controls">
      ${arrow("left", "feature-prev", t("prev"), featuredIndex === 0)}
      <span role="status">${featuredIndex + 1} / ${items.length}</span>
      ${arrow("right", "feature-next", t("next"), featuredIndex === items.length - 1)}
    </div>
  `;
}
async function catalog(params, requestId) {
  const criteria = parseCriteria(params);
  const chips = [
    ["q", criteria.q],
    ["price", criteria.price && priceText(criteria.price)],
    ["dates", criteria.from && criteria.from + " – " + criteria.to],
  ];
  main.innerHTML = `<section class="hero"><div class="hero-copy"><h1>${t("home")}</h1><p>${t("intro")}</p>${button(t("explore") + " ↗", "explore", 'class="lime"')}</div><section class="featured" id="featured" aria-label="${t("featured")}"></section></section>
    <section class="results"><h2>${t("catalog")}</h2>
    <div class="filters">
      <form class="search" id="search"><input type="search" name="q" aria-label="${t("search")}" aria-describedby="search-error" placeholder="${t("search")}" value="${escape(criteria.q)}"><button class="primary">${t("searchButton")}</button></form>
      <div class="filter"><button id="date-trigger" class="icon-label" type="button" aria-expanded="false" aria-controls="date-popover">${icon("calendar")}${t("dates")}</button><div id="date-popover" class="popover calendar" hidden></div></div>
      <div class="filter"><button id="price-trigger" class="icon-label" type="button" aria-expanded="false" aria-controls="price-popover">${icon("price")}${t("price")}</button>
      <div id="price-popover" class="popover" hidden>${["", "free", "paid"].map((price) => button(priceText(price), "price", `data-value="${price}" aria-pressed="${criteria.price === price}"`)).join(" ")}</div></div>
    </div>
    <p id="search-error" class="error" role="alert">${criteria.invalid ? t("invalidRoute") : ""}</p>
    <div class="chips">${chips
      .filter(([, label]) => label)
      .map(([key, label]) =>
        button(
          "× " + label,
          "clear-" + key,
          `aria-label="${t("remove")} ${escape(label)}"`,
        ),
      )
      .join("")}</div>
    <div id="results" aria-busy="true">${panel(t("loading"))}</div></section>`;
  showFeatured();
  applyFocus();
  const result = await read(
    "catalog",
    () => {
      const source = published();
      const query = criteria.q.toLocaleLowerCase(locale);
      const items = source
        .filter((item) => {
          const text = [
            item.title[locale],
            item.description?.[locale],
            room(item),
            item.slug,
            ...item.speakers.map((speaker) => speaker.name),
          ]
            .join(" ")
            .toLocaleLowerCase(locale);
          const day = localDay(item);
          return (
            text.includes(query) &&
            (!criteria.price ||
              (item.price === 0) === (criteria.price === "free")) &&
            (!criteria.from || (day >= criteria.from && day <= criteria.to))
          );
        })
        .sort(resultOrder);
      return { source, items };
    },
    true,
    criteria.q,
  );
  if (requestId !== generation) return;
  const target = document.querySelector("#results");
  target.setAttribute("aria-busy", "false");
  if (result.fault) {
    target.innerHTML = panel(
      t(result.fault === "offline" ? "offline" : "listError"),
      button(t("retry"), "retry"),
      "alert",
    );
    return;
  }
  const { source, items } = result.value;
  const boundary = criteria.token
    ? items.findIndex(
        (item, index) =>
          (index + 1) % data.pageSize === 0 &&
          index + 1 < items.length &&
          cursorFor(criteria, item) === criteria.token,
      )
    : -1;
  const offset = boundary + 1;
  if (criteria.token && offset === 0)
    history.replaceState(history.state, "", "#" + catalogRoute(criteria));
  if (!items.length) {
    target.innerHTML = panel(
      t(source.length ? "none" : "empty"),
      source.length ? button(t("clear"), "clear-all") : "",
    );
    return;
  }
  const visible = items.slice(offset, offset + data.pageSize);
  const previousBoundary = items[offset - data.pageSize - 1];
  const hasNext = offset + visible.length < items.length;
  target.innerHTML = `<p role="status" class="result-status muted">${items.length} ${t("results")}</p>
    <div class="grid">${visible.map(cover).join("")}</div>
    ${
      offset || hasNext
        ? `<nav class="pages" aria-label="${t("page")}">
      ${pageControl("left", t("prev"), offset ? catalogRoute(criteria, previousBoundary ? cursorFor(criteria, previousBoundary) : "") : "")}
      ${pageControl("right", t("next"), hasNext ? catalogRoute(criteria, cursorFor(criteria, visible.at(-1))) : "")}
    </nav>`
        : ""
    }`;
}
async function detail(path, requestId) {
  main.innerHTML = panel(t("loadingDetail"));
  applyFocus();
  const back = history.state?.catalog || "/workshops";
  const result = await read("detail", () =>
    published().find((item) => detailRoute(item) === path),
  );
  if (requestId !== generation) return;
  const backLink = link(back, "← " + t("back"));
  if (result.fault) {
    main.innerHTML =
      backLink +
      panel(
        t(result.fault === "offline" ? "offline" : "detailError"),
        button(t("retry"), "retry"),
        "alert",
      );
    return;
  }
  const item = result.value;
  if (!item) {
    main.innerHTML = `<div class="panel"><h1>${t("unavailable")}</h1><p>${t("unavailableText")}</p>${backLink}</div>`;
    return;
  }
  document.title = item.title[locale] + " · UniHub";
  const action =
    !role && item.seats
      ? link(
          "/login?next=" + encodeURIComponent(path),
          t("signinTo"),
          "button primary",
        )
      : role === "student"
        ? `<p>${t("registration")}</p>`
        : "";
  main.innerHTML = `${backLink}
    <div class="details">
      <article class="panel detail-content">${poster(item)}<h1 class="detail-title">${title(item)}</h1>
      ${item.description ? `<h2>${t("about")}</h2><p>${escape(item.description[locale])}</p>` : ""}
      ${
        item.speakers.length
          ? `<h2>${t("speakers")}</h2><ul class="speakers">${item.speakers
              .map(
                (s) =>
                  `<li><span class="avatar" aria-hidden="true">${escape(
                    s.name
                      .split(" ")
                      .slice(-2)
                      .map((word) => word[0])
                      .join(""),
                  )}</span><strong>${escape(s.name)}</strong></li>`,
              )
              .join("")}</ul>`
          : ""
      }
      ${item.brochure ? `<p>${linkAsset(item.brochure, t("brochure"))} · ${linkAsset(item.brochure, t("download"), true)}</p>` : ""}</article>
      <aside class="panel booking"><h2>${money(item)}</h2>
        <dl><dt>${t("when")}</dt><dd>${date(item)} · ${time(item, item.startsAt)}–${time(item, item.endsAt)}<br>${escape(item.timezone)}</dd>
        <dt>${t("where")}</dt><dd>${escape(room(item))}</dd><dt>${t("price")}</dt><dd>${money(item)}</dd></dl>
        <p class="seats ${item.seats ? "" : "full"}">${availability(item)}</p>${action}
      </aside>
    </div>`;
}
function linkAsset(path, label, download = false) {
  return `<a href="${escape(path)}" ${download ? "download" : ""}>${label}</a>`;
}
function authField(name, label, type, autocomplete) {
  const input = `<input id="${name}" name="${name}" type="${type}" autocomplete="${autocomplete}" required>`;
  const control =
    type === "password"
      ? `<div class="password-wrap">${input}<button type="button" class="password-toggle" data-action="password" aria-controls="${name}" aria-pressed="false" aria-label="${t("showPassword")}">${icon("eye")}</button></div>`
      : input;
  return `<div class="field"><label for="${name}">${t(label)}</label>${control}</div>`;
}
function login(temporary, params) {
  const destination = safeIntent(params.get("next"));
  if (role) return navigate(destination, true);
  if (temporary && !pendingIntent)
    return navigate("/login?next=" + encodeURIComponent(destination), true);
  main.innerHTML = `<div class="auth-layout"><aside class="auth-welcome"><h2>${t("home")}</h2><p>${t("intro")}</p>${poster({ poster: "design" })}</aside>
    <section class="panel auth-card"><h1>${t(temporary ? "temporary" : "signin")}</h1>
    <form id="login"><p id="auth-error" class="error" role="alert"></p>
    ${
      temporary
        ? authField("replacement", "newPassword", "password", "new-password")
        : `<p>${t("authHint")} <code>student@example.test</code> / <code>Workshop26!</code></p>
      ${authField("email", "email", "email", "username")}
      ${authField("password", "password", "password", "current-password")}`
    }
    <button type="submit" class="primary">${t(temporary ? "continue" : "signin")}</button></form></section></div>`;
  applyFocus();
}
function render(moveFocus = false) {
  const { path, params } = current();
  const requestId = ++generation;
  focusRoute = moveFocus;
  syncShell(path);
  if (path === "/workshops") return catalog(params, requestId);
  if (path.startsWith("/workshops/")) return detail(path, requestId);
  if (path === "/login" || path === "/change-temporary-password")
    return login(path !== "/login", params);
  main.innerHTML =
    path === "/"
      ? `<section class="hero"><div><h1>${t("home")}</h1><p>${t("intro")}</p>${link("/workshops", t("explore"), "button primary")}</div></section>`
      : `<h1>${t("unavailable")}</h1>${link("/workshops", t("back"))}`;
  applyFocus();
}

let calendarMonth = "2026-11";
let start = "";
function calendar(criteria) {
  const [year, month] = calendarMonth.split("-").map(Number);
  const offset = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
  const count = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const label = formatDate(calendarMonth + "-01T00:00:00Z", "UTC", {
    month: "long",
    year: "numeric",
  });
  const dayButtons = Array.from({ length: count }, (_, index) => {
    const day = calendarMonth + "-" + String(index + 1).padStart(2, "0");
    const selected = start
      ? day === start
      : day >= criteria.from && day <= criteria.to;
    const fullDate = formatDate(day + "T00:00:00Z", "UTC", {
      dateStyle: "long",
    });
    return button(
      String(index + 1),
      "day",
      `data-value="${day}" aria-label="${index + 1} · ${escape(fullDate)}" aria-pressed="${selected}"`,
    );
  }).join("");
  document.querySelector("#date-popover").innerHTML = `<div class="month">
    ${arrow("left", "month-prev", t("prevMonth"))}<strong aria-live="polite">${escape(label)}</strong>
    ${arrow("right", "month-next", t("nextMonth"))}
    </div><div class="days">${Array.from({ length: 7 }, (_, i) => `<span aria-hidden="true">${formatDate(new Date(Date.UTC(2026, 10, 1 + i)), "UTC", { weekday: "narrow" })}</span>`).join("")}${"<span></span>".repeat(offset)}${dayButtons}</div>
    <div class="date-footer"><span role="status">${t(start ? "selectEnd" : "selectStart")}</span>${button(t("clearDates"), "clear-dates")}</div>`;
}
function closePopovers(restoreFocus = false) {
  for (const id of [
    "language",
    "account-trigger",
    "date-trigger",
    "price-trigger",
  ]) {
    const trigger = document.getElementById(id);
    if (trigger?.getAttribute("aria-expanded") !== "true") continue;
    trigger.setAttribute("aria-expanded", "false");
    document.getElementById(trigger.getAttribute("aria-controls")).hidden =
      true;
    if (restoreFocus) trigger.focus();
  }
}
function togglePopover(trigger) {
  const opening = trigger.getAttribute("aria-expanded") !== "true";
  closePopovers();
  if (!opening) return;
  trigger.setAttribute("aria-expanded", "true");
  document.getElementById(trigger.getAttribute("aria-controls")).hidden = false;
}
main.addEventListener("submit", (event) => {
  event.preventDefault();
  const fields = new FormData(event.target);
  const { path, params } = current();
  if (event.target.id === "search") {
    const query = String(fields.get("q")).trim();
    const field = event.target.elements.q;
    if (query.length > 200) {
      field.setAttribute("aria-invalid", "true");
      document.querySelector("#search-error").textContent = t("invalid");
      field.focus();
      return;
    }
    navigate(catalogRoute({ ...parseCriteria(params), q: query }));
    return;
  }
  if (
    path === "/login" &&
    (fields.get("email") !== "student@example.test" ||
      fields.get("password") !== "Workshop26!")
  ) {
    document.querySelector("#auth-error").textContent = t("signinError");
    return;
  }
  const destination = safeIntent(params.get("next"));
  if (path === "/login" && options().temporary) {
    pendingIntent = destination;
    navigate(
      "/change-temporary-password?next=" + encodeURIComponent(destination),
    );
    return;
  }
  role = "student";
  sessionStorage.setItem("discovery-role", role);
  navigate(pendingIntent || destination, true);
  pendingIntent = "";
});
document.addEventListener("click", (event) => {
  const target = event.target.closest("a,button");
  const { path, params } = current();
  const criteria = parseCriteria(params);
  if (target?.matches(".skip")) {
    event.preventDefault();
    main.focus();
    return;
  }
  if (target?.id === "signout") {
    closePopovers();
    role = "";
    sessionStorage.removeItem("discovery-role");
    render(false);
    document.querySelector("#account").focus();
    return;
  }
  if (target?.id === "theme") {
    dark = !dark;
    sessionStorage.setItem("discovery-theme", dark ? "dark" : "light");
    syncShell(path);
    return;
  }
  if (target?.dataset.lang) {
    locale = target.dataset.lang;
    sessionStorage.setItem("discovery-locale", locale);
    closePopovers(true);
    render(false);
    return;
  }
  if (
    ["language", "account-trigger", "date-trigger", "price-trigger"].includes(
      target?.id,
    )
  ) {
    if (target.id === "date-trigger") {
      start = "";
      calendarMonth = criteria.from.slice(0, 7) || "2026-11";
      calendar(criteria);
    }
    togglePopover(target);
    return;
  }
  const detailLink = target?.getAttribute("href");
  if (
    path === "/workshops" &&
    main.contains(target) &&
    detailLink?.startsWith("#/workshops/") &&
    !event.ctrlKey &&
    !event.metaKey &&
    !event.shiftKey &&
    !event.altKey
  ) {
    event.preventDefault();
    history.pushState({ catalog: location.hash.slice(1) }, "", detailLink);
    render(true);
    return;
  }
  const action = target?.dataset.action;
  if (!event.target.closest(".popover")) closePopovers();
  if (!action) return;
  if (action === "retry") {
    render(false);
    main.focus();
  } else if (action === "password") {
    const field = document.getElementById(target.getAttribute("aria-controls"));
    const show = field.type === "password";
    field.type = show ? "text" : "password";
    target.setAttribute("aria-pressed", show);
    target.setAttribute(
      "aria-label",
      t(show ? "hidePassword" : "showPassword"),
    );
  } else if (action === "explore") {
    document.querySelector("#search").scrollIntoView({ block: "center" });
    document.querySelector("#search input").focus({ preventScroll: true });
  } else if (action.startsWith("feature-")) {
    featuredIndex += action.endsWith("next") ? 1 : -1;
    showFeatured();
    const control =
      document.querySelector(
        `#featured [data-action="${action}"]:not(:disabled)`,
      ) || document.querySelector("#featured button:not(:disabled)");
    control?.focus();
  } else if (action.startsWith("month-")) {
    const month = new Date(calendarMonth + "-01T00:00:00Z");
    month.setUTCMonth(month.getUTCMonth() + (action.endsWith("next") ? 1 : -1));
    calendarMonth = month.toISOString().slice(0, 7);
    calendar(criteria);
    document.querySelector(`[data-action="${action}"]`).focus();
  } else if (action === "day") {
    const day = target.dataset.value;
    if (!start) {
      start = day;
      calendar(criteria);
      document.querySelector(`[data-value="${day}"]`).focus();
    } else {
      const [from, to] = [start, day].sort();
      navigate(catalogRoute({ ...criteria, from, to }));
    }
  } else {
    if (action === "price") criteria.price = target.dataset.value;
    if (action === "clear-q") criteria.q = "";
    if (action === "clear-price") criteria.price = "";
    if (action === "clear-dates") criteria.from = criteria.to = "";
    navigate(action === "clear-all" ? "/workshops" : catalogRoute(criteria));
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closePopovers(true);
});
window.addEventListener("hashchange", () => {
  closePopovers();
  render(true);
});
for (const name of Object.keys(data.scenarios))
  scenarioSelect.add(new Option(name, name));
scenarioSelect.addEventListener("change", () => {
  scenario = scenarioSelect.value;
  reset();
});
document.querySelector("#reset").addEventListener("click", reset);
render();
