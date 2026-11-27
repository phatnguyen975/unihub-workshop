import type { ReactNode } from "react";
import artwork from "@/assets/poster-design.svg";
import { useLocale } from "./useLocale";

export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  return (
    <div className="auth-layout">
      <aside className="welcome">
        <div className="art">
          <img src={artwork} width="800" height="500" alt="" />
        </div>
        <h2>{t("welcomeTitle")}</h2>
        <p>{t("welcomeText")}</p>
      </aside>
      <section className="auth-card" aria-labelledby="page-title">
        {children}
      </section>
    </div>
  );
}
