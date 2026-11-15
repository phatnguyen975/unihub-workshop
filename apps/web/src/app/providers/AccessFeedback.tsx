import { useEffect } from 'react';
import toast, { Toaster } from 'react-hot-toast';
import { useAccess } from '@/features/identity';
import { useLocale } from '@/shared/ui/useLocale';

export function AccessFeedback() {
    const { state, controller } = useAccess();
    const { t } = useLocale();
    const message = state.kind === 'anonymous' || state.kind === 'authenticated' ? state.message : undefined;

    useEffect(() => {
        if (!message || !['signedOut', 'passwordUpdated', 'localRemoved'].includes(message)) return;
        toast.success(t(message), { id: `access-${message}` });
        controller.consumeMessage(message);
    }, [controller, message, t]);

    return <Toaster position="top-center" toastOptions={{
        duration: 4000,
        ariaProps: { role: 'status', 'aria-live': 'polite' },
        style: { background: 'var(--surface)', color: 'var(--ink)', border: '1px solid var(--line)', maxWidth: 'min(420px, calc(100vw - 32px))' },
    }} />;
}
