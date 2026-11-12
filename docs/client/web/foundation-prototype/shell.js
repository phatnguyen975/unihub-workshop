"use strict";
const label = (vi, en) => (state.locale === "vi" ? vi : en);
let theme = "light";
let slide = 0;
function shellPanel(title, body) {
  showDialog(`<h2 id="dialog-title">${title}</h2>${body}`);
}
function renderShell() {
  document
    .querySelectorAll('img[src*="unihub-logo"]')
    .forEach(
      (img) =>
        (img.src =
          theme === "dark"
            ? "assets/unihub-logo-dark.svg"
            : "assets/unihub-logo.svg"),
    );
  const org = ["organizer", "editor", "members"].includes(
    route().split("/")[0],
  );
  document.querySelector(".product-nav").hidden = org;
  document.querySelector(".header-actions").innerHTML = `
    <button class="icon-button" id="notifications" aria-label="${label("Thông báo", "Notifications")}"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3Z"/><path d="M10 20h4"/></svg></button>
    <button class="icon-button" id="theme" aria-label="${label("Đổi giao diện sáng tối", "Toggle light and dark theme")}" aria-pressed="${theme === "dark"}"><svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${theme === "light" ? '<path d="M20.5 13.5A9 9 0 0 1 10.5 3a9 9 0 1 0 10 10.5Z"/>' : '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1"/>'}</svg></button>
    <div class="control-group"><button class="icon-button locale" id="language" aria-label="${t("language")}" popovertarget="locale-panel">${state.locale === "vi" ? "VN" : "EN"}</button><div popover id="locale-panel" class="control-popup"><button data-locale="vi" lang="${state.locale}">${label("Tiếng Việt", "Vietnamese")}</button><button data-locale="en" lang="${state.locale}">${label("Tiếng Anh", "English")}</button></div></div>
    <div class="account"><button class="avatar icon-button" id="account" aria-expanded="false" aria-controls="account-panel" aria-label="${label("Tài khoản", "Account")}">${org ? "LH" : "MA"}</button><div class="account-panel" id="account-panel" hidden><strong>${org ? "Linh Hoàng" : "Minh Anh"}</strong><small>${org ? "linhhoang" : "minhanh"}@example.com</small><button data-account="profile">${label("Thông tin cá nhân", "Profile")}</button><button data-account="security">${label("Bảo mật tài khoản", "Account security")}</button><button data-account="logout">${label("Đăng xuất", "Sign out")}</button></div></div>`;
  document.querySelector("#theme").onclick = () => {
    theme = theme === "light" ? "dark" : "light";
    document.documentElement.dataset.theme = theme;
    render();
  };
  document.querySelector("#account").onclick = () => {
    const panel = document.querySelector("#account-panel");
    panel.hidden = !panel.hidden;
    document
      .querySelector("#account")
      .setAttribute("aria-expanded", String(!panel.hidden));
  };
  document.querySelectorAll("[data-account]").forEach(
    (button) =>
      (button.onclick = () => {
        document.querySelector("#account-panel").hidden = true;
        shellPanel(
          button.textContent,
          `<p>${button.dataset.account === "logout" ? label("Bạn muốn đăng xuất khỏi tài khoản này?", "Sign out of this account?") : label("Quản lý thông tin và bảo mật tài khoản của bạn.", "Manage your account information and security.")}</p><button class="btn" data-close>${t("close")}</button>`,
        );
      }),
  );
  document.querySelector("#notifications").onclick = () =>
    shellPanel(
      label("Thông báo", "Notifications"),
      `<p>${label("Bạn chưa có thông báo mới.", "You have no new notifications.")}</p>`,
    );
  document.querySelector("footer").innerHTML =
    `<div><strong>UniHub</strong><p>${t("footer")}</p><small>© UniHub · 2026</small></div><nav aria-label="${label("Liên kết cuối trang", "Footer links")}"><a href="#brand">${label("Về UniHub", "About UniHub")}</a><button data-footer="help">${label("Hướng dẫn tham gia", "Getting started")}</button><button data-footer="access">${label("Hỗ trợ truy cập", "Accessibility")}</button></nav>`;
  document
    .querySelectorAll("[data-footer]")
    .forEach(
      (button) =>
        (button.onclick = () =>
          shellPanel(
            button.textContent,
            `<p>${button.dataset.footer === "help" ? label("Tìm workshop, xem thông tin và đăng ký tham gia. Workshop có phí cần hoàn tất thanh toán trước khi nhận vé.", "Discover a workshop, check its details and register. Paid workshops require completed payment before a ticket is issued.") : label("Bạn có thể dùng bàn phím để di chuyển, đổi ngôn ngữ và chọn giao diện sáng hoặc tối.", "Use keyboard navigation, change language and choose a light or dark appearance.")}</p>`,
          )),
    );
  if (org) {
    const sidebar = document.querySelector(".org-sidebar");
    if (sidebar)
      sidebar.innerHTML = `<button class="btn secondary org-menu" aria-expanded="false">☰ ${label("Menu quản lý", "Management menu")}</button><nav class="org-links" aria-label="${label("Quản lý tổ chức", "Organization management")}"><h2>${t("orgName")}</h2><a href="#organizer">Workshop</a><a href="#members">${label("Thành viên", "Members")}</a></nav>`;
    document.querySelector(".org-menu")?.addEventListener("click", (e) => {
      const open = e.currentTarget.getAttribute("aria-expanded") !== "true";
      e.currentTarget.setAttribute("aria-expanded", String(open));
      document.querySelector(".org-links").classList.toggle("open", open);
    });
    if (route() === "organizer") {
      const title = document.querySelector(".org-content h1");
      title.insertAdjacentHTML(
        "afterend",
        `<a class="btn create-draft" href="#editor/new"><svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg><span>${label("Tạo workshop", "Create workshop")}</span></a>`,
      );
    }
  }
  document.querySelectorAll(".org-links a").forEach((a) => {
    if (a.hash === "#" + (route().startsWith("editor") ? "organizer" : route()))
      a.setAttribute("aria-current", "page");
  });
  if (route() === "brand") {
    document.querySelector(".brand-page>h2").textContent =
      theme === "dark"
        ? label("Bộ màu dark", "Dark palette")
        : label("Bộ màu light", "Light palette");
    const values =
      theme === "dark"
        ? ["#9CADFF", "#DBF76A", "#EDF1FF", "#0B1222"]
        : ["#234BEB", "#DBF76A", "#162142", "#F6F8FC"];
    document.querySelectorAll(".swatch").forEach((el, i) => {
      el.querySelector(".swatch-color").style.background = values[i];
      el.querySelector("strong").textContent = values[i];
    });
    document.querySelector(".brand-page>.notice").textContent = label(
      "Logo và palette light đã duyệt. Palette dark đang chờ duyệt.",
      "Logo and light palette approved. Dark palette awaits review.",
    );
  }
  setupControls();
  const filters = document.querySelector("#filters");
  if (filters) {
    renderFilters(filters);
    const hero = document.querySelector(".hero");
    hero.insertAdjacentHTML(
      "beforeend",
      `<div class="carousel-controls"><button class="icon-button" id="previous" aria-label="${label("Workshop trước", "Previous workshop")}">←</button><span id="slide-count" aria-live="polite"></span><button class="icon-button" id="next" aria-label="${label("Workshop tiếp theo", "Next workshop")}">→</button></div>`,
    );
    const paint = () => {
      const items = data.workshops.filter((w) => w.status === "published");
      slide = (slide + items.length) % items.length;
      const w = items[slide];
      const poster = document.querySelector(".hero-poster");
      poster.href = "#detail/" + w.id;
      poster.querySelector("img").src = "assets/" + w.poster;
      poster.querySelector("h2").textContent = localized(w.title);
      poster.querySelector("p").textContent =
        date(w) + " · " + date(w, { hour: "2-digit", minute: "2-digit" });
      document.querySelector("#slide-count").textContent =
        `${slide + 1} / ${items.length}`;
    };
    document.querySelector("#previous").onclick = () => {
      slide--;
      paint();
    };
    document.querySelector("#next").onclick = () => {
      slide++;
      paint();
    };
    paint();
  }
  document.querySelectorAll(".carousel-controls button").forEach((button) => {
    button.innerHTML =
      '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' +
      (button.id === "previous" ? "m14 6-6 6 6 6" : "m10 6 6 6-6 6") +
      '"/></svg>';
  });
}
function renderEditor(id) {
  const item = id === "new" ? null : workshop(id);
  main.innerHTML = `<div class="wrap org-layout"><aside class="org-sidebar"></aside><section class="org-content editor"><a class="back" href="#organizer">← ${t("back")}</a><h1>${item ? t("editTitle") : label("Tạo workshop", "Create workshop")}</h1><form id="editor-form"><label for="organization">${label("Tổ chức", "Organization")}</label><input id="organization" value="${t("orgName")}" readonly><label for="draft-title">${t("workshopTitle")}</label><input id="draft-title" required maxlength="150" value="${escape(item ? state.draft || localized(item.title) : "")}"><label for="description">${label("Mô tả", "Description")}</label><textarea id="description" rows="5">${item ? escape(localized(item.description)) : ""}</textarea><div class="editor-grid"><label>${t("when")}<input type="datetime-local" required value="${item ? item.start.slice(0, 16) : "2026-11-20T09:00"}"></label><label>${t("where")}<input required value="${item ? escape(item.room) : ""}"></label><label>${label("Số chỗ", "Capacity")}<input type="number" min="1" required value="${item ? item.capacity : 50}"></label><label>${t("fee")} (VND)<input type="number" min="0" required value="${item ? item.price : 0}"></label></div><div class="editor-actions"><a class="btn secondary" href="#organizer">${t("cancel")}</a><button class="btn secondary" name="action" value="save">${t("save")}</button><button class="btn" name="action" value="publish">${label("Công bố", "Publish")}</button></div></form></section></div>`;
  document.querySelector("#editor-form").onsubmit = (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const inputs = form.querySelectorAll(".editor-grid input");
    const title = document.querySelector("#draft-title").value.trim();
    if (!title) return;
    const record = item || {
      ...data.workshops[3],
      id: "created-" + data.workshops.length,
      title: { vi: title, en: title },
      description: { vi: "", en: "" },
    };
    record.banner = document.querySelector("#banner-preview")?.src;
    record.title[state.locale] = title;
    record.description[state.locale] =
      document.querySelector("#description").value;
    record.start = inputs[0].value + ":00+07:00";
    record.room = inputs[1].value;
    record.capacity = Number(inputs[2].value);
    record.price = Number(inputs[3].value);
    record.status = e.submitter.value === "publish" ? "published" : "draft";
    if (!item) data.workshops.push(record);
    state.draft = null;
    location.hash = "organizer";
  };
}
function renderMembers() {
  main.innerHTML = `<div class="wrap org-layout"><aside class="org-sidebar"></aside><section class="org-content"><h1>${label("Thành viên", "Members")}</h1><p class="notice">${label("Danh sách thành viên trong tổ chức của bạn.", "Members belonging to your organization.")}</p><div class="table-wrap"><table><thead><tr><th>${t("name")}</th><th>Email</th><th>${label("Vai trò", "Role")}</th><th>${t("action")}</th></tr></thead><tbody><tr><td><div class="record-identity"><span class="member-avatar" aria-hidden="true">LH</span><strong>Linh Hoàng</strong></div></td><td>linhhoang@example.com</td><td>Organizer</td><td>—</td></tr><tr><td><div class="record-identity"><span class="member-avatar" aria-hidden="true">MT</span><strong>Minh Trần</strong></div></td><td>minhtran@example.com</td><td>Staff</td><td><button class="small-btn" id="member-action">${label("Khóa tài khoản", "Suspend account")}</button></td></tr></tbody></table></div></section></div>`;
}
function paymentSteps(stage) {
  return `<ol class="payment-steps" aria-label="${label("Tiến trình đăng ký", "Registration progress")}">${[label("Giữ chỗ", "Reserve"), label("Thanh toán", "Pay"), label("Nhận vé", "Ticket")].map((s, i) => `<li class="${i < stage ? "complete" : i === stage ? "current" : "upcoming"}" ${i === stage ? 'aria-current="step"' : ""}><span class="step-dot" aria-hidden="true"></span><span>${s}</span>${i < stage ? `<span class="sr-only">${label("Đã hoàn tất", "Completed")}</span>` : ""}</li>`).join("")}</ol>`;
}
let activePayment = null;
function paidRegistration(item) {
  activePayment = item;
  const reservation = () => {
    showDialog(
      `<h2 id="dialog-title">${label("Đăng ký workshop", "Workshop registration")}</h2>${paymentSteps(0)}<p>${escape(localized(item.title))}</p><strong>${money(item.price)}</strong><p>${label("Giữ chỗ trước khi chuyển sang thanh toán.", "Reserve your place before continuing to payment.")}</p><button class="btn wide" id="reserve">${label("Giữ chỗ", "Reserve place")}</button>`,
    );
    document.querySelector("#reserve").onclick = payment;
  };
  const payment = () => {
    setDialogContent(
      `<h2 id="dialog-title">${label("Đã giữ chỗ", "Place reserved")}</h2>${paymentSteps(1)}<p>${label("Hoàn tất thanh toán để nhận vé. Giữ chỗ chưa phải là đăng ký thành công.", "Complete payment to receive your ticket. A reservation is not a confirmed registration.")}</p><strong>${money(item.price)}</strong><button class="btn wide" id="pay">${label("Tiếp tục thanh toán", "Continue to payment")}</button><button class="btn text" id="leave">${label("Để sau", "Not now")}</button>`,
    );
    document.querySelector("#leave").onclick = closeDialog;
    document.querySelector("#pay").onclick = () => {
      setDialogContent(
        `<h2 id="dialog-title">${label("Đang chờ kết quả thanh toán", "Awaiting payment confirmation")}</h2>${paymentSteps(1)}<p>${label("Vé chỉ được cấp sau khi thanh toán thành công.", "Tickets are issued only after successful payment.")}</p><button class="btn secondary" id="pending-close">${t("close")}</button>`,
      );
      document.querySelector("#pending-close").onclick = closeDialog;
      document
        .querySelector("#payment-result")
        .dispatchEvent(new Event("change"));
    };
  };
  reservation();
}
document.addEventListener("click", (e) => {
  if (!e.target.closest(".account")) {
    const panel = document.querySelector("#account-panel");
    if (panel) {
      panel.hidden = true;
      document.querySelector("#account").setAttribute("aria-expanded", "false");
    }
  }
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    const panel = document.querySelector("#account-panel");
    if (panel && !panel.hidden) {
      panel.hidden = true;
      document.querySelector("#account").setAttribute("aria-expanded", "false");
      document.querySelector("#account").focus();
    }
  }
});

document.querySelector("#payment-result").onchange = (e) => {
  if (!dialog.open || !activePayment) return;
  if (e.target.value === "success") {
    state.registered = true;
    state.ticketId = activePayment.id;
    setDialogContent(
      `<h2 id="dialog-title">${label("Thanh toán thành công", "Payment successful")}</h2>${paymentSteps(2)}<p>${t("successText")}</p><strong>${escape(localized(activePayment.title))}</strong><button class="btn wide" id="paid-ticket">${t("seeTicket")}</button>`,
    );
    document.querySelector("#paid-ticket").onclick = () => {
      closeDialog();
      location.hash = "wallet";
    };
  }
  if (e.target.value === "failure") {
    setDialogContent(
      `<h2 id="dialog-title">${label("Thanh toán chưa thành công", "Payment unsuccessful")}</h2><p>${label("Bạn chưa được cấp vé. Hãy kiểm tra trạng thái thanh toán trước khi thử lại.", "No ticket has been issued. Check payment status before retrying.")}</p><button class="btn secondary" id="payment-close">${t("close")}</button>`,
    );
    document.querySelector("#payment-close").onclick = closeDialog;
  }
};
