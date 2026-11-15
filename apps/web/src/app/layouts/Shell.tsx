import { useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router';
import { CalendarDays, Compass, LayoutDashboard, LogOut, Menu, Moon, Sun } from 'lucide-react';
import { useAccess } from '@/features/identity';
import { useLocale } from '@/shared/ui/useLocale';
import { CloseButton } from '@/shared/ui/CloseButton';
import logo from '@/assets/unihub-logo.svg';
import darkLogo from '@/assets/unihub-logo-dark.svg';

export function Shell({ children }: PropsWithChildren) {
    const navigate = useNavigate();
    const { pathname } = useLocation();
    const { t, locale, setLocale } = useLocale();
    const { state, controller } = useAccess();
    const identity = state.kind === 'authenticated' ? state.identity : null;
    const workspace = identity?.role === 'ORGANIZER' && !!identity.organization && pathname === '/organizer/workshops';
    const locked = !['anonymous', 'authenticated', 'temporary'].includes(state.kind);
    const [dark, setDark] = useState(false);
    const [language, setLanguage] = useState(false);
    const [account, setAccount] = useState(false);
    const [help, setHelp] = useState('');
    const [menuOpen, setMenuOpen] = useState(false);
    const helpDialog = useRef<HTMLDialogElement>(null);
    const navigationDialog = useRef<HTMLDialogElement>(null);
    const accountRoot = useRef<HTMLDivElement>(null);
    const languageRoot = useRef<HTMLDivElement>(null);

    useEffect(() => { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; }, [dark]);
    useEffect(() => {
        const dismiss = (event: PointerEvent) => {
            if (!accountRoot.current?.contains(event.target as Node)) setAccount(false);
            if (!languageRoot.current?.contains(event.target as Node)) setLanguage(false);
        };
        document.addEventListener('pointerdown', dismiss);
        return () => document.removeEventListener('pointerdown', dismiss);
    }, []);

    const closeNavigation = () => navigationDialog.current?.close();
    const workshopNavigation = <nav className="workspace-navigation" aria-label={t('management')}>
        <NavLink to="/organizer/workshops" onClick={closeNavigation}>
            <CalendarDays size={18} aria-hidden="true" />{t('workshops')}
        </NavLink>
    </nav>;
    const signOut = async () => {
        setAccount(false);
        await controller.logout();
        if (controller.getSnapshot().kind === 'anonymous') navigate('/', { replace: true });
    };

    return <>
        <a className="skip" href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus(); }}>{t('skip')}</a>
        <header className="header" onClickCapture={event => { if ((event.target as Element).closest('a')) controller.forgetTemporary(); }}>
            <div className={`header-inner${workspace ? ' has-workspace-menu' : ''}`}>
                {workspace && <button type="button" className="icon-button workspace-toggle" aria-label={t('openNavigation')} aria-haspopup="dialog" aria-expanded={menuOpen} aria-controls="workspace-menu" onClick={() => { navigationDialog.current?.showModal(); setMenuOpen(true); }}><Menu size={20} aria-hidden="true" /></button>}
                <Link className="brand" to="/" aria-label="UniHub home"><img src={dark ? darkLogo : logo} width="156" height="41" alt="UniHub" /></Link>
                <nav className="product-nav" aria-label={t('home')}>
                    <NavLink to="/workshops">{t('discover')}</NavLink>
                    {identity?.role === 'STAFF' && <NavLink to="/staff/workshops">{t('staffWorkshops')}</NavLink>}
                </nav>
                <div className="header-actions">
                    <button className="icon-button" aria-label={t('theme')} aria-pressed={dark} onClick={() => setDark(!dark)}>{dark ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}</button>
                    <div className="locale-control" ref={languageRoot} onKeyDown={event => { if (event.key === 'Escape') { setLanguage(false); languageRoot.current?.querySelector('button')?.focus(); } }}>
                        <button className="icon-button locale-trigger" aria-label={t('language')} aria-expanded={language} aria-controls="locale-options" onClick={() => setLanguage(!language)}>{locale === 'en' ? 'EN' : 'VN'}</button>
                        {language && <div className="popup locale-options" id="locale-options">{(['en', 'vi'] as const).map(value => <button key={value} aria-pressed={value === locale} onClick={() => { setLocale(value); setLanguage(false); languageRoot.current?.querySelector('button')?.focus(); }}>{t(value)}</button>)}</div>}
                    </div>
                    {identity ? <div className="account-control" ref={accountRoot} onKeyDown={event => { if (event.key === 'Escape') { setAccount(false); accountRoot.current?.querySelector('button')?.focus(); } }}>
                        <button className="avatar" aria-label={t('account')} aria-expanded={account} aria-controls="account-options" onClick={() => setAccount(!account)}>{identity.name.split(' ').map(part => part[0]).slice(0, 2).join('')}</button>
                        {account && <div className="popup account-options" id="account-options">
                            <div className="identity"><strong className="account-name" title={identity.name}>{identity.name}</strong><span className="account-email" title={identity.email}>{identity.email}</span></div>
                            {identity.role === 'ORGANIZER' && identity.organization && <Link to="/organizer/workshops" onClick={() => setAccount(false)}><LayoutDashboard size={18} aria-hidden="true" />{t('management')}</Link>}
                            {identity.role === 'STAFF' && <Link to="/staff/workshops" onClick={() => setAccount(false)}><CalendarDays size={18} aria-hidden="true" />{t('staffWorkshops')}</Link>}
                            <Link to="/workshops" onClick={() => setAccount(false)}><Compass size={18} aria-hidden="true" />{t('discover')}</Link>
                            <button onClick={() => void signOut()}><LogOut size={18} aria-hidden="true" />{t('signOut')}</button>
                        </div>}
                    </div> : !locked && <Link className="btn" to="/login">{t('signIn')}</Link>}
                </div>
            </div>
        </header>
        <main id="main" tabIndex={-1} className={workspace ? 'workspace-main' : 'public-main'}>
            {workspace ? <div className="organizer-layout"><aside className="sidebar"><p className="organization" title={identity.organization ?? ''}>{identity.organization}</p>{workshopNavigation}</aside><div className="workspace-content">{children}</div></div> : children}
        </main>
        <footer><div className="footer-inner"><p>{t('footer')}</p><nav aria-label={t('gettingStarted')}>{['about', 'gettingStarted', 'accessibility'].map(key => <button key={key} onClick={() => { setHelp(key); helpDialog.current?.showModal(); }}>{t(key)}</button>)}</nav><span>&copy; UniHub</span></div></footer>
        <dialog ref={helpDialog} aria-labelledby="help-title"><CloseButton onClick={() => helpDialog.current?.close()} /><h2 id="help-title">{t(help)}</h2><p>{t(help + 'Text')}</p></dialog>
        {workspace && <dialog id="workspace-menu" className="navigation-dialog" ref={navigationDialog} aria-labelledby="navigation-title" onClose={() => setMenuOpen(false)} onClick={event => { if (event.target === event.currentTarget) closeNavigation(); }}>
            <div><CloseButton onClick={closeNavigation} /><h2 id="navigation-title">{t('management')}</h2><p className="organization">{identity.organization}</p>{workshopNavigation}</div>
        </dialog>}
    </>;
}
