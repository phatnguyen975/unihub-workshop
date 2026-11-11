import { useState, type PropsWithChildren } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createQueryClient } from "./query-client";

export function AppProviders({ children }: PropsWithChildren) {
  const [client] = useState(createQueryClient);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
