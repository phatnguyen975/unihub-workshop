import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { loginSchema, replacementSchema, landing, safeDestination } from '../model/access';
import { useAccess } from '../services/useAccess';
import { useLocale } from '../../../shared/ui/useLocale';
import { Field } from '../../../shared/ui/Field';
import artwork from '../../../assets/poster-design.svg';
export function AccessForm({ temporary = false }: {
    temporary?: boolean;
}) {
    const { t } = useLocale();
    const { controller, state } = useAccess();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const intent = safeDestination(params.get('next') ?? '');
    const suffix = intent ? '?next=' + encodeURIComponent(intent) : '';
    const [error, setError] = useState('');
    const [retrySeconds, setRetrySeconds] = useState(0);
    const heading = useRef<HTMLHeadingElement>(null);
    const login = useForm({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } });
    const replacement = useForm({ resolver: zodResolver(replacementSchema), defaultValues: { email: state.kind === 'temporary' ? state.email : '', currentPassword: state.kind === 'temporary' ? state.credential : '', newPassword: '', confirmation: '' } });
    const busy = login.formState.isSubmitting || replacement.formState.isSubmitting;
    useEffect(() => { heading.current?.focus(); return () => { controller.cancel(); }; }, [controller, temporary]);
    useEffect(() => { if (state.kind === 'authenticated')
        navigate(safeDestination(intent, state.identity.role) || landing(state.identity.role), { replace: true });
    else if (state.kind === 'temporary' && !temporary)
        navigate('/change-temporary-password' + suffix, { replace: true }); }, [state, intent, navigate, temporary, suffix]);
    useEffect(() => { if(retrySeconds===0)return; const timer=setTimeout(()=>setRetrySeconds(value=>Math.max(0,value-1)),1000); return ()=>clearTimeout(timer); },[retrySeconds]);
    const handleError = (e: unknown) => { const key = controller.error(e); setError(key); login.resetField('password'); replacement.setValue('currentPassword', ''); controller.forgetTemporary(); replacement.resetField('newPassword'); replacement.resetField('confirmation'); if (key === 'rate')
        setRetrySeconds(3); };
    return <div className="auth-layout">
    <aside className="welcome">
    <div className="art">
    <img src={artwork} width="800" height="500" alt=""/>
    </div>
    <h2>{t('welcomeTitle')}</h2>
    <p>{t('welcomeText')}</p>
    </aside>
    <section className="auth-card" aria-labelledby="page-title">
    <h1 id="page-title" tabIndex={-1} ref={heading}>{t(temporary ? 'temporaryTitle' : 'welcome')}</h1>
    <p className="subtitle">{t(temporary ? 'temporaryText' : 'loginText')}</p>{state.kind === 'anonymous' && state.message && <div className="notice" role="status">{t(state.message)}</div>}{error && <div className="notice error" role="alert">{t(error)}{error === 'unknownPassword' && <>
            <p>{t('unknownPasswordText')}</p>
            <Link to={'/login' + suffix}>{t('signInNew')}</Link>
            </>}{error === 'rate' && <p>{retrySeconds > 0 ? t('wait').replace('{seconds}', String(retrySeconds)) : t('retry')}</p>}</div>}{controller.storageFailed() && <div className="notice" role="status">{t('storage')}</div>}

 {temporary ? <form noValidate aria-busy={busy} onSubmit={replacement.handleSubmit(async (v) => { setError(''); try {
            await controller.replace(v.email, v.currentPassword, v.newPassword);
        }
        catch (e) {
            handleError(e);
        } })}>
        <Field name="email" label="email" registration={replacement.register('email')} error={replacement.formState.errors.email?.message}/>{state.kind !== 'temporary' && <Field name="currentPassword" label="currentPassword" password registration={replacement.register('currentPassword')} error={replacement.formState.errors.currentPassword?.message}/>}<Field name="newPassword" label="newPassword" password registration={replacement.register('newPassword')} error={replacement.formState.errors.newPassword?.message} help="passwordHelp"/>
        <Field name="confirmation" label="confirmPassword" password registration={replacement.register('confirmation')} error={replacement.formState.errors.confirmation?.message}/>
        <div className="form-links"/>
        <button className="btn wide" disabled={busy || retrySeconds > 0}>{t(busy ? 'saving' : 'updatePassword')}</button>
        </form> : <form noValidate aria-busy={busy} onSubmit={login.handleSubmit(async (v) => { setError(''); try {
            await controller.login(v.email, v.password);
        }
        catch (e) {
            handleError(e);
        } })}>
        <Field name="email" label="email" registration={login.register('email')} error={login.formState.errors.email?.message}/>
        <Field name="password" label="password" password registration={login.register('password')} error={login.formState.errors.password?.message}/>
        <div className="form-links">
        <Link to={'/forgot-password' + suffix}>{t('forgot')}</Link>
        </div>
        <button className="btn wide" disabled={busy || retrySeconds > 0}>{t(busy ? 'working' : 'signIn')}</button>
        </form>}

 {temporary ? <Link className="back-link" onClick={() => controller.forgetTemporary()} to={'/login' + suffix}>{t('backLogin')}</Link> : <p className="join">{t('noAccount')} <Link to={'/register' + suffix}>{t('createAccount')}</Link>
        </p>}</section>
    </div>;
}
