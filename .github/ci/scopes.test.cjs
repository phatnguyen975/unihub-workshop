const { test } = require("node:test");
const assert = require("node:assert/strict");
const { selectScopes, gatePasses } = require("./scopes.cjs");

test("documentation and mobile changes do not select unrelated applications", () => {
  assert.deepEqual(
    selectScopes([
      "README.md",
      "docs/client/web/tooling.md",
      "apps/mobile/package.json",
    ]),
    { backend: false, workers: false, web: false },
  );
});

test("each app and its workflow select only their own scope", () => {
  for (const scope of ["backend", "workers", "web"]) {
    for (const path of [
      `apps/${scope}/package.json`,
      `.github/workflows/ci-${scope}.yml`,
    ]) {
      assert.deepEqual(
        selectScopes([path]),
        Object.fromEntries(
          ["backend", "workers", "web"].map((name) => [name, name === scope]),
        ),
      );
    }
  }
  assert.deepEqual(selectScopes(["apps/backend/a", "apps/web/b"]), {
    backend: true,
    workers: false,
    web: true,
  });
});

test("shared CI policy changes select all applications", () => {
  for (const path of [
    ".gitleaks.toml",
    ".gitattributes",
    ".github/workflows/ci-gate.yml",
    ".github/ci/scopes.cjs",
  ]) {
    assert.deepEqual(selectScopes([path]), {
      backend: true,
      workers: true,
      web: true,
    });
  }
});

function results(selected) {
  return {
    changes: {
      result: "success",
      outputs: Object.fromEntries(
        Object.entries(selected).map(([key, value]) => [key, String(value)]),
      ),
    },
    ...Object.fromEntries(
      Object.entries(selected).map(([key, value]) => [
        key,
        { result: value ? "success" : "skipped" },
      ]),
    ),
  };
}

test("gate accepts every valid scope combination", () => {
  for (let mask = 0; mask < 8; mask++) {
    assert.equal(
      gatePasses(
        results({
          backend: !!(mask & 1),
          workers: !!(mask & 2),
          web: !!(mask & 4),
        }),
      ),
      true,
    );
  }
});

test("gate rejects failed, cancelled, missing or unexpectedly skipped checks", () => {
  for (const result of [
    "failure",
    "cancelled",
    "skipped",
    "timed_out",
    undefined,
  ]) {
    const needs = results({ backend: false, workers: false, web: true });
    needs.web.result = result;
    assert.equal(gatePasses(needs), false);
  }
  for (const result of ["failure", "cancelled", "skipped"]) {
    const needs = results({ backend: false, workers: false, web: false });
    needs.changes.result = result;
    assert.equal(gatePasses(needs), false);
  }
  const needs = results({ backend: false, workers: false, web: false });
  delete needs.changes.outputs.web;
  assert.equal(gatePasses(needs), false);
});
