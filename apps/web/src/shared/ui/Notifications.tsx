import { Toaster, ToastBar } from 'react-hot-toast';
import { notificationStyles } from './notify';

export function Notifications() {
    return <Toaster position="top-right" containerStyle={{ top: 96, zIndex: 4 }} toastOptions={{
        duration: 4000,
        ariaProps: { role: 'status', 'aria-live': 'polite' },
        style: { background: 'var(--surface)', color: 'var(--ink)', border: '1px solid var(--line)', maxWidth: 'min(420px, calc(100vw - 32px))' },
        success: { style: notificationStyles.success, iconTheme: { primary: '#fff', secondary: '#166345' } },
        error: { style: notificationStyles.error, iconTheme: { primary: '#fff', secondary: '#b42335' } },
    }}>
        {item => <ToastBar toast={item} position="top-right" style={{
            // Keep text contrast stable instead of fading it into the page.
            animation: 'none', opacity: item.visible ? 1 : 0,
            visibility: item.visible ? 'visible' : 'hidden',
        }} />}
    </Toaster>;
}
