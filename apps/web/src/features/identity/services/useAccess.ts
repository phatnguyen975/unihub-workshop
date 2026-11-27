import { createContext, useContext, useSyncExternalStore } from "react";
import { AccessController } from "./controller";
export const Context = createContext<AccessController | null>(null);
export function useAccess() {
  const controller = useContext(Context);
  if (!controller) throw Error("Identity provider required");
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
  );
  return { controller, state };
}
