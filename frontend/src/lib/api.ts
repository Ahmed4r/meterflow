let token: string | null = null;
let refreshPromise: Promise<boolean> | null = null;
export function setToken(value: string | null) {
  token = value;
}
export function hasAccessToken() {
  return token !== null;
}
export async function restore(): Promise<boolean> {
  if (!refreshPromise) {
    const refresh = async () => {
      try {
        const r = await fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' });
        if (!r.ok) {
          token = null;
          return false;
        }
        token = (await r.json()).accessToken;
        return true;
      } catch {
        return false;
      }
    };
    // Cookies are shared between tabs. Serialize rotation across tabs where Web Locks
    // is supported, as well as deduplicating refresh calls within this tab.
    const task = (async (): Promise<boolean> =>
      typeof navigator !== 'undefined' && navigator.locks
        ? await navigator.locks.request('meterflow-refresh', refresh)
        : await refresh())();
    refreshPromise = task.finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}
export async function api<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  if (token && !path.startsWith('/auth/')) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(`/api/v1${path}`, {
    ...options,
    headers,
    credentials: 'include',
    cache: 'no-store',
  });
  if (response.status === 401 && retry && !path.startsWith('/auth/') && (await restore()))
    return api(path, options, false);
  if (!response.ok) {
    let message = 'Request failed. Please try again.';
    try {
      message = (await response.json()).detail || message;
    } catch {}
    throw new Error(message);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
export async function download(path: string, name: string, retry = true): Promise<void> {
  const response = await fetch(`/api/v1${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (response.status === 401 && retry && (await restore())) return download(path, name, false);
  if (!response.ok) throw new Error('Download failed. Refresh and try again.');
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const money = (value: number | string) =>
  new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(Number(value));
export const number = (value: number) => new Intl.NumberFormat('en-GB').format(value);
export const currentMonth = () => new Date().toISOString().slice(0, 7) + '-01';
export type Organization = {
  id: string;
  name: string;
  role: 'OWNER' | 'ADMIN' | 'VIEWER';
  ai_enabled: boolean;
};
export type Profile = { id: string; name: string; email: string; organizations: Organization[] };
export type Summary = {
  month: string;
  currency: string;
  totalUsage: number;
  estimatedBill: number;
  previousUsage: number;
  previousBill: number;
  usageChangePercent: number | null;
  activeSubscriptions: number;
  daily: { date: string; units: number }[];
  services: { service: string; units: number }[];
  anomalies: { date: string; units: number }[];
};
export type Plan = {
  id: string;
  name: string;
  currency: string;
  basePrice: number;
  tiers: { upTo: number | null; unitPrice: number }[];
};
export type Customer = { id: string; name: string; reference: string; created_at: string };
export type Subscription = {
  id: string;
  customer: string;
  plan: string;
  status: string;
  starts_at: string;
  customer_id: string;
  plan_id: string;
};
export type Invoice = {
  id: string;
  customer: string;
  period_start: string;
  total: number;
  status: string;
  currency: string;
};
