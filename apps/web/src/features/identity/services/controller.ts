import { AccessError, type AccessState, type IdentityService } from './contract';
export class AccessController {
    private state: AccessState = { kind: 'restoring' };
    private listeners = new Set<() => void>();
    private generation = 0;
    private restoration: Promise<void> | null = null;
    private service: IdentityService;
    private clearPrivate: () => void;
    constructor(service: IdentityService, clearPrivate: () => void) { this.service = service; this.clearPrivate = clearPrivate; }
    getSnapshot = () => this.state;
    subscribe = (callback: () => void) => { this.listeners.add(callback); return () => { this.listeners.delete(callback); }; };
    private set(state: AccessState) { this.state = state; this.listeners.forEach(listener => listener()); }
    cancel() { this.generation++; }
    clearLocal = () => { this.cancel(); this.service.clear(); this.clearPrivate(); this.set({ kind: 'anonymous', message: 'localRemoved' }); };
    async login(email: string, password: string) { const version = ++this.generation; const current = () => version === this.generation; const identity = await this.service.login(email, password, current).catch(error=>{if(current())throw error;return null;}); if (!identity || !current())
        return; this.clearPrivate(); this.set(identity.temporary ? { kind: 'temporary', email, credential: password } : { kind: 'authenticated', identity }); }
    async replace(email: string, password: string, next: string) { const version = ++this.generation; const current = () => version === this.generation; const identity = await this.service.replace(email, password, next, current).catch(error=>{if(current())throw error;return null;}); if (identity && current()) {
        this.clearPrivate();
        this.set({ kind: 'authenticated', identity, message: 'passwordUpdated' });
    } }
    forgetTemporary() { if (this.state.kind === 'temporary') {
        this.cancel();
        this.set({ kind: 'anonymous' });
    } }
    restore = (): Promise<void> => {
        if (this.restoration)
            return this.restoration;
        if (this.service.locked()) {
            this.clearPrivate();
            this.set({ kind: 'logout-unconfirmed' });
            return Promise.resolve();
        }
        const version = ++this.generation;
        const hadSession = this.service.current() !== null;
        this.clearPrivate();
        this.set({ kind: 'restoring' });
        this.restoration = this.service.restore(() => version === this.generation).then(identity => { if (version === this.generation)
            this.set(identity ? { kind: 'authenticated', identity } : { kind: 'anonymous', message: hadSession ? 'expired' : undefined }); }).catch(() => { if (version === this.generation)
            this.set({ kind: 'restore-unavailable' }); }).finally(() => { this.restoration = null; });
        return this.restoration;
    };
    logout = async () => { const version = ++this.generation; this.service.lock(); this.clearPrivate(); this.set({ kind: 'logging-out' }); try {
        await this.service.logout(() => version === this.generation);
        if (version === this.generation)
            this.set({ kind: 'anonymous', message: 'signedOut' });
    }
    catch {
        if (version === this.generation)
            this.set({ kind: 'logout-unconfirmed' });
    } };
    error(error: unknown) { return error instanceof AccessError ? error.kind : 'unavailable'; }
    consumeMessage(expected: string) {
        const state = this.state;
        if ((state.kind === 'anonymous' || state.kind === 'authenticated') && state.message === expected) this.set({ ...state, message: undefined });
    }
    storageFailed = () => this.service.storageFailed();
}
