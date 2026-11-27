import { createContext, useContext } from "react";
import type en from "./en.json";
export type CopyKey = keyof typeof en;
export const Context = createContext<{
  locale: "en" | "vi";
  setLocale: (v: "en" | "vi") => void;
  t: (key: string) => string;
}>({ locale: "en", setLocale: () => {}, t: (key) => key });
export const useLocale = () => useContext(Context);
