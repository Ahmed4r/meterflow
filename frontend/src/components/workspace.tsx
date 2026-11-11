'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  LayoutDashboard,
  ChartNoAxesCombined,
  Layers,
  Users,
  ReceiptText,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  UserRound,
  LogOut,
  Sun,
  Moon,
  ChevronDown,
  ArrowUpRight,
  Activity,
  Sparkles,
  CalendarDays,
  ArrowRight,
} from 'lucide-react';
import { api, restore, setToken, hasAccessToken, Profile, currentMonth } from '@/lib/api';
import { ActionForm, Field, ErrorNotice, Loading } from './ui';
import { Overview } from './overview';
import {
  Customers,
  Subscriptions,
  Invoices,
  OrganizationSettings,
  Privacy,
  Analyst,
} from './screens';
const nav = [
  { path: '/', label: 'Overview', icon: LayoutDashboard },
  { path: '/usage', label: 'Usage analytics', icon: ChartNoAxesCombined },
  { path: '/subscriptions', label: 'Subscriptions', icon: Layers },
  { path: '/customers', label: 'Customers', icon: Users },
  { path: '/invoices', label: 'Invoices', icon: ReceiptText },
  { path: '/analyst', label: 'Billing analyst', icon: Sparkles },
];
export function Mark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <i />
      <i />
      <i />
      <i />
    </span>
  );
}
export function Workspace() {
  const pathname = usePathname(),
    router = useRouter(),
    client = useQueryClient();
  const [ready, setReady] = useState(false),
    [signedIn, setSignedIn] = useState(false),
    [orgId, setOrgId] = useState(''),
    [month, setMonth] = useState(currentMonth()),
    [dark, setDark] = useState(false),
    [collapsed, setCollapsed] = useState(false),
    [error, setError] = useState<unknown>(null);
  useEffect(() => {
    const theme = localStorage.getItem('mf-theme') === 'dark';
    setDark(theme);
    document.documentElement.dataset.theme = theme ? 'dark' : 'light';
    (hasAccessToken() ? Promise.resolve(true) : restore()).then((ok) => {
      setSignedIn(ok);
      setReady(true);
    });
  }, []);
  const profile = useQuery({
    queryKey: ['profile'],
    queryFn: () => api<Profile>('/me'),
    enabled: ready && signedIn,
  });
  const org =
    profile.data?.organizations.find((o) => o.id === orgId) || profile.data?.organizations[0];
  const authRoute = pathname === '/login' || pathname === '/register';
  useEffect(() => {
    if (ready && !signedIn && !authRoute) router.replace('/login');
  }, [ready, signedIn, authRoute, router]);
  function toggleTheme() {
    const next = !dark;
    setDark(next);
    document.documentElement.dataset.theme = next ? 'dark' : 'light';
    localStorage.setItem('mf-theme', next ? 'dark' : 'light');
  }
  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' });
      setToken(null);
      setSignedIn(false);
      client.clear();
      router.replace('/login');
    } catch (e) {
      setError(e);
    }
  }
  if (!ready) return <Loading />;
  if (!signedIn)
    return (
      <Auth
        register={pathname === '/register'}
        onLogin={() => {
          client.clear();
          setSignedIn(true);
          router.replace('/');
        }}
      />
    );
  if (profile.isError)
    return (
      <main className="standalone">
        <ErrorNotice error={profile.error} />
        <button className="button" onClick={() => profile.refetch()}>
          Retry
        </button>
        <button className="button" onClick={logout}>
          Sign out
        </button>
      </main>
    );
  if (!profile.data) return <Loading />;
  if (!org)
    return (
      <main className="standalone">
        <h1>No organization access</h1>
        <p>Ask an organization owner to add your email.</p>
        <button className="button" onClick={logout}>
          Sign out
        </button>
      </main>
    );
  const section =
    nav.find((n) => n.path === pathname)?.label ||
    { '/settings': 'Organization settings', '/profile': 'Profile & privacy' }[pathname] ||
    'Overview';
  const refresh = () => client.invalidateQueries();
  return (
    <div className={`workspace ${collapsed ? 'collapsed' : ''}`}>
      <aside className="sidebar">
        <Link href="/" className="brand">
          <Mark />
          <span>
            MeterFlow<span className="brand-dot">.</span>
          </span>
        </Link>
        <label className="org-select">
          <span className="org-avatar">{org.name.slice(0, 1)}</span>
          <select
            aria-label="Organization"
            value={org.id}
            onChange={(e) => {
              setOrgId(e.target.value);
              client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'profile' });
            }}
          >
            {profile.data.organizations.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
          <ChevronDown size={14} />
        </label>
        <div className="nav-label">Workspace</div>
        <nav aria-label="Main navigation">
          {nav.map(({ path, label, icon: Icon }) => (
            <Link
              key={path}
              href={path}
              aria-label={label}
              title={label}
              className={`nav-item ${pathname === path ? 'selected' : ''}`}
              aria-current={pathname === path ? 'page' : undefined}
            >
              <Icon size={18} />
              <span>{label}</span>
              {path === '/analyst' && <span className="nav-tag">AI</span>}
            </Link>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="workspace-note">
            <Activity size={17} />
            <div>
              Every unit accounted for.<small>Meter. Understand. Optimize.</small>
            </div>
          </div>
          <Link
            className={`nav-item ${pathname === '/settings' ? 'selected' : ''}`}
            href="/settings"
            aria-label="Settings"
          >
            <Settings size={18} />
            <span>Settings</span>
          </Link>
          <Link
            className={`profile-link ${pathname === '/profile' ? 'selected' : ''}`}
            href="/profile"
            aria-label={`${profile.data.name} profile and privacy`}
          >
            <span className="avatar">
              {profile.data.name
                .split(' ')
                .map((s) => s[0])
                .slice(0, 2)
                .join('')}
            </span>
            <span>
              {profile.data.name}
              <small>{org.role.toLowerCase()}</small>
            </span>
            <ChevronDown size={14} />
          </Link>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button"
              aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}
              onClick={() => setCollapsed(!collapsed)}
            >
              {collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
            </button>
            <span className="breadcrumb-org">Workspace</span>
            <span className="slash">/</span>
            <span>{section}</span>
          </div>
          <div className="topbar-actions">
            <span className="live-indicator">
              <span />
              Live data
            </span>
            <button
              className="icon-button"
              aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
              onClick={toggleTheme}
            >
              {dark ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <button className="icon-button" aria-label="Sign out" onClick={logout}>
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main id="main-content">
          <ErrorNotice error={error} />
          <div className="page-heading">
            <div>
              <div className="page-context">{org.name}</div>
              <h1>{section}</h1>
              <p>
                {pathname === '/'
                  ? 'A clear view of your usage and billing.'
                  : pathname === '/usage'
                    ? 'See where your usage goes.'
                    : pathname === '/analyst'
                      ? 'Understand the numbers behind your bill.'
                      : ''}
              </p>
            </div>
            {['/', '/usage', '/analyst'].includes(pathname) && (
              <label className="date-filter">
                <CalendarDays size={16} />
                <input
                  aria-label="Billing month"
                  type="month"
                  value={month.slice(0, 7)}
                  max={currentMonth().slice(0, 7)}
                  onChange={(e) => {
                    if (e.target.value) setMonth(e.target.value + '-01');
                  }}
                />
              </label>
            )}
          </div>
          {pathname === '/' || pathname === '/usage' ? (
            <Overview org={org} month={month} detailed={pathname === '/usage'} />
          ) : pathname === '/customers' ? (
            <Customers org={org} />
          ) : pathname === '/subscriptions' ? (
            <Subscriptions org={org} />
          ) : pathname === '/invoices' ? (
            <Invoices org={org} />
          ) : pathname === '/settings' ? (
            <OrganizationSettings org={org} refresh={refresh} />
          ) : pathname === '/profile' ? (
            <Privacy
              profile={profile.data}
              refresh={refresh}
              onDeleted={() => {
                setToken(null);
                setSignedIn(false);
                client.clear();
                router.replace('/login');
              }}
            />
          ) : pathname === '/analyst' ? (
            <Analyst org={org} month={month} />
          ) : (
            <Overview org={org} month={month} detailed={false} />
          )}
          <footer className="page-footer">
            <span>
              <Mark />
              MeterFlow
            </span>
            <span>
              All billing periods use UTC <span className="footer-separator">/</span> EUR
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
function Auth({ register, onLogin }: { register: boolean; onLogin: () => void }) {
  return (
    <main className="auth-page">
      <section className="auth-intro">
        <Link href="/login" className="brand">
          <Mark />
          MeterFlow.
        </Link>
        <div>
          <span className="auth-kicker">
            <Activity size={18} />
            Intelligent usage & billing
          </span>
          <h1>
            Know your usage.
            <br />
            Own your costs.
          </h1>
          <p>
            From the first API call to the final invoice.
            <br />
            Everything adds up.
          </p>
          <div className="auth-ledger">
            <div>
              <span>Usage recorded</span>
              <span className="ledger-line" />
            </div>
            <div>
              <span>Costs calculated</span>
              <span className="ledger-line short" />
            </div>
            <div>
              <span>Clarity delivered</span>
              <span className="ledger-check">✓</span>
            </div>
          </div>
        </div>
        <small>Built for teams that care about the details.</small>
      </section>
      <section className="auth-form-panel">
        <div className="auth-form">
          <span className="auth-icon">
            <UserRound size={24} />
          </span>
          <h2>{register ? 'Create your workspace' : 'Welcome back'}</h2>
          <p>
            {register
              ? 'Start with a clear view of your costs.'
              : 'Sign in to your MeterFlow workspace.'}
          </p>
          <ActionForm
            submit={register ? 'Create account' : 'Sign in'}
            onSubmit={async (data) => {
              const body = Object.fromEntries(data);
              const result = await api<{ accessToken: string }>(
                register ? '/auth/register' : '/auth/login',
                { method: 'POST', body: JSON.stringify(body) },
              );
              setToken(result.accessToken);
              onLogin();
            }}
          >
            {register && (
              <>
                <Field label="Your name" name="name" />
                <Field label="Organization name" name="organization" />
              </>
            )}
            <Field label="Email address" name="email" type="email" placeholder="you@company.com" />
            <Field label="Password" name="password" type="password" minLength={10} />
            {register && <small>At least 10 characters. Maximum 72.</small>}
          </ActionForm>
          <div className="auth-switch">
            {register ? 'Already have an account?' : 'New to MeterFlow?'}{' '}
            <Link href={register ? '/login' : '/register'}>
              {register ? 'Sign in' : 'Create an account'}
              <ArrowRight size={14} />
            </Link>
          </div>
          <p className="auth-privacy">
            Your workspace stays private. External AI analysis is off by default.
          </p>
        </div>
      </section>
    </main>
  );
}
