import fixtures from "../data/mock/fixtures.json";

export interface Workshop {
  slug: string;
  poster: string;
  title: { en: string; vi: string };
  startsAt: string;
  endsAt: string;
  timezone: string;
  price: number;
  seats: number;
  location: string;
  published: boolean;
  speakers: { name: string }[];
  description: { en: string; vi: string } | null;
  brochure: string | null;
}

export interface Criteria {
  query: string;
  price: string;
  from: string;
  to: string;
}

export function normalizeSearch(value: string): string {
  return value.trim();
}

export function normalizeDateRange(
  from: string,
  to: string,
): [string, string] | null {
  if (!from || !to) return null;
  return from <= to ? [from, to] : [to, from];
}

export function criteriaKey(criteria: Criteria): string {
  return JSON.stringify({
    ...criteria,
    query: normalizeSearch(criteria.query),
  });
}

export function pageByCursor(
  items: Workshop[],
  cursor: string | null,
  criteria: Criteria,
  pageSize = 3,
) {
  const [key, slug] = cursor?.split("~", 2) ?? [];
  const valid =
    key === encodeURIComponent(criteriaKey(criteria)) &&
    items.some((item) => item.slug === slug);
  const start = valid ? items.findIndex((item) => item.slug === slug) + 1 : 0;
  const page = items.slice(start, start + pageSize);
  const hasNext = start + pageSize < items.length;
  const last = page.at(-1);
  return {
    items: page,
    hasPrevious: start > 0,
    hasNext,
    start,
    nextCursor:
      hasNext && last
        ? `${encodeURIComponent(criteriaKey(criteria))}~${last.slug}`
        : null,
    previousCursor:
      start > pageSize
        ? `${encodeURIComponent(criteriaKey(criteria))}~${items[start - pageSize - 1].slug}`
        : null,
    invalidCursor: !!cursor && !valid,
  };
}

export function findWorkshops(
  criteria: Criteria,
  locale: "en" | "vi",
): Workshop[] {
  const query = normalizeSearch(criteria.query).toLocaleLowerCase(locale);
  return fixtures.workshops
    .filter((item) => {
      const text = [
        item.title[locale],
        item.description?.[locale],
        item.location,
        item.slug,
        ...item.speakers.map((speaker) => speaker.name),
      ]
        .join(" ")
        .toLocaleLowerCase(locale);
      const day = new Intl.DateTimeFormat("sv-SE", {
        timeZone: item.timezone,
      }).format(new Date(item.startsAt));
      return (
        item.published &&
        text.includes(query) &&
        (!criteria.price ||
          (item.price === 0) === (criteria.price === "free")) &&
        (!criteria.from || day >= criteria.from) &&
        (!criteria.to || day <= criteria.to)
      );
    })
    .sort(
      (a, b) =>
        a.startsAt.localeCompare(b.startsAt) || a.slug.localeCompare(b.slug),
    );
}

export function findWorkshop(slug: string): Workshop | undefined {
  return fixtures.workshops.find(
    (item) => item.published && item.slug === slug,
  );
}

export function featuredWorkshops(): Workshop[] {
  return fixtures.featured
    .map((slug) => findWorkshop(slug))
    .filter((item): item is Workshop => item !== undefined)
    .slice(0, 3);
}
