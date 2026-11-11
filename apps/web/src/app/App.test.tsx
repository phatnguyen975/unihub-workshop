import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("application routing", () => {
  it("keeps the minimal home screen", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "UniHub Web", level: 1 }),
    ).toBeVisible();
  });

  it("recovers from an unknown deep link through a real navigation link", async () => {
    window.history.replaceState(null, "", "/missing-route");
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Page not found" }),
    ).toBeVisible();
    await userEvent
      .setup()
      .click(screen.getByRole("link", { name: "Return home" }));
    expect(screen.getByRole("heading", { name: "UniHub Web" })).toBeVisible();
    expect(window.location.pathname).toBe("/");
  });
});
