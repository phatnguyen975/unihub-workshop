// @vitest-environment node
import path from "node:path";
import { Linter } from "eslint";
import { describe, expect, it } from "vitest";
import plugin from "./eslint-boundaries.js";

function messages(owner, source, dynamic = false) {
  const code = dynamic ? `import('${source}');` : `import '${source}';`;
  return new Linter().verify(
    code,
    [{ plugins: { unihub: plugin }, rules: { "unihub/boundaries": "error" } }],
    { filename: path.resolve("src", owner) },
  );
}

describe("module boundary enforcement", () => {
  it.each([
    ["shared/example.js", "@/features/workshops"],
    ["shared/example.js", "@/app/App"],
    ["features/tickets/example.js", "@/features/payments"],
    ["features/tickets/example.js", "@/app/providers/AppProviders"],
    ["app/example.js", "@/features/workshops/model/internal"],
    ["app/example.js", "../../../mobile/src/app/index"],
  ])("rejects invalid dependency from %s to %s", (owner, target) => {
    expect(
      messages(owner, target).some(
        (message) => message.ruleId === "unihub/boundaries",
      ),
    ).toBe(true);
  });
  it("checks lazy imports as well as static imports", () => {
    expect(
      messages("shared/example.js", "@/features/workshops", true),
    ).toHaveLength(1);
  });
  it.each([
    ["app/example.js", "@/features/workshops"],
    ["app/example.js", "@/features/workshops/data/mock/index"],
    ["features/workshops/example.js", "@/features/workshops/model/types"],
    ["features/workshops/example.js", "@/shared/lib/example"],
  ])("allows owned/public dependency from %s to %s", (owner, target) => {
    expect(messages(owner, target)).toHaveLength(0);
  });
});
