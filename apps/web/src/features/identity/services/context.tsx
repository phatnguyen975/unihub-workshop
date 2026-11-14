import { useEffect, type PropsWithChildren } from 'react';
import { Context } from './useAccess';
import { AccessController } from './controller';
export function IdentityProvider({ controller, children }: PropsWithChildren<{
    controller: AccessController;
}>) { useEffect(() => { void controller.restore(); }, [controller]); return <Context value={controller}>{children}</Context>; }
