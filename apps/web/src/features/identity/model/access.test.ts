import { describe, it, expect } from "vitest";
import {
  safeDestination,
  landing,
  loginSchema,
  replacementSchema,
} from "./access";
describe("access navigation and validation", () => {
  it.each([
    "https://evil.test",
    "//evil.test",
    "/login",
    "/%2f%2fevil.test",
    "/workshops?next=evil",
  ])("rejects unsafe intent %s", (value) =>
    expect(safeDestination(value, "STUDENT")).toBe(""),
  );
  it("preserves permitted query context and isolates roles", () => {
    expect(safeDestination("/workshops?q=design%20skills", "STUDENT")).toBe(
      "/workshops?q=design%20skills",
    );
    expect(safeDestination("/organizer/workshops", "STUDENT")).toBe("");
    expect(landing("STAFF")).toBe("/staff/workshops");
  });
  it("allows public workshop detail intent and rejects unsafe or role-conflicting targets", () => {
    expect(safeDestination("/workshops/design-sprint", "STUDENT")).toBe(
      "/workshops/design-sprint",
    );
    expect(safeDestination("/workshops/design-sprint", "ORGANIZER")).toBe(
      "/workshops/design-sprint",
    );
    expect(
      safeDestination("/workshops/design-sprint?next=%2Flogin", "STUDENT"),
    ).toBe("");
    expect(safeDestination("/workshops/%2F%2Fevil", "STUDENT")).toBe("");
    expect(safeDestination("/staff/workshops", "STUDENT")).toBe("");
  });
  it("normalizes email but never password", () => {
    expect(
      loginSchema.parse({
        email: " linh@example.test ",
        password: " keep spaces ",
      }),
    ).toEqual({ email: "linh@example.test", password: " keep spaces " });
  });
  it("rejects weak, unchanged and mismatched replacement", () => {
    const input = {
      email: "a@example.test",
      currentPassword: "DemoPass1!",
      newPassword: "DemoPass1!",
      confirmation: "different",
    };
    expect(replacementSchema.safeParse(input).success).toBe(false);
    expect(
      replacementSchema.safeParse({
        ...input,
        newPassword: "NextPass2!",
        confirmation: "NextPass2!",
      }).success,
    ).toBe(true);
  });
});

it.each(["/workshops?q=%zz", "/workshops?q=%00", "/workshops?q=%ff"])(
  "rejects malformed encoded intent %s",
  (value) => expect(safeDestination(value, "STUDENT")).toBe(""),
);
