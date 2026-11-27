import {
  ArrowUpRight,
  CalendarDays,
  Tag,
  Clock,
  MapPin,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router";
import { useLocale } from "@/shared/ui/useLocale";
import { useQuery } from "@tanstack/react-query";
import {
  normalizeSearch,
  normalizeDateRange,
  pageByCursor,
  type Workshop,
} from "../model/discovery";
import artwork from "@/assets/poster-design.svg";
import aiPoster from "@/assets/poster-ai.svg";
import careerPoster from "@/assets/poster-career.svg";
import type { WorkshopService } from "../data/mock/service";

const control =
  "min-h-11 rounded-[10px] border border-(--field-line) bg-(--surface) px-3 py-2 text-(--ink)! hover:bg-(--tint) disabled:cursor-default!";
const arrowControl = control + " grid size-11 place-items-center p-0";

function WorkshopPoster({ item }: { item: Workshop }) {
  const src =
    item.poster === "ai"
      ? aiPoster
      : item.poster === "career"
        ? careerPoster
        : artwork;
  return (
    <img
      src={src}
      className="aspect-[2/1] w-full object-cover"
      width={640}
      height={320}
      alt=""
    />
  );
}

function facts(item: Workshop, locale: "en" | "vi") {
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: item.timezone,
  }).format(new Date(item.startsAt));
  const price = item.price
    ? new Intl.NumberFormat(locale, {
        style: "currency",
        currency: "VND",
      }).format(item.price)
    : locale === "vi"
      ? "Miễn phí"
      : "Free";
  const seats = item.seats
    ? `${item.seats} ${locale === "vi" ? "chỗ còn lại" : "seats available"}`
    : locale === "vi"
      ? "Hết chỗ"
      : "Full";
  return { time, price, seats };
}

export function WorkshopDiscovery({
  userRole,
  service,
}: {
  userRole: "STUDENT" | "ORGANIZER" | "STAFF" | null;
  service: WorkshopService;
}) {
  const { locale } = useLocale();

  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") ?? "");
  const [dateFrom, setDateFrom] = useState(params.get("from") ?? "");
  const [dateTo, setDateTo] = useState(params.get("to") ?? "");
  const [featured, setFeatured] = useState(0);
  const [retry, setRetry] = useState(0);
  const [read, setRead] = useState<{
    key: string;
    empty: boolean;
    items: Workshop[];
    featured: Workshop[];
  } | null>(null);
  const [readError, setReadError] = useState(false);
  const slug = location.pathname.match(/^\/workshops\/([a-z0-9-]+)$/)?.[1];
  const vi = locale === "vi";
  const search = location.search;
  const activeCriteria = {
    query: params.get("q") ?? "",
    price: params.get("price") ?? "",
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
  };
  const cursor = params.get("cursor");
  const cursorStatus = pageByCursor(
    read?.items ?? [],
    cursor,
    activeCriteria,
    3,
  );
  const readKey = `${locale}:${search}:${retry}`;
  const detail = useQuery({
    queryKey: ["workshop-detail", slug, retry],
    queryFn: () => service.detail(slug!),
    enabled: !!slug,
    retry: false,
  });
  useEffect(() => {
    if (slug) return;
    let active = true;
    const values = new URLSearchParams(search);
    const criteria = {
      query: values.get("q") ?? "",
      price: values.get("price") ?? "",
      from: values.get("from") ?? "",
      to: values.get("to") ?? "",
    };
    service.list(criteria, locale).then(
      (result) => {
        if (active) {
          setRead({ ...result, key: readKey });
          setReadError(false);
        }
      },
      () => {
        if (active) {
          setRead(null);
          setReadError(true);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [service, slug, search, locale, retry, readKey]);
  useEffect(() => {
    if (!import.meta.env.DEV) return;
    const refreshScenario = () => {
      setFeatured(0);
      setRetry((value) => value + 1);
    };
    window.addEventListener(
      "unihub:workshop-scenario-updated",
      refreshScenario,
    );
    return () =>
      window.removeEventListener(
        "unihub:workshop-scenario-updated",
        refreshScenario,
      );
  }, []);
  useEffect(() => {
    if (slug || read?.key !== readKey || !cursorStatus.invalidCursor) return;
    const next = new URLSearchParams(params);
    next.delete("cursor");
    setParams(next, { replace: true });
  }, [slug, read, readKey, cursorStatus.invalidCursor, params, setParams]);
  if (slug) {
    if (detail.isPending)
      return <section role="status">{vi ? "Đang tải…" : "Loading…"}</section>;
    if (detail.isError)
      return (
        <section role="alert">
          <p>{vi ? "Không thể tải workshop." : "Could not load workshop."}</p>
          <button onClick={() => void detail.refetch()}>
            {vi ? "Thử lại" : "Retry"}
          </button>
        </section>
      );
    const item = detail.data;
    if (!item)
      return (
        <section className="status-panel">
          <h1>{vi ? "Không thể xem workshop" : "Workshop unavailable"}</h1>
          <Link to="/workshops">
            {vi ? "Về danh sách" : "Back to workshops"}
          </Link>
        </section>
      );
    const info = facts(item, locale);
    const previous =
      (location.state as { catalog?: string } | null)?.catalog || "/workshops";
    return (
      <section className="mx-auto w-[min(100%-40px,1280px)] py-6 pb-11 wrap-anywhere min-[651px]:w-[min(100%-64px,1280px)] [&_h1]:mb-3! [&_h1]:text-[clamp(1.85rem,5vw,2.7rem)]! [&_h1]:leading-tight [&_h2]:mb-3! [&_h2]:text-2xl [&_article_h2]:mt-5! [&_dt]:text-sm [&_dt]:text-(--muted) [&_dd]:mb-3 [&_aside_p]:mb-3!">
        <Link
          className={
            control +
            " inline-flex cursor-pointer items-center gap-1 font-semibold"
          }
          to={previous}
        >
          {vi ? "Về danh sách" : "Back to workshops"}
        </Link>
        <div className="mt-[18px] grid gap-4 min-[651px]:grid-cols-[minmax(0,2fr)_minmax(230px,1fr)] min-[651px]:items-start [&>*]:min-w-0 [&>*]:rounded-2xl [&>*]:border [&>*]:border-(--line) [&>*]:bg-(--surface) [&>*]:p-4 [&_article>img]:mb-[18px] [&_article>img]:rounded-2xl">
          <article>
            <WorkshopPoster item={item} />
            <h1 id="page-title" tabIndex={-1}>
              {item.title[locale]}
            </h1>
            {item.description && (
              <>
                <h2>{vi ? "Giới thiệu" : "About this workshop"}</h2>
                <p>{item.description[locale]}</p>
              </>
            )}
            {item.speakers.length > 0 && (
              <>
                <h2>{vi ? "Diễn giả" : "Speakers"}</h2>
                <ul className="mb-4 grid gap-2 [&_li]:flex [&_li]:items-center [&_li]:gap-2 [&_li_span]:grid [&_li_span]:size-9 [&_li_span]:place-items-center [&_li_span]:rounded-full [&_li_span]:bg-(--tint) [&_li_span]:font-bold">
                  {item.speakers.map(({ name }) => (
                    <li key={name}>
                      <span aria-hidden="true">
                        {name
                          .split(" ")
                          .slice(0, 2)
                          .map((part) => part[0])
                          .join("")}
                      </span>
                      {name}
                    </li>
                  ))}
                </ul>
              </>
            )}
            {item.brochure === "assets/design-sprint-brochure.html" && (
              <p>
                <a
                  href="/mock/design-sprint-brochure.html"
                  target="_blank"
                  rel="noreferrer"
                >
                  {vi ? "Xem brochure" : "View brochure"}
                </a>
                {" · "}
                <a href="/mock/design-sprint-brochure.html" download>
                  {vi ? "Tải brochure" : "Download brochure"}
                </a>
              </p>
            )}
          </article>
          <aside>
            <h2>{info.price}</h2>
            <dl>
              <dt>{vi ? "Thời gian" : "Time"}</dt>
              <dd>
                {info.time} · {item.timezone}
                <br />
                {new Intl.DateTimeFormat(locale, {
                  dateStyle: "medium",
                  timeStyle: "short",
                  timeZone: item.timezone,
                }).format(new Date(item.endsAt))}
              </dd>
              <dt>{vi ? "Địa điểm" : "Location"}</dt>
              <dd>{item.location}</dd>
            </dl>
            <p
              className={
                item.seats
                  ? "font-semibold text-(--success)"
                  : "font-semibold text-(--danger)"
              }
            >
              {info.seats}
            </p>
            {item.seats > 0 && userRole === null && (
              <Link
                className="btn w-full"
                to={"/login?next=" + encodeURIComponent(location.pathname)}
              >
                {vi ? "Đăng nhập để tiếp tục" : "Sign in to continue"}
              </Link>
            )}
            {item.seats > 0 && userRole === "STUDENT" && (
              <p>
                {vi
                  ? "Chưa thể đăng ký trong bản xem trước."
                  : "Registration is not available in this preview."}
              </p>
            )}
          </aside>
        </div>
      </section>
    );
  }
  const criteria = activeCriteria;
  const pending = read?.key !== readKey;
  const items = pending ? [] : (read?.items ?? []);
  const pageResult = pageByCursor(items, cursor, criteria, 3);
  const { start: position, items: page } = pageResult;
  const setFilter = (name: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(name, value);
    else next.delete(name);
    next.delete("cursor");
    setParams(next);
  };
  const go = (value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set("cursor", value);
    else next.delete("cursor");
    setParams(next);
  };
  const changeDates = (from: string, to: string) => {
    setDateFrom(from);
    setDateTo(to);
    const range = normalizeDateRange(from, to);
    if (!range && (from || to)) return;
    const next = new URLSearchParams(params);
    if (range) {
      next.set("from", range[0]);
      next.set("to", range[1]);
      setDateFrom(range[0]);
      setDateTo(range[1]);
    } else {
      next.delete("from");
      next.delete("to");
    }
    next.delete("cursor");
    setParams(next);
  };
  const featuredItems = read?.featured ?? [];
  const featuredIndex = Math.min(
    featured,
    Math.max(0, featuredItems.length - 1),
  );
  const featuredItem = featuredItems[featuredIndex];
  if (readError)
    return (
      <section className="status-panel" role="alert">
        <p>{vi ? "Không thể tải danh sách." : "Could not load workshops."}</p>
        <button
          onClick={() => {
            setReadError(false);
            setRetry((value) => value + 1);
          }}
        >
          {vi ? "Thử lại" : "Retry"}
        </button>
      </section>
    );
  if (!read)
    return (
      <section className="status-panel" role="status">
        {vi ? "Đang tải…" : "Loading…"}
      </section>
    );
  return (
    <section className="mx-auto w-[min(100%-40px,1280px)] py-6 pb-11 wrap-anywhere min-[651px]:w-[min(100%-64px,1280px)] [&_svg]:size-4 [&_svg]:shrink-0 [&_button:disabled]:opacity-65">
      <div className="discovery-hero grid items-center gap-6 overflow-hidden rounded-3xl bg-[#234beb] p-6 text-white min-[651px]:grid-cols-2 min-[651px]:p-8 min-[1000px]:gap-12 min-[1000px]:p-10 [&_h1]:mb-3! [&_h1]:text-[clamp(2rem,6vw,3.5rem)]! [&_h1]:leading-[1.16] [&_:focus-visible]:outline-white!">
        <div>
          <h1 id="page-title" tabIndex={-1}>
            {vi
              ? "Khám phá workshop tại trường"
              : "Explore university workshops"}
          </h1>
          <p className="mb-5! max-w-[410px] text-[#e4eaff]">
            {vi
              ? "Tìm workshop thực hành và gặp gỡ diễn giả."
              : "Find hands-on workshops and meet speakers."}
          </p>
          <a
            className="inline-flex min-h-11 items-center gap-1 rounded-[10px] bg-(--accent) px-3 py-2 font-semibold text-[#162142]!"
            href="#workshop-results"
          >
            {vi ? "Khám phá workshop" : "Explore workshops"}{" "}
            <ArrowUpRight aria-hidden="true" />
          </a>
        </div>
        {featuredItem && (
          <div className="grid min-w-0 gap-3.5 min-[651px]:px-3">
            <Link
              className="block rotate-3 overflow-hidden rounded-xl border-[6px] border-white bg-(--accent) text-[#162142]! shadow-[0_16px_32px_#11225b55] hover:[&_h2]:underline min-[651px]:rotate-5 min-[651px]:border-8"
              to={"/workshops/" + featuredItem.slug}
              state={{ catalog: location.pathname + location.search }}
            >
              <WorkshopPoster item={featuredItem} />
              <div className="p-4 [&_h2]:my-1.5! [&_h2]:text-[clamp(1.2rem,3vw,1.5rem)]! [&_small]:font-semibold">
                <small>{vi ? "Nổi bật" : "Featured"}</small>
                <h2>{featuredItem.title[locale]}</h2>
                <p>
                  {facts(featuredItem, locale).time} · {featuredItem.location}
                </p>
              </div>
            </Link>
            <div className="flex flex-wrap items-center justify-between gap-2 min-[651px]:mt-4">
              <button
                className={arrowControl}
                disabled={featuredIndex === 0}
                onClick={() => setFeatured(featuredIndex - 1)}
                aria-label={vi ? "Trước" : "Previous"}
              >
                <ChevronLeft aria-hidden="true" />
              </button>{" "}
              {featuredIndex + 1} / {featuredItems.length}{" "}
              <button
                className={arrowControl}
                disabled={featuredIndex === featuredItems.length - 1}
                onClick={() => setFeatured(featuredIndex + 1)}
                aria-label={vi ? "Tiếp" : "Next"}
              >
                <ChevronRight aria-hidden="true" />
              </button>
            </div>
          </div>
        )}
      </div>
      <section
        className="mt-8 scroll-mt-[140px] [&>h2]:mb-3! [&>h2]:text-[clamp(1.5rem,4vw,2rem)]!"
        id="workshop-results"
      >
        <h2>{vi ? "Danh sách workshop" : "Workshop catalog"}</h2>
        <div className="mb-3 flex flex-wrap items-end gap-2">
          <form
            className="grid min-w-0 flex-[1_1_310px] grid-cols-[minmax(0,1fr)_auto] gap-2 [&_p]:col-span-full"
            onSubmit={(e) => {
              e.preventDefault();
              if (query.trim().length <= 200)
                setFilter("q", normalizeSearch(query));
            }}
          >
            <label>
              <span className="sr-only">
                {vi ? "Tìm workshop" : "Search workshops"}
              </span>
              <input
                className={control + " w-full min-w-0"}
                placeholder={vi ? "Tìm workshop" : "Search workshops"}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </label>
            <button className="btn">{vi ? "Tìm kiếm" : "Search"}</button>
            {query.trim().length > 200 && (
              <p role="alert">
                {vi
                  ? "Nhập tối đa 200 ký tự."
                  : "Enter 200 characters or fewer."}
              </p>
            )}
          </form>
          <details
            className="group relative"
            onKeyDown={(event) => {
              if (event.key === "Escape" && event.currentTarget.open) {
                event.stopPropagation();
                event.currentTarget.open = false;
                event.currentTarget.querySelector("summary")?.focus();
              }
            }}
          >
            <summary
              className={
                control +
                " inline-flex cursor-pointer items-center gap-1 font-semibold"
              }
            >
              <CalendarDays aria-hidden="true" />
              {vi ? "Ngày" : "Dates"}
            </summary>
            <div className="absolute start-0 top-[calc(100%+6px)] z-10 grid w-max max-w-[min(248px,calc(100vw-40px))] gap-1.5 rounded-xl border border-(--line) bg-(--surface) p-3 shadow-lg min-[651px]:start-auto min-[651px]:end-0 group-last:start-auto group-last:end-0 [&_label]:grid [&_label]:text-sm">
              <label>
                {vi ? "Từ ngày" : "From"}{" "}
                <input
                  type="date"
                  className={control}
                  value={dateFrom}
                  onChange={(e) => changeDates(e.target.value, dateTo)}
                />
              </label>
              <label>
                {vi ? "Đến ngày" : "To"}{" "}
                <input
                  type="date"
                  className={control}
                  value={dateTo}
                  onChange={(e) => changeDates(dateFrom, e.target.value)}
                />
              </label>

              <button
                className={control}
                type="button"
                onClick={() => changeDates("", "")}
              >
                {vi ? "Xóa ngày" : "Clear dates"}
              </button>
            </div>
          </details>
          <details
            className="relative"
            onKeyDown={(event) => {
              if (event.key === "Escape" && event.currentTarget.open) {
                event.stopPropagation();
                event.currentTarget.open = false;
                event.currentTarget.querySelector("summary")?.focus();
              }
            }}
          >
            <summary
              className={
                control +
                " inline-flex cursor-pointer items-center gap-1 font-semibold"
              }
            >
              <Tag aria-hidden="true" />
              {vi ? "Giá" : "Price"}
            </summary>
            <div className="absolute start-0 top-[calc(100%+6px)] z-10 grid w-max max-w-[min(248px,calc(100vw-40px))] gap-1.5 rounded-xl border border-(--line) bg-(--surface) p-3 shadow-lg min-[651px]:start-auto min-[651px]:end-0 group-last:start-auto group-last:end-0 [&_label]:grid [&_label]:text-sm">
              {[
                ["", vi ? "Tất cả" : "All prices"],
                ["free", vi ? "Miễn phí" : "Free"],
                ["paid", vi ? "Có phí" : "Paid"],
              ].map(([value, label]) => (
                <button
                  type="button"
                  key={value}
                  className={
                    control +
                    " text-left aria-pressed:border-(--brand) aria-pressed:bg-(--tint)"
                  }
                  aria-pressed={criteria.price === value}
                  onClick={(event) => {
                    const popup = event.currentTarget.closest("details");
                    if (popup) {
                      popup.open = false;
                      popup.querySelector("summary")?.focus();
                    }
                    setFilter("price", value);
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
          </details>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {Object.entries({
            q: criteria.query,
            price: criteria.price,
            dates:
              criteria.from && criteria.to
                ? criteria.from + " – " + criteria.to
                : "",
          })
            .filter(([, value]) => value)
            .map(([key, value]) => (
              <button
                key={key}
                className={control + " text-sm"}
                onClick={() => {
                  if (key === "dates") changeDates("", "");
                  else {
                    if (key === "q") setQuery("");
                    setFilter(key, "");
                  }
                }}
                aria-label={(vi ? "Xóa bộ lọc: " : "Remove filter: ") + value}
              >
                {key === "price"
                  ? value === "free"
                    ? vi
                      ? "Miễn phí"
                      : "Free"
                    : vi
                      ? "Có phí"
                      : "Paid"
                  : value}{" "}
                ×
              </button>
            ))}
        </div>
        {items.length > 0 && (
          <p className="mt-4! mb-3! text-(--muted)" role="status">
            {items.length} {vi ? "kết quả" : "results"}
          </p>
        )}
        <div className="workshop-grid grid gap-4 min-[651px]:grid-cols-2 min-[1000px]:grid-cols-3 [&_article]:min-w-0 [&_article]:rounded-2xl [&_article]:border [&_article]:border-(--line) [&_article]:bg-(--surface)">
          {page.map((item) => {
            const info = facts(item, locale);
            return (
              <article key={item.slug}>
                <div className="relative [&_img]:rounded-t-[15px]">
                  <WorkshopPoster item={item} />
                  <span
                    className="absolute top-3 left-3 grid min-w-[50px] place-items-center rounded-[9px] bg-(--surface) px-[7px] py-[5px] text-[0.8rem] [&_strong]:text-[1.2rem] [&_strong]:leading-none"
                    aria-hidden="true"
                  >
                    <strong>
                      {new Intl.DateTimeFormat(locale, {
                        day: "numeric",
                        timeZone: item.timezone,
                      }).format(new Date(item.startsAt))}
                    </strong>
                    {new Intl.DateTimeFormat(locale, {
                      month: "short",
                      timeZone: item.timezone,
                    }).format(new Date(item.startsAt))}
                  </span>
                </div>
                <div className="grid gap-1.25 p-4 [&_h3]:text-lg [&_h3]:font-bold [&_h3_a]:text-inherit! [&_h3_a:hover]:underline [&_p]:flex [&_p]:items-center [&_p]:gap-1.25 [&_p]:text-sm [&_p]:text-(--muted)">
                  <h3>
                    <Link
                      to={"/workshops/" + item.slug}
                      state={{ catalog: location.pathname + location.search }}
                    >
                      {item.title[locale]}
                    </Link>
                  </h3>
                  <p>
                    <Clock aria-hidden="true" /> {info.time}
                  </p>
                  <p>
                    <MapPin aria-hidden="true" /> {item.location}
                  </p>
                  <div className="mt-2 flex flex-wrap justify-between gap-x-2.5 gap-y-1 border-t border-(--line) pt-3 text-sm">
                    <strong>{info.price}</strong>
                    <span
                      className={
                        item.seats
                          ? "font-semibold text-(--success)"
                          : "font-semibold text-(--danger)"
                      }
                    >
                      {info.seats}
                    </span>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
        {pending && <p role="status">{vi ? "Đang tải…" : "Loading…"}</p>}
        {!pending && items.length === 0 && (
          <p>
            {read.empty
              ? vi
                ? "Chưa có workshop nào."
                : "There are no workshops yet."
              : vi
                ? "Không có workshop phù hợp."
                : "No workshops match your search."}{" "}
            {!read.empty && (
              <button
                onClick={() => {
                  setQuery("");
                  setDateFrom("");
                  setDateTo("");
                  setParams({});
                }}
              >
                {vi ? "Xóa bộ lọc" : "Clear filters"}
              </button>
            )}
          </p>
        )}
        {items.length > 0 && (
          <nav
            className="mt-[18px] flex items-center justify-center gap-2"
            aria-label={vi ? "Phân trang" : "Pagination"}
          >
            <button
              className={arrowControl}
              disabled={position === 0}
              onClick={() => go(pageResult.previousCursor ?? "")}
              aria-label={vi ? "Trước" : "Previous"}
            >
              <ChevronLeft aria-hidden="true" />
            </button>
            <button
              className={arrowControl}
              disabled={!pageResult.hasNext}
              onClick={() => go(pageResult.nextCursor ?? "")}
              aria-label={vi ? "Tiếp" : "Next"}
            >
              <ChevronRight aria-hidden="true" />
            </button>
          </nav>
        )}
      </section>
    </section>
  );
}
