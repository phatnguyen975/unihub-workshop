import toast, { type ToastOptions } from "react-hot-toast";

export const notificationStyles = {
  success: {
    background: "#166345",
    color: "#fff",
    border: "1px solid #145339",
  },
  error: { background: "#b42335", color: "#fff", border: "1px solid #951d2c" },
  warning: {
    background: "#fef3c7",
    color: "#78350f",
    border: "1px solid #d97706",
  },
};

/** Shared notification entry points; persistent recovery belongs in the page. */
export const notify = {
  dismiss: toast.dismiss,
  success: (message: string, options?: ToastOptions) =>
    toast.success(message, options),
  error: (message: string, options?: ToastOptions) =>
    toast.error(message, options),
  warning: (message: string, options?: ToastOptions) =>
    toast(message, {
      ...options,
      icon: "!",
      style: { ...notificationStyles.warning, ...options?.style },
    }),
  info: (message: string, options?: ToastOptions) => toast(message, options),
};
