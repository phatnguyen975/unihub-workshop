import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router';
import { Moon, Sun, X } from 'lucide-react';
import { useAccess } from '@/features/identity';
import { useLocale } from '@/shared/ui/useLocale';
import logo from '@/assets/unihub-logo.svg';
import darkLogo from '@/assets/unihub-logo-dark.svg';
import type { PropsWithChildren } from 'react';
export function Shell({ children }: PropsWithChildren) {
    const navigate = useNavigate();
    const { t, locale, setLocale } = useLocale();
    const { state, controller } = useAccess();
    const identity = state.kind === 'authenticated' ? state.identity : null;
    const [dark, setDark] = useState(false);
    const [language, setLanguage] = useState(false);
    const [account, setAccount] = useState(false);
    const [help, setHelp] = useState('');
    const [menu, setMenu] = useState(false);
    const dialog = useRef<HTMLDialogElement>(null);
    const accountRoot = useRef<HTMLDivElement>(null);
    const languageRoot = useRef<HTMLDivElement>(null);
    useEffect(() => { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; }, [dark]);
    useEffect(() => { const dismiss = (e: PointerEvent) => { if (!accountRoot.current?.contains(e.target as Node))
        setAccount(false); if (!languageRoot.current?.contains(e.target as Node))
        setLanguage(false); }; document.addEventListener('pointerdown', dismiss); return () => document.removeEventListener('pointerdown', dismiss); }, []);
    const locked = !['anonymous', 'authenticated', 'temporary'].includes(state.kind);
    return <>
    <a className="skip" href="#main" onClick={e => { e.preventDefault(); document.getElementById('main')?.focus(); }}>{t('skip')}</a>
    <header className="header" onClickCapture={e => { if ((e.target as Element).closest('a'))
        controller.forgetTemporary(); }}>
    <div className="header-inner">
    <Link className="brand" to="/" aria-label="UniHub home">
    <img src={dark ? darkLogo : logo} width="156" height="41" alt="UniHub"/>
    </Link>
    <nav className="product-nav" aria-label={t('home')}>{identity?.role === 'ORGANIZER' ? null : <NavLink to={identity?.role === 'STAFF' ? '/staff/workshops' : '/workshops'}>{t(identity?.role === 'STAFF' ? 'staffWorkshops' : 'discover')}</NavLink>}</nav>
    <div className="header-actions">
    <button className="icon-button" aria-label={t('theme')} aria-pressed={dark} onClick={() => setDark(!dark)}>{dark ? <Sun /> : <Moon />}</button>
    <div className="locale-control" ref={languageRoot} onKeyDown={e => { if (e.key === 'Escape') {
        setLanguage(false);
        languageRoot.current?.querySelector('button')?.focus();
    } }}>
    <button className="icon-button" aria-label={t('language')} aria-expanded={language} onClick={() => setLanguage(!language)}>{locale === 'en' ? 'EN' : 'VN'}</button>{language && <div className="popup">{(['en', 'vi'] as const).map(value => <button key={value} aria-pressed={value === locale} onClick={() => { setLocale(value); setLanguage(false); languageRoot.current?.querySelector('button')?.focus(); }}>{t(value)}</button>)}</div>}</div>{identity ? <div className="account-control" ref={accountRoot} onKeyDown={e => { if (e.key === 'Escape') {
            setAccount(false);
            accountRoot.current?.querySelector('button')?.focus();
        } }}>
        <button className="avatar" aria-label={t('account')} aria-expanded={account} onClick={() => setAccount(!account)}>{identity.name.split(' ').map(x => x[0]).slice(0, 2).join('')}</button>{account && <div className="popup">
            <div className="identity">{identity.name}<span>{identity.email}</span>
            </div>
            <button onClick={() => { setAccount(false); void controller.logout().then(() => { if (controller.getSnapshot().kind === 'anonymous')
                navigate('/', { replace: true }); }); }}>{t('signOut')}</button>
            </div>}</div> : !locked && <Link className="btn" to="/login">{t('signIn')}</Link>}</div>
    </div>
    </header>
    <main id="main" tabIndex={-1}>{identity?.role === 'ORGANIZER' ? <div className="organizer-layout">
        <aside className="sidebar">
        <p className="organization">{identity.organization}</p>
        <button className="btn secondary mobile-menu" aria-expanded={menu} onClick={() => setMenu(!menu)}>{t('menu')}</button>
        <nav className={menu ? 'expanded' : ''} aria-label={t('workshops')}>
        <NavLink to="/organizer/workshops">{t('workshops')}</NavLink>
        </nav>
        </aside>
        <div>{children}</div>
        </div> : children}</main>
    <footer>
    <div className="footer-inner">
    <p>{t('footer')}</p>
    <nav aria-label={t('gettingStarted')}>{['about', 'gettingStarted', 'accessibility'].map(key => <button key={key} onClick={() => { setHelp(key); dialog.current?.showModal(); }}>{t(key)}</button>)}</nav>
    <span>© UniHub</span>
    </div>
    </footer>
    <dialog ref={dialog} aria-labelledby="help-title">
    <button className="dialog-close" aria-label={t('close')} onClick={() => dialog.current?.close()}>
    <X size={20}/>
    </button>
    <h2 id="help-title">{t(help)}</h2>
    <p>{t(help + 'Text')}</p>
    </dialog>
    </>;
}
