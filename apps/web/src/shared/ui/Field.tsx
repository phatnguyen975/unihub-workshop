import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import type { UseFormRegisterReturn } from "react-hook-form";
import { useLocale } from "./useLocale";
export function Field({
  name,
  label,
  password = false,
  error,
  registration,
  help,
}: {
  name: string;
  label: string;
  password?: boolean;
  error?: string;
  registration: UseFormRegisterReturn;
  help?: string;
}) {
  const { t } = useLocale();
  const [visible, setVisible] = useState(false);
  return (
    <div className="field">
      <label htmlFor={name}>{t(label)}</label>
      <div className={password ? "password-wrap" : ""}>
        <input
          id={name}
          type={password ? (visible ? "text" : "password") : "email"}
          autoComplete={
            name === "email"
              ? "username"
              : name === "password" || name === "currentPassword"
                ? "current-password"
                : "new-password"
          }
          maxLength={name === "email" ? 254 : 255}
          aria-invalid={!!error}
          aria-describedby={`${name}-error${help ? ` ${name}-help` : ""}`}
          {...registration}
        />
        {password && (
          <button
            type="button"
            className="password-toggle"
            aria-label={t(visible ? "hidePassword" : "showPassword")}
            aria-pressed={visible}
            onClick={() => setVisible(!visible)}
          >
            {visible ? (
              <EyeOff aria-hidden="true" />
            ) : (
              <Eye aria-hidden="true" />
            )}
          </button>
        )}
      </div>
      {help && (
        <p className="requirements" id={`${name}-help`}>
          {t(help)}
        </p>
      )}
      <span className="field-error" id={`${name}-error`}>
        {error ? t(error) : ""}
      </span>
    </div>
  );
}
