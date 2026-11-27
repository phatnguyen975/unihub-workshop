import { X } from "lucide-react";
import { useLocale } from "./useLocale";

export function CloseButton({ onClick }: { onClick: () => void }) {
  const { t } = useLocale();
  return (
    <button
      type="button"
      className="dialog-close"
      aria-label={t("close")}
      onClick={onClick}
    >
      <X size={20} aria-hidden="true" />
    </button>
  );
}
