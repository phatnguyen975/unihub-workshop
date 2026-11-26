import {
  featuredWorkshops,
  findWorkshop,
  findWorkshops,
  type Criteria,
} from "../../model/discovery";

export function createWorkshopService() {
  let current = "normal";
  let failed = false;
  return {
    scenario(value: string) {
      current = value;
      failed = false;
    },
    reset() {
      current = "normal";
      failed = false;
    },
    async list(criteria: Criteria, locale: "en" | "vi") {
      if (current === "list-error" && !failed) {
        failed = true;
        throw new Error("List unavailable");
      }
      if (current === "offline") throw new Error("Offline");
      if (current === "slow")
        await new Promise((resolve) => setTimeout(resolve, 1600));
      return {
        empty: current === "empty",
        items: current === "empty" ? [] : findWorkshops(criteria, locale),
        featured:
          current === "empty" || current === "no-featured"
            ? []
            : featuredWorkshops(),
      };
    },
    async detail(slug: string) {
      if (current === "detail-error" && !failed) {
        failed = true;
        throw new Error("Detail unavailable");
      }
      if (current === "offline") throw new Error("Offline");
      return findWorkshop(slug) ?? null;
    },
  };
}

export type WorkshopService = ReturnType<typeof createWorkshopService>;
