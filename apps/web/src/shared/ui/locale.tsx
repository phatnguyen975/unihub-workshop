import { useState, type PropsWithChildren } from "react";
import en from "./en.json";
import vi from "./vi.json";
import { Context } from "./useLocale";
import type { CopyKey } from "./useLocale";
export function LocaleProvider({ children }: PropsWithChildren) {
  const [locale, setLocale] = useState<"en" | "vi">("en");
  const t = (key: string) => (locale === "en" ? en : vi)[key as CopyKey] ?? key;
  return (
    <Context
      value={{
        locale,
        setLocale: (v) => {
          document.documentElement.lang = v;
          setLocale(v);
        },
        t,
      }}
    >
      {children}
    </Context>
  );
}
