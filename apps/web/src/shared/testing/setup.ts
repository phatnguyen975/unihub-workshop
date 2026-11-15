import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";

// jsdom has no media-query API; actual motion behavior is covered in browser tests.
beforeEach(() => vi.stubGlobal("matchMedia", (query: string): MediaQueryList => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: () => {},
  removeListener: () => {},
  addEventListener: () => {},
  removeEventListener: () => {},
  dispatchEvent: () => false,
})));

afterEach(() => {
  cleanup();
  if (typeof window !== "undefined") window.history.replaceState(null, "", "/");
});
