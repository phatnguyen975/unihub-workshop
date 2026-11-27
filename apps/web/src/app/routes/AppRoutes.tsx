import { WorkshopDiscovery, createWorkshopService } from "@/features/workshops";
import { CloseButton } from "@/shared/ui/CloseButton";
import { AuthLayout } from "@/shared/ui/AuthLayout";
import { useEffect, useRef } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { LockKeyhole } from "lucide-react";
import {
  AccessForm,
  useAccess,
  landing,
  requiredRole,
  safeDestination,
} from "@/features/identity";
import { useLocale } from "@/shared/ui/useLocale";
import { Shell } from "../layouts/Shell";
import artwork from "@/assets/poster-design.svg";
const workshopService = createWorkshopService();
if (import.meta.env.DEV) {
  const scenario = sessionStorage.getItem("unihub.workshops.scenario");
  if (scenario) workshopService.scenario(scenario);
  window.addEventListener("unihub:workshop-scenario", (event) => {
    workshopService.scenario((event as CustomEvent<string>).detail);
    window.dispatchEvent(new Event("unihub:workshop-scenario-updated"));
  });
}
function Status({
  title,
  text,
  children,
}: {
  title: string;
  text: string;
  children?: React.ReactNode;
}) {
  const { t } = useLocale();
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus();
  }, [title]);
  return (
    <section className="status-panel">
      <div className="status-symbol">
        <LockKeyhole />
      </div>
      <h1 id="page-title" ref={heading} tabIndex={-1}>
        {t(title)}
      </h1>
      <p>{t(text)}</p>
      <div className="actions">{children}</div>
    </section>
  );
}
function Content() {
  const navigate = useNavigate();
  const { state, controller } = useAccess();
  const { t } = useLocale();
  const location = useLocation();
  const path = location.pathname;
  const dialog = useRef<HTMLDialogElement>(null);
  const intent = safeDestination(
    new URLSearchParams(location.search).get("next") ?? "",
  );
  const suffix = intent ? "?next=" + encodeURIComponent(intent) : "";
  useEffect(() => {
    document.querySelector<HTMLElement>("#page-title")?.focus();
  }, [path, state.kind]);
  useEffect(() => {
    document.title =
      (path === "/login"
        ? t("signIn")
        : path === "/change-temporary-password"
          ? t("temporaryTitle")
          : t("workshops")) + " · UniHub";
  }, [path, t]);
  if (["restoring", "renewing", "logging-out"].includes(state.kind))
    return (
      <Status
        title={
          state.kind === "logging-out"
            ? "signingOut"
            : state.kind === "renewing"
              ? "renewing"
              : "restoring"
        }
        text={state.kind === "logging-out" ? "signingOutText" : "restoringText"}
      >
        <div
          className="session-progress"
          role="progressbar"
          aria-label={t(
            state.kind === "logging-out"
              ? "signingOut"
              : state.kind === "renewing"
                ? "renewing"
                : "restoring",
          )}
        >
          <span />
        </div>
      </Status>
    );
  if (
    state.kind === "restore-unavailable" ||
    state.kind === "logout-unconfirmed"
  )
    return (
      <>
        <Status
          title={
            state.kind === "restore-unavailable"
              ? "restoreError"
              : "logoutError"
          }
          text={
            state.kind === "restore-unavailable"
              ? "restoreErrorText"
              : "logoutErrorText"
          }
        >
          <button
            className="btn"
            onClick={() => {
              void (state.kind === "restore-unavailable"
                ? controller.restore()
                : controller.logout().then(() => {
                    if (controller.getSnapshot().kind === "anonymous")
                      navigate("/", { replace: true });
                  }));
            }}
          >
            {t("retry")}
          </button>
          <button
            className="btn secondary"
            onClick={() => dialog.current?.showModal()}
          >
            {t("removeLocal")}
          </button>
        </Status>
        <dialog
          ref={dialog}
          aria-labelledby="remove-title"
          aria-describedby="remove-description"
        >
          <CloseButton onClick={() => dialog.current?.close()} />
          <h2 id="remove-title">{t("removeTitle")}</h2>
          <p id="remove-description">{t("removeText")}</p>
          <div className="dialog-actions">
            <button
              autoFocus
              className="btn secondary"
              onClick={() => dialog.current?.close()}
            >
              {t("cancel")}
            </button>
            <button
              className="btn"
              onClick={() => {
                dialog.current?.close();
                controller.clearLocal();
                navigate("/", { replace: true });
              }}
            >
              {t("removeConfirm")}
            </button>
          </div>
        </dialog>
      </>
    );
  const identity = state.kind === "authenticated" ? state.identity : null;
  if (requiredRole(path) && !identity)
    return (
      <Navigate
        to={"/login?next=" + encodeURIComponent(path + location.search)}
        replace
      />
    );
  if (
    requiredRole(path) &&
    identity &&
    (requiredRole(path) !== identity.role || !identity.organization)
  ) {
    const missing = identity.role !== "STUDENT" && !identity.organization;
    return (
      <Status title="denied" text={missing ? "missingOrg" : "deniedText"}>
        <Link className="btn" to={missing ? "/" : landing(identity.role)}>
          {t(missing ? "backHome" : "goToWorkshops")}
        </Link>
      </Status>
    );
  }
  if (path === "/login" || path === "/change-temporary-password") {
    if (identity)
      return (
        <Navigate
          to={safeDestination(intent, identity.role) || landing(identity.role)}
          replace
        />
      );
    return <AccessForm key={path} temporary={path !== "/login"} />;
  }
  if (state.kind === "temporary")
    return <Navigate to={"/change-temporary-password" + suffix} replace />;
  const message =
    state.kind === "anonymous" || state.kind === "authenticated"
      ? state.message
      : undefined;
  const notice =
    message &&
    !["signedOut", "passwordUpdated", "localRemoved"].includes(message)
      ? message
      : undefined;
  if (path === "/")
    return (
      <section className="home">
        <div>
          <h1 id="page-title" tabIndex={-1}>
            {t("homeTitle")}
          </h1>
          <p>{t("homeText")}</p>
          {notice && (
            <div className="notice" role="status">
              {t(notice)}
            </div>
          )}
          <div className="actions">
            <Link className="btn" to="/workshops">
              {t("explore")}
            </Link>
            {!identity && (
              <Link className="btn secondary" to="/login">
                {t("signIn")}
              </Link>
            )}
          </div>
        </div>
        <img src={artwork} width="800" height="500" alt="" />
      </section>
    );
  if (path === "/workshops" || /^\/workshops\/[a-z0-9-]+$/.test(path))
    return (
      <WorkshopDiscovery
        userRole={identity?.role ?? null}
        service={workshopService}
      />
    );
  if (path === "/register" || path === "/forgot-password")
    return (
      <AuthLayout>
        <h1 id="page-title" tabIndex={-1}>
          {t(path === "/register" ? "registerTitle" : "recoveryTitle")}
        </h1>
        {notice && (
          <p className="notice" role="status">
            {t(notice)}
          </p>
        )}
        {controller.storageFailed() && (
          <p className="notice" role="status">
            {t("storage")}
          </p>
        )}
        <Link className="back-link form-back-link mt-6!" to={"/login" + suffix}>
          {t("backLogin")}
        </Link>
      </AuthLayout>
    );
  if (path === "/organizer/workshops" || path === "/staff/workshops") {
    const title = path === "/staff/workshops" ? "staffWorkshops" : "workshops";
    return (
      <section className="boundary">
        {identity && (
          <p className="eyebrow">
            {identity.name} ·{" "}
            {t(
              "role" +
                (identity.role === "STUDENT"
                  ? "Student"
                  : identity.role === "STAFF"
                    ? "Staff"
                    : "Organizer"),
            )}
          </p>
        )}
        <h1 id="page-title" tabIndex={-1}>
          {t(title)}
        </h1>
        {notice && (
          <div className="notice" role="status">
            {t(notice)}
          </div>
        )}
        {controller.storageFailed() && (
          <div className="notice" role="status">
            {t("storage")}
          </div>
        )}
        <Link className="back-link" to="/">
          {t("backHome")}
        </Link>
      </section>
    );
  }
  return (
    <Status title="notFound" text="notFoundText">
      <Link className="btn" to="/">
        {t("backHome")}
      </Link>
    </Status>
  );
}
export function AppRoutes() {
  return (
    <Shell>
      <Content />
    </Shell>
  );
}
