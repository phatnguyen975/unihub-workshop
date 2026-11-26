import { useEffect } from "react";
import { notify } from "@/shared/ui/notify";
import { Notifications } from "@/shared/ui/Notifications";
import { useAccess } from "@/features/identity";
import { useLocale } from "@/shared/ui/useLocale";

export function AccessFeedback() {
  const { state, controller } = useAccess();
  const { t } = useLocale();
  const message =
    state.kind === "anonymous" || state.kind === "authenticated"
      ? state.message
      : undefined;

  useEffect(() => {
    if (
      state.kind === "logout-unconfirmed" ||
      state.kind === "restore-unavailable"
    ) {
      const key =
        state.kind === "logout-unconfirmed" ? "logoutError" : "restoreError";
      const id = `access-${key}`;
      notify.error(t(key), { id });
      return () => notify.dismiss(id);
    }
    if (
      !message ||
      !["signedOut", "passwordUpdated", "localRemoved"].includes(message)
    )
      return;
    const show = message === "localRemoved" ? notify.warning : notify.success;
    show(t(message), { id: `access-${message}` });
    controller.consumeMessage(message);
  }, [controller, message, state.kind, t]);

  return <Notifications />;
}
