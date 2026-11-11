const { execFileSync } = require("node:child_process");
const { appendFileSync } = require("node:fs");

const scopes = ["backend", "workers", "web"];

function selectScopes(paths) {
  const shared = paths.some(
    (path) =>
      [
        ".gitleaks.toml",
        ".gitattributes",
        ".github/workflows/ci-gate.yml",
      ].includes(path) || path.startsWith(".github/ci/"),
  );
  return Object.fromEntries(
    scopes.map((scope) => [
      scope,
      shared ||
        paths.some(
          (path) =>
            path.startsWith(`apps/${scope}/`) ||
            path === `.github/workflows/ci-${scope}.yml`,
        ),
    ]),
  );
}

function gatePasses(needs) {
  if (needs.changes?.result !== "success") return false;
  return scopes.every((scope) => {
    const selected = needs.changes.outputs[scope];
    if (!["true", "false"].includes(selected)) return false;
    return (
      needs[scope]?.result === (selected === "true" ? "success" : "skipped")
    );
  });
}

if (require.main === module) {
  if (process.argv[2] === "detect") {
    const { BASE_SHA, HEAD_SHA, GITHUB_OUTPUT } = process.env;
    if (
      ![BASE_SHA, HEAD_SHA].every((sha) => /^[a-f0-9]{40}$/.test(sha || ""))
    ) {
      throw new Error("Expected explicit PR base and head commit SHAs");
    }
    const mergeBase = execFileSync("git", ["merge-base", BASE_SHA, HEAD_SHA], {
      encoding: "utf8",
    }).trim();
    const paths = execFileSync(
      "git",
      ["diff", "--no-renames", "--name-only", "-z", mergeBase, HEAD_SHA],
      { encoding: "utf8", maxBuffer: 32 * 1024 * 1024 },
    )
      .split("\0")
      .filter(Boolean);
    const selected = selectScopes(paths);
    console.log(selected);
    for (const [scope, value] of Object.entries(selected)) {
      appendFileSync(GITHUB_OUTPUT, `${scope}=${value}\n`);
    }
  } else if (process.argv[2] === "gate") {
    const needs = JSON.parse(process.env.NEEDS_JSON);
    console.log(JSON.stringify(needs, null, 2));
    if (!gatePasses(needs)) process.exitCode = 1;
  } else {
    throw new Error("Expected detect or gate");
  }
}

module.exports = { selectScopes, gatePasses };
