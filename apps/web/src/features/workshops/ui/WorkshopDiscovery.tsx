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
import type { WorkshopService } from "../data/mock/service";

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
    queryKey: ["workshop-detail", slug],
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
    const refreshScenario = () => setRetry((value) => value + 1);
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
    if (slug || !read || !cursorStatus.invalidCursor) return;
    const next = new URLSearchParams(params);
    next.delete("cursor");
    setParams(next, { replace: true });
  }, [slug, read, cursorStatus.invalidCursor, params, setParams]);
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
      <section className="workshop-detail">
        <Link to={previous}>{vi ? "Về danh sách" : "Back to workshops"}</Link>
        <div className="workshop-detail-grid">
          <article>
            <img src={artwork} alt="" />
            <h1 id="page-title" tabIndex={-1}>
              {item.title[locale]}
            </h1>
            {item.description && (
              <>
                <h2>{vi ? "Giới thiệu" : "About this workshop"}</h2>
                <p>{item.description[locale]}</p>
              </>
            )}
            <h2>{vi ? "Diễn giả" : "Speakers"}</h2>
            {item.speakers.map((s) => (
              <p key={s.name}>{s.name}</p>
            ))}
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
            <p>
              {info.time} · {item.timezone}
            </p>
            <p>
              {new Intl.DateTimeFormat(locale, {
                dateStyle: "medium",
                timeStyle: "short",
                timeZone: item.timezone,
              }).format(new Date(item.endsAt))}
            </p>
            <p>{item.location}</p>
            <p>{info.seats}</p>
            {item.seats > 0 && userRole === null && (
              <Link
                className="btn"
                to={"/login?next=" + encodeURIComponent(location.pathname)}
              >
                {vi ? "Đăng nhập để tiếp tục" : "Sign in to continue"}
              </Link>
            )}
            {userRole === "STUDENT" && (
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
  const criteria = {
    query: params.get("q") ?? "",
    price: params.get("price") ?? "",
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
  };
  const items = read?.items ?? [];
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
  if (!read || read.key !== readKey)
    return (
      <section className="status-panel" role="status">
        {vi ? "Đang tải…" : "Loading…"}
      </section>
    );
  return (
    <section className="workshop-catalog">
      <div className="discovery-hero">
        <div>
          <h1 id="page-title" tabIndex={-1}>
            {vi
              ? "Khám phá workshop tại trường"
              : "Explore university workshops"}
          </h1>
          <p>
            {vi
              ? "Tìm workshop thực hành và gặp gỡ diễn giả."
              : "Find hands-on workshops and meet speakers."}
          </p>
        </div>
        {featuredItems.length > 0 && (
          <div>
            <Link
              to={"/workshops/" + featuredItems[featured].slug}
              state={{ catalog: location.pathname + location.search }}
            >
              <img src={artwork} alt="" />
              <strong>{featuredItems[featured].title[locale]}</strong>
            </Link>
            <div>
              <button
                disabled={featured === 0}
                onClick={() => setFeatured(featured - 1)}
                aria-label={vi ? "Trước" : "Previous"}
              >
                ‹
              </button>{" "}
              {featured + 1} / {featuredItems.length}{" "}
              <button
                disabled={featured === featuredItems.length - 1}
                onClick={() => setFeatured(featured + 1)}
                aria-label={vi ? "Tiếp" : "Next"}
              >
                ›
              </button>
            </div>
          </div>
        )}
      </div>
      <h2>{vi ? "Danh sách workshop" : "Workshop catalog"}</h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (query.trim().length <= 200)
            setFilter("q", normalizeSearch(query));
        }}
      >
        <label>
          {vi ? "Tìm workshop" : "Search workshops"}{" "}
          <input value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <button className="btn">{vi ? "Tìm kiếm" : "Search"}</button>
        {query.trim().length > 200 && (
          <p role="alert">
            {vi ? "Nhập tối đa 200 ký tự." : "Enter 200 characters or fewer."}
          </p>
        )}
      </form>
      <label>
        {vi ? "Giá" : "Price"}{" "}
        <select
          value={criteria.price}
          onChange={(e) => setFilter("price", e.target.value)}
        >
          <option value="">{vi ? "Tất cả" : "All prices"}</option>
          <option value="free">{vi ? "Miễn phí" : "Free"}</option>
          <option value="paid">{vi ? "Có phí" : "Paid"}</option>
        </select>
      </label>
      <label>
        {vi ? "Từ ngày" : "From"}{" "}
        <input
          type="date"
          value={dateFrom}
          onChange={(e) => changeDates(e.target.value, dateTo)}
        />
      </label>
      <label>
        {vi ? "Đến ngày" : "To"}{" "}
        <input
          type="date"
          value={dateTo}
          onChange={(e) => changeDates(dateFrom, e.target.value)}
        />
      </label>
      <div className="workshop-grid">
        {page.map((item) => {
          const info = facts(item, locale);
          return (
            <article key={item.slug}>
              <img src={artwork} alt="" />
              <h3>
                <Link
                  to={"/workshops/" + item.slug}
                  state={{ catalog: location.pathname + location.search }}
                >
                  {item.title[locale]}
                </Link>
              </h3>
              <p>
                {info.time} · {item.location}
              </p>
              <p>
                {info.price} · {info.seats}
              </p>
            </article>
          );
        })}
      </div>
      {items.length === 0 && (
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
      <nav aria-label={vi ? "Phân trang" : "Pagination"}>
        <button
          disabled={position === 0}
          onClick={() => go(pageResult.previousCursor ?? "")}
          aria-label={vi ? "Trước" : "Previous"}
        >
          ‹
        </button>
        <button
          disabled={!pageResult.hasNext}
          onClick={() => go(pageResult.nextCursor ?? "")}
          aria-label={vi ? "Tiếp" : "Next"}
        >
          ›
        </button>
      </nav>
    </section>
  );
}
