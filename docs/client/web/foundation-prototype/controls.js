"use strict";
let calendarMonth = new Date(2026, 10, 1);
let memberSuspended = false;
let bannerUrl = null;

function setDialogContent(content) {
  dialog.innerHTML = content;
  const title = dialog.querySelector("#dialog-title");
  if (title) {
    const header = document.createElement("div");
    header.className = "dialog-heading";
    title.before(header);
    header.append(title);
    const close = document.createElement("button");
    close.className = "dialog-close";
    close.type = "button";
    close.setAttribute("aria-label", t("close"));
    close.innerHTML =
      '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>';
    close.onclick = closeDialog;
    header.append(close);
  }
}

function setupControls() {
  document.querySelectorAll("[data-locale]").forEach((button) => {
    button.setAttribute(
      "aria-pressed",
      String(button.dataset.locale === state.locale),
    );
    button.onclick = () => {
      state.locale = button.dataset.locale;
      render();
      document.querySelector("#language").focus();
    };
  });
  const member = document.querySelector("#member-action");
  if (member) {
    member.textContent = memberSuspended
      ? label("Mở khóa tài khoản", "Restore account")
      : label("Khóa tài khoản", "Suspend account");
    member.onclick = () => {
      const action = member.textContent;
      showDialog(
        `<h2 id="dialog-title">${escape(action)}</h2><p>${label("Xác nhận thay đổi trạng thái tài khoản của Minh Trần?", "Confirm changing the account status for Minh Trần?")}</p><div class="actions"><button class="btn secondary" data-close>${t("cancel")}</button><button class="btn" id="confirm-member">${label("Xác nhận", "Confirm")}</button></div>`,
      );
      document.querySelector("#confirm-member").onclick = () => {
        memberSuspended = !memberSuspended;
        closeDialog();
        render();
        document.querySelector("#member-action").focus();
      };
    };
  }
  const editor = document.querySelector("#editor-form");
  if (editor) {
    const item = route().endsWith("/new")
      ? null
      : workshop(route().split("/")[1]);
    editor.insertAdjacentHTML(
      "afterbegin",
      `<section class="banner-field"><label for="banner-file">${label("Ảnh banner", "Banner image")}</label><div class="banner-preview"><img id="banner-preview" src="${item?.banner || "assets/" + (item?.poster || "poster-ai.svg")}" alt="${label("Xem trước banner", "Banner preview")}"><label class="btn secondary" for="banner-file">${label("Chọn ảnh", "Choose image")}</label><input class="sr-only" id="banner-file" type="file" accept="image/png,image/jpeg,image/webp"><p class="notice">PNG, JPG, WebP · ${label("Tối đa 5 MB", "Up to 5 MB")}</p><p id="banner-error" class="form-error" role="alert"></p></div></section>`,
    );
    document.querySelector("#banner-file").onchange = (e) => {
      const file = e.target.files[0];
      if (!file) return;
      if (
        !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
        file.size > 5 * 1024 * 1024
      ) {
        document.querySelector("#banner-error").textContent = label(
          "Chọn ảnh PNG, JPG hoặc WebP không quá 5 MB.",
          "Choose a PNG, JPG or WebP image up to 5 MB.",
        );
        return;
      }
      document.querySelector("#banner-error").textContent = "";
      if (bannerUrl) URL.revokeObjectURL(bannerUrl);
      bannerUrl = URL.createObjectURL(file);
      document.querySelector("#banner-preview").src = bannerUrl;
      if (item) item.banner = bannerUrl;
    };
  }
  positionPopovers();
}

function positionPopovers() {
  document.querySelectorAll("[popovertarget]").forEach((button) => {
    const panel = document.getElementById(button.getAttribute("popovertarget"));
    button.setAttribute("aria-expanded", "false");
    panel.addEventListener("toggle", (e) => {
      if (e.newState === "open") {
        const rect = button.getBoundingClientRect();
        panel.style.top =
          Math.max(
            12,
            Math.min(
              rect.bottom + 8,
              innerHeight - panel.getBoundingClientRect().height - 12,
            ),
          ) + "px";
      }
    });
    panel.addEventListener("beforetoggle", (e) => {
      button.setAttribute("aria-expanded", String(e.newState === "open"));
      if (e.newState === "open") {
        const rect = button.getBoundingClientRect();
        panel.style.top = Math.min(rect.bottom + 8, innerHeight - 340) + "px";
        const width = panel.id === "date-panel" ? 320 : 180;
        panel.style.width = width + "px";
        panel.style.left =
          Math.max(12, Math.min(rect.right - width, innerWidth - width - 12)) +
          "px";
      }
    });
  });
}

function renderFilters(container) {
  container.innerHTML = `<button class="filter-button" id="date-filter" popovertarget="date-panel"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18"/></svg> <span id="range-label">${label("Khoảng ngày", "Date range")}</span></button><div popover id="date-panel" class="control-popup calendar"><div class="calendar-heading"><button id="month-prev" class="icon-button" aria-label="${label("Tháng trước", "Previous month")}">←</button><strong id="month-title"></strong><button id="month-next" class="icon-button" aria-label="${label("Tháng sau", "Next month")}">→</button></div><p class="notice">${label("Chọn ngày bắt đầu, sau đó chọn ngày kết thúc.", "Choose a start date, then an end date.")}</p><div class="calendar-grid" id="calendar-grid"></div><div class="calendar-footer"><p id="range-preview" role="status"></p><button class="btn text" id="clear-dates">${label("Xóa ngày", "Clear dates")}</button></div></div><button class="filter-button" id="price-filter" popovertarget="price-panel"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="5" width="18" height="14" rx="3"/><circle cx="12" cy="12" r="3"/><path d="M6 12h1m10 0h1"/></svg><span id="price-label"></span></button><div popover id="price-panel" class="control-popup">${[
    ["", label("Tất cả mức phí", "All prices")],
    ["free", t("free")],
    ["paid", label("Có phí", "Paid")],
  ]
    .map(([value, text]) => `<button data-price="${value}">${text}</button>`)
    .join("")}</div>`;
  const paint = () => {
    const year = calendarMonth.getFullYear(),
      month = calendarMonth.getMonth();
    document.querySelector("#month-title").textContent =
      new Intl.DateTimeFormat(state.locale === "vi" ? "vi-VN" : "en-GB", {
        month: "long",
        year: "numeric",
      }).format(calendarMonth);
    const days = new Date(year, month + 1, 0).getDate(),
      offset = (new Date(year, month, 1).getDay() + 6) % 7;
    const weekdays =
      state.locale === "vi"
        ? ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]
        : ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];
    document.querySelector("#calendar-grid").innerHTML =
      weekdays.map((d) => `<span>${d}</span>`).join("") +
      "<span></span>".repeat(offset) +
      Array.from({ length: days }, (_, i) => {
        const key = `${year}-${String(month + 1).padStart(2, "0")}-${String(i + 1).padStart(2, "0")}`;
        return `<button data-day="${key}" aria-label="${key}" aria-pressed="${key === state.from || key === state.to}" class="${state.from && state.to && key > state.from && key < state.to ? "in-range" : ""}">${i + 1}</button>`;
      }).join("");
    document.querySelectorAll("[data-day]").forEach(
      (button) =>
        (button.onclick = () => {
          const day = button.dataset.day;
          if (!state.from || state.to) {
            state.from = day;
            state.to = "";
          } else {
            state.to = day;
            if (state.to < state.from)
              [state.from, state.to] = [state.to, state.from];
          }
          paint();
          updateResults();
        }),
    );
    const range = state.from
      ? `${state.from}${state.to ? " – " + state.to : ""}`
      : label("Khoảng ngày", "Date range");
    document.querySelector("#range-label").textContent = range;
    document.querySelector("#range-preview").textContent = range;
    document.querySelector("#price-label").textContent =
      state.fee === "paid"
        ? label("Có phí", "Paid")
        : state.fee === "free"
          ? t("free")
          : label("Phí tham gia", "Price");
  };
  document.querySelector("#month-prev").onclick = () => {
    calendarMonth = new Date(
      calendarMonth.getFullYear(),
      calendarMonth.getMonth() - 1,
      1,
    );
    paint();
  };
  document.querySelector("#month-next").onclick = () => {
    calendarMonth = new Date(
      calendarMonth.getFullYear(),
      calendarMonth.getMonth() + 1,
      1,
    );
    paint();
  };
  document.querySelector("#clear-dates").onclick = () => {
    state.from = "";
    state.to = "";
    paint();
    updateResults();
  };
  document.querySelectorAll("[data-price]").forEach(
    (button) =>
      (button.onclick = () => {
        state.fee = button.dataset.price;
        paint();
        document.querySelector("#price-panel").hidePopover();
        updateResults();
        document.querySelector("#price-filter").focus();
      }),
  );
  paint();
  document
    .querySelectorAll(".calendar-heading .icon-button")
    .forEach((button) => {
      button.innerHTML =
        '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' +
        (button.id === "month-prev" ? "m14 6-6 6 6 6" : "m10 6 6 6-6 6") +
        '"/></svg>';
    });
  positionPopovers();
}
