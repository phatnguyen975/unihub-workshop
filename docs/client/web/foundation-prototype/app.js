/* Design preview only: local fixtures, no authentication or provider calls. */
"use strict";
const main = document.querySelector("main");
const dialog = document.querySelector("#dialog");
const state = {
  locale: "en",
  category: "all",
  query: "",
  scenario: "normal",
  registered: false,
  ticketId: null,
  draft: null,
};
let data;
let loadTimer;
let toastTimer;
const icons = {
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  pin: '<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/>',
};
const icon = (name) =>
  `<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">${icons[name]}</svg>`;
const escape = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
const t = (key) => data.locales[state.locale][key];
const localized = (value) => value[state.locale];
const workshop = (id) =>
  data.workshops.find((item) => item.id === id) || data.workshops[0];
const route = () => location.hash.slice(1) || "discover";
const money = (price) =>
  price === 0
    ? t("free")
    : new Intl.NumberFormat(state.locale === "vi" ? "vi-VN" : "en-US", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0,
      }).format(price);
const date = (item, options = { day: "2-digit", month: "short" }) =>
  new Intl.DateTimeFormat(state.locale === "vi" ? "vi-VN" : "en-GB", {
    ...options,
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(item.start));
const announce = (message) => {
  document.querySelector("#announcement").textContent = message;
};

function updateChrome() {
  document.documentElement.lang = state.locale;
  document.querySelector(".skip").textContent = t("skip");
  document
    .querySelector(".product-nav")
    .setAttribute(
      "aria-label",
      state.locale === "vi" ? "Điều hướng chính" : "Main navigation",
    );
  document.querySelectorAll("[data-copy]").forEach((node) => {
    node.textContent = t(node.dataset.copy);
  });
  const scenarioNames =
    state.locale === "vi"
      ? ["Bình thường", "Rỗng", "Lỗi", "Đang tải", "Hết chỗ"]
      : ["Normal", "Empty", "Error", "Loading", "Full"];
  [...document.querySelector("#scenario").options].forEach((option, index) => {
    option.textContent = scenarioNames[index];
  });
  document
    .querySelectorAll("a[aria-current]")
    .forEach((node) => node.removeAttribute("aria-current"));
  const page = route().split("/")[0];
  document.querySelector(".product-nav").hidden = page === "organizer";
  const avatar = document.querySelector(".header-actions .avatar");
  avatar.textContent = page === "organizer" ? "LH" : "MA";
  avatar.setAttribute(
    "aria-label",
    page === "organizer" ? "Linh Hoàng" : "Minh Anh",
  );
  if (!document.querySelector("#toast").hidden)
    document.querySelector("#toast").textContent = t("saved");
  document
    .querySelectorAll(`a[href="#${page === "detail" ? "discover" : page}"]`)
    .forEach((node) => node.setAttribute("aria-current", "page"));
  document.title = `UniHub — ${t(page === "organizer" ? "manageTitle" : page === "brand" ? "identity" : page === "wallet" ? "myTickets" : "discover")}`;
}

function card(item) {
  return `<article class="card"><div class="card-poster"><img src="assets/${item.poster}" alt="" width="800" height="500"><span class="date-chip"><b>${date(item, { day: "2-digit" })}</b>${date(item, { month: "short" })}</span></div><div class="card-body"><h3><a href="#detail/${item.id}">${escape(localized(item.title))}</a></h3><p class="meta">${icon("clock")}${date(item, { hour: "2-digit", minute: "2-digit" })} – ${item.end}</p><p class="meta">${icon("pin")}${escape(item.room)}</p><div class="card-bottom"><strong>${money(item.price)}</strong><span>${state.scenario === "full" ? t("full") : `${item.seats} ${t("seats")}`}</span></div></div></article>`;
}

function emptyPanel(kind) {
  if (kind === "loading")
    return `<section class="empty" aria-busy="true"><span class="empty-icon" aria-hidden="true">◷</span><h2>${t("loading")}</h2></section>`;
  const error = kind === "error";
  return `<section class="empty"><span class="empty-icon" aria-hidden="true">${error ? "!" : icon("search")}</span><h2>${t(error ? "errorTitle" : "emptyTitle")}</h2><p>${t(error ? "errorText" : "emptyText")}</p><button class="btn secondary" data-action="recover">${t(error ? "retry" : "clear")}</button></section>`;
}

function filteredWorkshops() {
  const query = state.query
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase();
  return data.workshops.filter(
    (item) =>
      item.status === "published" &&
      (!state.fee ||
        (state.fee === "free" ? item.price === 0 : item.price > 0)) &&
      (!state.from || item.start.slice(0, 10) >= state.from) &&
      (!state.to || item.start.slice(0, 10) <= state.to) &&
      `${localized(item.title)} ${item.speaker}`
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLocaleLowerCase()
        .includes(query),
  );
}

function updateResults() {
  const items = filteredWorkshops();
  const kind = ["empty", "error", "loading"].includes(state.scenario)
    ? state.scenario
    : items.length
      ? null
      : "empty";
  document.querySelector("#results").innerHTML = kind
    ? emptyPanel(kind)
    : items.map(card).join("");
  document.querySelector("#result-count").textContent =
    `${kind ? 0 : items.length} ${t("results")}`;
  announce(
    kind === "loading"
      ? t("loading")
      : kind === "error"
        ? t("errorTitle")
        : `${kind ? 0 : items.length} ${t("results")}`,
  );
}

function discover() {
  const item = data.workshops[0];
  main.innerHTML = `<div class="wrap"><section class="hero" aria-labelledby="hero-title"><div class="hero-content"><p class="hero-note">${t("heroNote")}</p><h1 id="hero-title">${t("heroTitle")}</h1><p>${t("heroText")}</p><a class="btn lime" href="#workshops" id="explore">${t("heroCta")}<span class="arrow" aria-hidden="true">↗</span></a></div><div class="hero-visual"><a class="hero-poster" href="#detail/ai"><img src="assets/poster-ai.svg" alt="" width="800" height="500"><div><span class="category">${t("heroTag")}</span><h2>${t("heroWorkshop")}</h2><p>${date(item)} · 09:00 – 11:00</p></div></a></div></section><section id="workshops" aria-labelledby="browse-title"><div class="section-top"><div><h2 id="browse-title">${t("browseTitle")}</h2><p>${t("browseText")}</p></div><small id="result-count" aria-hidden="true"></small></div><div class="filters"><label class="search">${icon("search")}<span class="sr-only">${t("search")}</span><input id="search" type="search" placeholder="${t("search")}" value="${escape(state.query)}"></label><div id="filters" class="filter-actions"></div></div><div class="cards" id="results"></div></section></div>`;
  updateResults();
  document.querySelector("#search").addEventListener("input", (event) => {
    state.query = event.target.value;
    updateResults();
  });
  document.querySelector("#explore").addEventListener("click", (event) => {
    event.preventDefault();
    document.querySelector("#workshops").scrollIntoView();
    document.querySelector("#search").focus({ preventScroll: true });
  });
}

function detail(id) {
  const item = workshop(id);
  const full = state.scenario === "full";
  main.innerHTML = `<div class="wrap"><a href="#discover" class="back"><span aria-hidden="true">←</span>${t("back")}</a><div class="detail-grid"><article class="detail-content"><img class="detail-cover" src="assets/${item.poster}" alt="" width="800" height="500"><p style="margin-top:24px"></p><h1 class="detail-title">${escape(localized(item.title))}</h1><h2>${t("about")}</h2><p class="detail-copy">${escape(localized(item.description))}</p><h2>${t("speaker")}</h2><div class="speaker"><span class="avatar" aria-hidden="true">${item.speaker
    .split(" ")
    .slice(-2)
    .map((word) => word[0])
    .join(
      "",
    )}</span><div><strong>${item.speaker}</strong><br><small>${localized(item.role)}</small></div></div></article><aside class="booking" aria-label="${t("ticketNote")}"><h2>${money(item.price)}</h2><dl class="facts"><dt>${t("when")}</dt><dd>${date(item, { day: "numeric", month: "long", year: "numeric" })}<br>${date(item, { hour: "2-digit", minute: "2-digit" })} – ${item.end} (UTC+7)</dd><dt>${t("where")}</dt><dd>${escape(item.room)}</dd><dt>${t("fee")}</dt><dd>${money(item.price)}</dd></dl><div class="capacity" aria-hidden="true"><span style="width:${full ? 100 : Math.round((item.registered / item.capacity) * 100)}%"></span></div><p class="meta">${full ? t("full") : `${item.seats} ${t("seats")}`}</p><button class="btn wide" data-register="${item.id}" ${full ? "disabled" : ""}>${full ? t("full") : t("register")}</button>${full ? `<p class="notice">${t("fullText")}</p>` : ""}</aside></div></div>`;
}

function organizer() {
  main.innerHTML = `<div class="wrap org-layout"><aside class="org-sidebar"><span class="avatar">U</span><h2>${t("orgName")}</h2><p>${t("organizer")}</p><a href="#organizer" aria-current="page">${t("titleColumn")}</a></aside><section class="org-content" aria-labelledby="manage-title"><span class="category">${t("organizer")}</span><h1 id="manage-title" style="margin-top:20px">${t("manageTitle")}</h1><p class="intro">${t("manageText")}</p>${["empty", "error", "loading"].includes(state.scenario) ? emptyPanel(state.scenario) : `<div class="table-wrap"><table><caption class="sr-only">${t("manageTitle")}</caption><thead><tr>${["titleColumn", "dateColumn", "statusColumn", "registered", "action"].map((key) => `<th scope="col">${t(key)}</th>`).join("")}</tr></thead><tbody>${data.workshops.map((item) => `<tr><td><div class="record-identity"><img class="workshop-thumb" src="${escape(item.banner || "assets/" + item.poster)}" alt="" width="48" height="48"><div><strong>${escape(item.status === "draft" && state.draft ? state.draft : localized(item.title))}</strong><small>${escape(item.room)}</small></div></div></td><td>${date(item)}<small>${date(item, { hour: "2-digit", minute: "2-digit" })}</small></td><td><span class="status ${item.status === "draft" ? "draft" : ""}">${t(item.status)}</span></td><td>${item.status === "draft" ? "—" : `${item.registered} / ${item.capacity}`}</td><td>${item.status === "draft" ? `<button class="small-btn" data-edit="${item.id}">${t("edit")}</button>` : `<a class="small-btn" href="#detail/${item.id}">${t("view")}</a>`}</td></tr>`).join("")}</tbody></table></div>`}<p class="notice">${t("orgNotice")}</p></section></div>`;
}

function brand() {
  main.innerHTML = `<div class="wrap brand-page"><h1>${t("brandTitle")}</h1><p class="intro">${t("brandText")}</p><section class="identity" aria-label="${t("identity")}"><img src="assets/unihub-logo.svg" alt="UniHub" width="330" height="87"></section><div class="brand-grid"><section class="brand-panel"><h2>${t("symbol")}</h2><div class="icon-row"><img class="symbol-large" src="assets/unihub-symbol.svg" alt="UniHub symbol" width="84" height="84"><a class="btn secondary" href="assets/unihub-logo.svg" target="_blank" rel="noopener">${t("logoDownload")}</a></div></section><section class="brand-panel"><h2>${t("smallSizes")}</h2><div class="icon-row">${[16, 24, 32].map((size) => `<img src="assets/unihub-symbol.svg" width="${size}" height="${size}" alt="UniHub ${size} px">`).join("")}<a href="assets/unihub-symbol.svg" target="_blank" rel="noopener">${t("iconDownload")}</a></div></section></div><h2 style="margin-top:40px">${t("palette")}</h2><div class="palette">${[
    ["#234BEB", "brandPrimary"],
    ["#DBF76A", "brandAccent"],
    ["#162142", "brandInk"],
    ["#F6F8FC", "brandSurface"],
  ]
    .map(
      ([hex, key]) =>
        `<div class="swatch"><div class="swatch-color" style="background:${hex}"></div><div class="swatch-label"><strong>${hex}</strong>${t(key)}</div></div>`,
    )
    .join("")}</div><p class="notice">${t("lightOnly")}</p></div>`;
}

function wallet() {
  const item = workshop(state.ticketId);
  main.innerHTML = `<div class="wrap"><h1 style="font-size:36px">${t("walletTitle")}</h1><p class="notice">${t("walletText")}</p>${state.registered ? `<article class="ticket"><img src="assets/poster-ai.svg" alt="" width="150" height="94"><div><span class="status">${t("ticketStatus")}</span><h2>${escape(localized(item.title))}</h2><p class="meta">${date(item)} · 09:00 – 11:00</p><p class="meta">${item.room}</p></div></article>` : `<section class="empty"><h2>${t("noTickets")}</h2><p>${t("noTicketsText")}</p><a class="btn" href="#discover">${t("heroCta")}</a></section>`}</div>`;
}

function render(focus = false) {
  if (!data) return;
  updateChrome();
  const [page, id] = route().split("/");
  if (page === "editor") renderEditor(id);
  else if (page === "members") renderMembers();
  else if (page === "detail") detail(id);
  else if (page === "organizer") organizer();
  else if (page === "brand") brand();
  else if (page === "wallet") wallet();
  else discover();
  renderShell();
  const tableRegion = document.querySelector(".table-wrap");
  if (tableRegion) {
    tableRegion.tabIndex = 0;
    tableRegion.setAttribute("role", "region");
    tableRegion.setAttribute("aria-label", t("manageTitle"));
  }
  if (focus) {
    main.focus({ preventScroll: true });
    window.scrollTo(0, 0);
  }
  clearTimeout(loadTimer);
  if (state.scenario === "loading")
    loadTimer = setTimeout(() => {
      state.scenario = "normal";
      document.querySelector("#scenario").value = "normal";
      render();
    }, 1200);
}

function closeDialog() {
  dialog.close();
}
function showDialog(content) {
  setDialogContent(content);
  dialog.showModal();
  dialog
    .querySelectorAll("[data-close]")
    .forEach((button) => button.addEventListener("click", closeDialog));
}
function registration(id) {
  const item = workshop(id);
  if (item.price > 0) {
    paidRegistration(item);
    return;
  }
  showDialog(
    `<h2 id="dialog-title">${t("registrationTitle")}</h2><p>${escape(localized(item.title))}</p><p>${t("registrationText")}</p><form id="registration" novalidate><label for="name">${t("name")}</label><input id="name" name="name" autocomplete="name" required maxlength="100" value="Minh Anh"><label for="email">${t("email")}</label><input id="email" name="email" type="email" autocomplete="email" required maxlength="254" value="minhanh@example.com"><p class="form-error" id="form-error" role="alert" hidden></p><div class="actions"><button class="btn secondary" type="button" data-close>${t("cancel")}</button><button class="btn" type="submit">${t("confirm")}</button></div></form>`,
  );
  document
    .querySelector("#registration")
    .addEventListener("submit", (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      if (!form.checkValidity() || !form.elements.name.value.trim()) {
        const error = document.querySelector("#form-error");
        error.hidden = false;
        error.textContent = t("validation");
        for (const input of form.querySelectorAll("input")) {
          const invalid = !input.checkValidity() || !input.value.trim();
          input.setAttribute("aria-invalid", String(invalid));
          if (invalid) input.setAttribute("aria-describedby", "form-error");
          else input.removeAttribute("aria-describedby");
        }
        form.querySelector("[aria-invalid=true]")?.focus();
        return;
      }
      state.registered = true;
      state.ticketId = id;
      setDialogContent(
        `<div class="confirmation-art" aria-hidden="true">✦</div><h2 id="dialog-title" tabindex="-1">${t("successTitle")}</h2><p>${t("successText")}</p><button class="btn wide" id="see-ticket">${t("seeTicket")}</button>`,
      );
      document.querySelector("#dialog-title").focus();
      document.querySelector("#see-ticket").addEventListener("click", () => {
        closeDialog();
        location.hash = "wallet";
      });
    });
}

function editDraft(id) {
  location.hash = "editor/" + id;
}

main.addEventListener("click", (event) => {
  const category = event.target.closest("[data-category]");
  if (category) {
    state.category = category.dataset.category;
    document
      .querySelectorAll("[data-category]")
      .forEach((button) =>
        button.setAttribute("aria-pressed", String(button === category)),
      );
    updateResults();
  }
  const register = event.target.closest("[data-register]");
  if (register) registration(register.dataset.register);
  const edit = event.target.closest("[data-edit]");
  if (edit) editDraft(edit.dataset.edit);
  if (event.target.closest("[data-action=recover]")) {
    if (state.scenario !== "error") {
      state.query = "";
      state.category = "all";
    }
    state.fee = "";
    state.from = "";
    state.to = "";
    state.scenario = "normal";
    document.querySelector("#scenario").value = "normal";
    render();
    (document.querySelector("#search") || main).focus();
  }
});

document.querySelector("#scenario").addEventListener("change", (event) => {
  state.scenario = event.target.value;
  render();
});
document.querySelector("#reset").addEventListener("click", () => {
  Object.assign(state, {
    category: "all",
    query: "",
    scenario: "normal",
    registered: false,
    ticketId: null,
    draft: null,
    fee: "",
    from: "",
    to: "",
  });
  document.querySelector("#scenario").value = "normal";
  document.querySelector("#toast").hidden = true;
  document.querySelector("#payment-result").value = "pending";
  activePayment = null;
  if (dialog.open) closeDialog();
  render();
  announce(t("reset"));
});
window.addEventListener("hashchange", () => {
  if (dialog.open) closeDialog();
  render(true);
});
Promise.all(
  ["scenarios.json", "vi.json"].map((file) =>
    fetch(`fixtures/${file}`).then((response) => {
      if (!response.ok) throw Error(`Fixture HTTP ${response.status}`);
      return response.json();
    }),
  ),
)
  .then(([fixtures, vi]) => {
    data = fixtures;
    data.locales.vi = vi;
    render();
  })
  .catch(() => {
    main.textContent =
      "Không tải được dữ liệu preview. Hãy chạy bằng local HTTP server và tải lại trang. / Preview data unavailable. Serve this directory over local HTTP and reload.";
  });
