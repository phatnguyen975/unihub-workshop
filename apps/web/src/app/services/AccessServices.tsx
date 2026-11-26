import { AccessFeedback } from "../providers/AccessFeedback";
import { useState, lazy, Suspense } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { AccessController, IdentityProvider } from "@/features/identity";
import { createMockIdentity } from "@/features/identity/data/mock/index";
import type { PropsWithChildren } from "react";
const ReviewTools = import.meta.env.DEV
  ? lazy(() => import("../review/ReviewTools"))
  : null;
export function AccessServices({ children }: PropsWithChildren) {
  const query = useQueryClient();
  const [service] = useState(() => createMockIdentity());
  const [controller] = useState(
    () =>
      new AccessController(service, () => {
        void query.cancelQueries();
        query.removeQueries();
      }),
  );
  return (
    <IdentityProvider controller={controller}>
      <AccessFeedback />
      {children}
      {ReviewTools && (
        <Suspense fallback={null}>
          <ReviewTools service={service} controller={controller} />
        </Suspense>
      )}
    </IdentityProvider>
  );
}
