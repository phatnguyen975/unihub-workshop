import path from "node:path";
import { fileURLToPath } from "node:url";

const appRoot = fileURLToPath(new URL("../", import.meta.url));
const srcRoot = path.join(appRoot, "src");
const slash = (value) => value.split(path.sep).join("/");

export const boundaries = {
  meta: { type: "problem", schema: [], messages: { boundary: "{{reason}}" } },
  create(context) {
    const filename = context.filename;
    const owner = slash(path.relative(srcRoot, filename));
    function check(node, value) {
      if (typeof value !== "string") return;
      const resolved = value.startsWith("@/")
        ? path.join(srcRoot, value.slice(2))
        : value.startsWith(".")
          ? path.resolve(path.dirname(filename), value)
          : null;
      if (!resolved) return;
      const target = slash(path.relative(srcRoot, resolved));
      let reason;
      if (target.startsWith("../"))
        reason =
          "Source modules must not import another app or repository internals.";
      else if (owner.startsWith("shared/") && /^(app|features)\//.test(target))
        reason = "Shared modules cannot import app or feature code.";
      else if (owner.startsWith("features/") && target.startsWith("app/"))
        reason = "Features cannot import app composition.";
      else if (
        owner.startsWith("features/") &&
        target.startsWith("features/") &&
        owner.split("/")[1] !== target.split("/")[1]
      )
        reason =
          "Compose cross-feature work in app through explicit public interfaces.";
      else if (owner.startsWith("app/") && target.startsWith("features/")) {
        const parts = target.split("/");
        const publicSurface =
          parts.length === 2 ||
          (parts.length === 3 && /^index(?:\.[cm]?[jt]sx?)?$/.test(parts[2]));
        const adapterFactory =
          /^features\/[^/]+\/data\/(mock|http)\/index(?:\.ts)?$/.test(target);
        if (!publicSurface && !adapterFactory)
          reason =
            "App imports feature public exports or explicit adapter factory entrypoints only.";
      }
      if (reason)
        context.report({ node, messageId: "boundary", data: { reason } });
    }
    return {
      ImportDeclaration(node) {
        check(node.source, node.source.value);
      },
      ExportNamedDeclaration(node) {
        if (node.source) check(node.source, node.source.value);
      },
      ExportAllDeclaration(node) {
        check(node.source, node.source.value);
      },
      ImportExpression(node) {
        if (node.source.type === "Literal")
          check(node.source, node.source.value);
      },
    };
  },
};

export default { rules: { boundaries } };
