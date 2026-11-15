import { z } from 'zod';
export type Role = 'STUDENT' | 'ORGANIZER' | 'STAFF';
export interface Identity {
    id: string;
    email: string;
    name: string;
    role: Role;
    organization: string | null;
    temporary: boolean;
}
export const loginSchema = z.object({ email: z.string().trim().email('emailInvalid').max(254, 'emailInvalid'), password: z.string().max(255, 'passwordInvalid').refine(v => v.trim().length > 0, 'passwordInvalid') });
export const replacementSchema = z.object({ email: loginSchema.shape.email, currentPassword: z.string().min(1, 'required'), newPassword: z.string().min(8, 'newInvalid').max(255, 'newInvalid').regex(/[A-Z]/, 'newInvalid').regex(/[a-z]/, 'newInvalid').regex(/[0-9]/, 'newInvalid').regex(/[!@#$%^&*(),.?":{}|<>]/, 'newInvalid'), confirmation: z.string() }).superRefine((v, c) => { if (v.newPassword === v.currentPassword)
    c.addIssue({ code: 'custom', path: ['newPassword'], message: 'samePassword' }); if (v.newPassword !== v.confirmation)
    c.addIssue({ code: 'custom', path: ['confirmation'], message: 'mismatch' }); });
export const landing = (role?: Role) => role === 'ORGANIZER' ? '/organizer/workshops' : role === 'STAFF' ? '/staff/workshops' : '/workshops';
export const requiredRole = (path: string): Role | null => path === '/organizer/workshops' ? 'ORGANIZER' : path === '/staff/workshops' ? 'STAFF' : null;
export function safeDestination(value: string, role?: Role): string { try {
    if (!value.startsWith('/') || value.startsWith('//') || ([...value].some(c => c.charCodeAt(0) <= 32) || value.includes('\\')))
        return '';
    const decoded=decodeURIComponent(value);
    if([...decoded].some(c=>c.charCodeAt(0)<32))return '';
    const u = new URL(value, 'http://local.invalid');
    if (u.origin !== 'http://local.invalid' || !['/workshops', '/organizer/workshops', '/staff/workshops'].includes(u.pathname) || u.hash || [...u.searchParams.keys()].some(k => k !== 'q'))
        return '';
    const required = requiredRole(u.pathname);
    return role && required && role !== required ? '' : u.pathname + u.search;
}
catch {
    return '';
} }
