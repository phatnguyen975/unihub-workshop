import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router";
import { Shell } from "./Shell";
import { IdentityProvider, AccessController } from "@/features/identity";
import { createMockIdentity } from "@/features/identity/data/mock/index";
import { LocaleProvider } from "@/shared/ui/locale";
async function organizer(path: string) {
  const service = createMockIdentity({ delay: async () => {} });
  const controller = new AccessController(service, () => {});
  await controller.login("minh@example.test", "UniHubDemo1!");
  render(
    <MemoryRouter initialEntries={[path]}>
      <LocaleProvider>
        <IdentityProvider controller={controller}>
          <Shell>
            <h1>Content</h1>
          </Shell>
        </IdentityProvider>
      </LocaleProvider>
    </MemoryRouter>,
  );
  await screen.findByRole("button", { name: "Account" });
}
describe("organizer shell boundaries", () => {
  it("does not wrap public home in the management sidebar", async () => {
    await organizer("/");
    expect(document.querySelector(".sidebar")).toBeNull();
  });
  it("does not wrap public workshop browsing in the management sidebar", async () => {
    await organizer("/workshops");
    expect(document.querySelector(".sidebar")).toBeNull();
  });
  it("provides a management entry and header menu only in workspace", async () => {
    await organizer("/organizer/workshops");
    expect(
      screen.getByRole("button", { name: "Open workshop navigation" }),
    ).toBeInTheDocument();
  });
});

it("keeps the staff workspace entry in the avatar popup rather than the header navigation", async () => {
  const service = createMockIdentity({ delay: async () => {} });
  const controller = new AccessController(service, () => {});
  await controller.login("an@example.test", "UniHubDemo1!");
  render(
    <MemoryRouter initialEntries={["/staff/workshops"]}>
      <LocaleProvider>
        <IdentityProvider controller={controller}>
          <Shell>
            <h1>Content</h1>
          </Shell>
        </IdentityProvider>
      </LocaleProvider>
    </MemoryRouter>,
  );
  await screen.findByRole("button", { name: "Account" });
  expect(
    document.querySelector('.product-nav a[href="/staff/workshops"]'),
  ).toBeNull();
});
