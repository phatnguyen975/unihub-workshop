// Design-only fake. Nothing in this file is a real authentication mechanism.
export function createFake(fixtures) {
  const key = "unihub.web-access.prototype.r1";
  const blank = () => ({
    version: 1,
    sample: "student",
    scenario: "normal",
    now: fixtures.clockOrigin,
    session: null,
    logoutLock: false,
    activated: {},
    passwordOutcomeLost: false,
  });
  let stored;
  let storageFailed = false;
  try {
    stored = JSON.parse(sessionStorage.getItem(key));
  } catch {
    storageFailed = true;
  }
  const valid =
    stored?.version === 1 &&
    fixtures.accounts.some((a) => a.id === stored.sample) &&
    fixtures.scenarios.some((s) => s.id === stored.scenario) &&
    Number.isFinite(stored.now) &&
    typeof stored.activated === "object" &&
    stored.activated !== null;
  let db = valid ? stored : blank();
  if (
    db.session &&
    (!fixtures.accounts.some((a) => a.id === db.session.accountId) ||
      !Number.isFinite(db.session.accessUntil) ||
      !Number.isFinite(db.session.renewUntil))
  )
    db.session = null;
  const account = (id) => fixtures.accounts.find((a) => a.id === id);
  const save = () => {
    try {
      if (db.scenario === "storage")
        throw new Error("Simulated tab storage failure");
      sessionStorage.setItem(key, JSON.stringify(db));
      storageFailed = false;
    } catch {
      storageFailed = true;
      // Remove a previous synthetic session where removal is still possible.
      try {
        sessionStorage.removeItem(key);
      } catch {
        /* Actual storage denial. */
      }
    }
  };
  const digest = async (value) => {
    // Non-security verifier of synthetic test input only. Never use real credentials.
    const bytes = await crypto.subtle.digest(
      "SHA-256",
      new TextEncoder().encode(value),
    );
    return Array.from(new Uint8Array(bytes), (b) =>
      b.toString(16).padStart(2, "0"),
    ).join("");
  };
  const wait = async () => {
    const scenario = db.scenario;
    await new Promise((resolve) =>
      setTimeout(resolve, scenario === "slow" ? 2500 : fixtures.latencyMs),
    );
    if (
      !navigator.onLine ||
      scenario === "offline" ||
      scenario === "unavailable"
    )
      throw { kind: "unavailable" };
    if (scenario === "rate") throw { kind: "rate" };
    return scenario;
  };
  const issue = (a) => ({
    accountId: a.id,
    accessUntil: db.now + fixtures.accessSeconds,
    renewUntil: db.now + fixtures.renewalSeconds,
  });
  const identity = () => (db.session ? account(db.session.accountId) : null);
  return {
    get data() {
      return db;
    },
    get storageFailed() {
      return storageFailed;
    },
    identity,
    save,
    setScenario(id) {
      db.scenario = id;
      save();
    },
    reset(sample = db.sample) {
      db = { ...blank(), sample };
      save();
    },
    advance() {
      db.now += 61;
      save();
    },
    revoke() {
      if (db.session) {
        db.session.renewUntil = db.now - 1;
        db.session.accessUntil = db.now - 1;
      }
      save();
    },
    lockLogout() {
      db.logoutLock = true;
      save();
    },
    clear() {
      db.session = null;
      db.logoutLock = false;
      save();
    },
    async login(email, password, current) {
      const scenario = await wait();
      if (!current()) return null;
      const a = fixtures.accounts.find((a) => a.email === email.trim());
      const matches =
        a &&
        (db.activated[a.id]
          ? (await digest(password)) === db.activated[a.id]
          : password === fixtures.demoPassword);
      if (!current()) return null;
      if (!matches || scenario === "invalid") throw { kind: "invalid" };
      if (scenario === "disabled") throw { kind: "disabled" };
      if (a.temporary && !db.activated[a.id])
        return { kind: "temporary", account: a };
      db.session = issue(a);
      db.logoutLock = false;
      save();
      return { kind: "authenticated", account: a };
    },
    async replace(email, temporary, next, current) {
      const scenario = await wait();
      if (!current()) return null;
      const a = fixtures.accounts.find((a) => a.email === email.trim());
      if (!a || temporary !== fixtures.demoPassword) throw { kind: "invalid" };
      if (!a.temporary || db.activated[a.id]) throw { kind: "notTemporary" };
      const verifier = await digest(next);
      if (!current()) return null;
      db.activated[a.id] = verifier;
      if (scenario === "lost-password") {
        db.passwordOutcomeLost = true;
        save();
        throw { kind: "unknownPassword" };
      }
      db.session = issue(a);
      save();
      return { kind: "authenticated", account: a };
    },
    async restore(current) {
      if (!db.session) return { kind: "anonymous" };
      await wait();
      if (!current()) return null;
      if (db.session.renewUntil <= db.now) {
        this.clear();
        return { kind: "expired" };
      }
      if (db.session.accessUntil <= db.now) {
        db.session.accessUntil = db.now + fixtures.accessSeconds;
        save();
      }
      return { kind: "authenticated", account: identity() };
    },
    async logout(current) {
      const scenario = await wait();
      if (!current()) return null;
      if (scenario === "logout-failure") throw { kind: "unavailable" };
      if (scenario === "logout-lost" && db.session) {
        db.session = null;
        save();
        throw { kind: "unavailable" };
      }
      this.clear();
      return { kind: "signed-out" };
    },
  };
}
