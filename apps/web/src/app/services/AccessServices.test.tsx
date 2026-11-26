import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useQueryClient } from "@tanstack/react-query";
import { describe, it, expect } from "vitest";
import { useAccess } from "@/features/identity";
import { AppProviders } from "../providers/AppProviders";
import { AccessServices } from "./AccessServices";
function Probe() {
  const query = useQueryClient();
  const { controller, state } = useAccess();
  return (
    <>
      <p>{state.kind}</p>
      <button
        onClick={() =>
          void controller.login("linh@example.test", "UniHubDemo1!")
        }
      >
        Login
      </button>
      <button
        onClick={() =>
          query.setQueryData(["private", "previous-account"], {
            name: "Private record",
          })
        }
      >
        Seed private cache
      </button>
      <button
        onClick={() => {
          controller.clearLocal();
          expect(
            query.getQueryData(["private", "previous-account"]),
          ).toBeUndefined();
        }}
      >
        Clear session
      </button>
    </>
  );
}
describe("composition identity/query isolation", () => {
  it("removes private projections on local removal", async () => {
    render(
      <AppProviders>
        <AccessServices>
          <Probe />
        </AccessServices>
      </AppProviders>,
    );
    await screen.findByText("anonymous");
    await userEvent.click(screen.getByRole("button", { name: "Login" }));
    await screen.findByText("authenticated");
    await userEvent.click(
      screen.getByRole("button", { name: "Seed private cache" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Clear session" }),
    );
    expect(await screen.findByText("anonymous")).toBeVisible();
  });
});
