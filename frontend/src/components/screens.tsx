'use client';
import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Plus,
  Search,
  Download,
  KeyRound,
  ShieldCheck,
  ArrowUpRight,
  Copy,
  Check,
  Sparkles,
  Send,
  Trash2,
} from 'lucide-react';
import {
  api,
  download,
  Organization,
  Profile,
  Customer,
  Subscription,
  Plan,
  Invoice,
  Summary,
  money,
  number,
  currentMonth,
} from '@/lib/api';
import { ActionForm, Field, Modal, Pager, Loading, ErrorNotice, Empty, Badge } from './ui';
function useRefresh() {
  const client = useQueryClient();
  return () => {
    void client.invalidateQueries();
  };
}
const write = (org: Organization) => org.role !== 'VIEWER';
export function Customers({ org }: { org: Organization }) {
  const [page, setPage] = useState(0),
    [search, setSearch] = useState(''),
    [modal, setModal] = useState(false);
  const refresh = useRefresh();
  const q = useQuery({
    queryKey: ['customers', org.id, page, search],
    queryFn: () =>
      api<Customer[]>(
        `/organizations/${org.id}/customers?page=${page}&search=${encodeURIComponent(search)}`,
      ),
  });
  return (
    <>
      <div className="section-toolbar">
        <label className="search">
          <Search size={16} />
          <input
            aria-label="Search customers"
            placeholder="Search customers…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </label>
        {write(org) && (
          <button className="button primary" onClick={() => setModal(true)}>
            <Plus size={16} />
            Add customer
          </button>
        )}
      </div>
      <ErrorNotice error={q.error} />
      <section className="panel">
        {q.isPending ? (
          <Loading />
        ) : q.data?.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Reference</th>
                  <th>Added</th>
                </tr>
              </thead>
              <tbody>
                {q.data.map((c, i) => (
                  <tr key={c.id}>
                    <td>
                      <span className="customer-cell">
                        <span className={`customer-avatar color-${i % 4}`}>
                          {c.name.slice(0, 2).toUpperCase()}
                        </span>
                        <strong>{c.name}</strong>
                      </span>
                    </td>
                    <td className="muted">{c.reference}</td>
                    <td>{new Date(c.created_at).toLocaleDateString('en-GB')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty>
            {search
              ? 'No customers match this search.'
              : 'Add your first customer to assign a subscription.'}
          </Empty>
        )}
        <Pager page={page} count={q.data?.length || 0} onChange={setPage} />
      </section>
      {modal && (
        <Modal title="Add customer" onClose={() => setModal(false)}>
          <ActionForm
            submit="Add customer"
            onDone={() => {
              setModal(false);
              refresh();
            }}
            onSubmit={(data) =>
              api(`/organizations/${org.id}/customers`, {
                method: 'POST',
                body: JSON.stringify(Object.fromEntries(data)),
              })
            }
          >
            <Field label="Customer name" name="name" />
            <Field label="Reference" name="reference" placeholder="CUS-1007" />
          </ActionForm>
        </Modal>
      )}
    </>
  );
}
export function Subscriptions({ org }: { org: Organization }) {
  const refresh = useRefresh();
  const [page, setPage] = useState(0),
    [modal, setModal] = useState<'plan' | 'subscription' | null>(null),
    [cancel, setCancel] = useState<Subscription | null>(null),
    [history, setHistory] = useState<Subscription | null>(null);
  const [tiers, setTiers] = useState([
    { upTo: '10000', unitPrice: '0.008' },
    { upTo: '', unitPrice: '0.005' },
  ]);
  const q = useQuery({
    queryKey: ['subscriptions', org.id, page],
    queryFn: () => api<Subscription[]>(`/organizations/${org.id}/subscriptions?page=${page}`),
  });
  const plans = useQuery({
    queryKey: ['plans', org.id],
    queryFn: () => api<Plan[]>(`/organizations/${org.id}/plans`),
  });
  const customers = useQuery({
    queryKey: ['customers', org.id, 0, ''],
    queryFn: () => api<Customer[]>(`/organizations/${org.id}/customers`),
  });
  const events = useQuery({
    queryKey: ['history', org.id, history?.id],
    enabled: !!history,
    queryFn: () =>
      api<{ action: string; created_at: string }[]>(
        `/organizations/${org.id}/subscriptions/${history?.id}/history`,
      ),
  });
  return (
    <>
      <div className="section-toolbar">
        <div className="section-caption">Monthly plans · graduated usage pricing</div>
        {write(org) && (
          <div className="toolbar">
            <button className="button" onClick={() => setModal('plan')}>
              <Plus size={16} />
              Create plan
            </button>
            <button className="button primary" onClick={() => setModal('subscription')}>
              <Plus size={16} />
              Assign plan
            </button>
          </div>
        )}
      </div>
      <ErrorNotice error={q.error || plans.error} />
      <div className="plan-grid">
        {plans.data?.map((p) => (
          <section className="plan-card" key={p.id}>
            <span className="plan-label">{p.name}</span>
            <h2>
              {money(p.basePrice)}
              <small>/ month</small>
            </h2>
            <div className="plan-tiers">
              {p.tiers.map((t, i) => (
                <div key={i}>
                  <span>
                    {i === 0 ? 'First' : `After ${number(p.tiers[i - 1].upTo || 0)}`}
                    {i === 0 && t.upTo ? ` ${number(t.upTo)}` : ''} units
                  </span>
                  <strong>
                    {new Intl.NumberFormat('de-DE', { maximumFractionDigits: 6 }).format(
                      t.unitPrice,
                    )}{' '}
                    € / unit
                  </strong>
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>
      <section className="panel">
        <div className="panel-heading">
          <h2>Subscriptions</h2>
          <span className="muted">UTC calendar months</span>
        </div>
        {q.isPending ? (
          <Loading />
        ) : q.data?.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Plan</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <strong>{s.customer}</strong>
                    </td>
                    <td>{s.plan}</td>
                    <td>
                      <Badge value={s.status} />
                    </td>
                    <td className="muted">{new Date(s.starts_at).toLocaleDateString('en-GB')}</td>
                    <td>
                      <div className="row-actions">
                        <button className="text-link" onClick={() => setHistory(s)}>
                          History
                        </button>
                        {write(org) && s.status === 'ACTIVE' && (
                          <button className="text-link danger-text" onClick={() => setCancel(s)}>
                            Cancel
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty>Create a plan and assign it to a customer.</Empty>
        )}
        <Pager page={page} count={q.data?.length || 0} onChange={setPage} />
      </section>
      {modal === 'plan' && (
        <Modal title="Create pricing plan" onClose={() => setModal(null)}>
          <ActionForm
            submit="Create plan"
            onDone={() => {
              setModal(null);
              refresh();
            }}
            onSubmit={(data) =>
              api(`/organizations/${org.id}/plans`, {
                method: 'POST',
                body: JSON.stringify({
                  name: data.get('name'),
                  basePrice: data.get('basePrice'),
                  tiers: tiers.map((t) => ({
                    upTo: t.upTo ? Number(t.upTo) : null,
                    unitPrice: t.unitPrice,
                  })),
                }),
              })
            }
          >
            <Field label="Plan name" name="name" />
            <Field
              label="Monthly fee (EUR)"
              name="basePrice"
              type="number"
              min={0}
              max={1000000}
              step="0.01"
              defaultValue="29"
            />
            <div className="field">
              Graduated tiers{' '}
              <small>Final tier has no upper limit. Plans cannot be edited after creation.</small>
            </div>
            {tiers.map((t, i) => (
              <div className="tier-input" key={i}>
                <label className="field">
                  {i === tiers.length - 1 ? 'Unlimited' : 'Up to units'}
                  <input
                    type="number"
                    aria-label={`Tier ${i + 1} upper bound`}
                    disabled={i === tiers.length - 1}
                    placeholder="No limit"
                    value={t.upTo}
                    min={1}
                    required={i !== tiers.length - 1}
                    onChange={(e) =>
                      setTiers(tiers.map((x, j) => (j === i ? { ...x, upTo: e.target.value } : x)))
                    }
                  />
                </label>
                <label className="field">
                  EUR / unit
                  <input
                    type="number"
                    aria-label={`Tier ${i + 1} unit price`}
                    min={0}
                    max={10000}
                    step="0.000001"
                    required
                    value={t.unitPrice}
                    onChange={(e) =>
                      setTiers(
                        tiers.map((x, j) => (j === i ? { ...x, unitPrice: e.target.value } : x)),
                      )
                    }
                  />
                </label>
              </div>
            ))}
            <div className="toolbar">
              <button
                type="button"
                className="button small"
                disabled={tiers.length >= 20}
                onClick={() =>
                  setTiers([
                    ...tiers.slice(0, -1),
                    { upTo: '', unitPrice: '0.003' },
                    tiers[tiers.length - 1],
                  ])
                }
              >
                Add tier
              </button>
              <button
                type="button"
                className="button small"
                disabled={tiers.length === 1}
                onClick={() => setTiers([...tiers.slice(0, -2), tiers[tiers.length - 1]])}
              >
                Remove tier
              </button>
            </div>
          </ActionForm>
        </Modal>
      )}
      {modal === 'subscription' && (
        <Modal title="Assign a plan" onClose={() => setModal(null)}>
          {!customers.data?.length || !plans.data?.length ? (
            <Empty>Create a customer and a pricing plan first.</Empty>
          ) : (
            <ActionForm
              submit="Start subscription"
              onDone={() => {
                setModal(null);
                refresh();
              }}
              onSubmit={(data) =>
                api(`/organizations/${org.id}/subscriptions`, {
                  method: 'POST',
                  body: JSON.stringify(Object.fromEntries(data)),
                })
              }
            >
              <label className="field">
                Customer
                <select name="customerId">
                  {customers.data.map((c) => (
                    <option value={c.id} key={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Plan
                <select name="planId">
                  {plans.data.map((p) => (
                    <option value={p.id} key={p.id}>
                      {p.name} · {money(p.basePrice)} / month
                    </option>
                  ))}
                </select>
              </label>
              <p className="muted">
                Starts now. Calendar-month billing; full monthly fee, no proration.
              </p>
            </ActionForm>
          )}
        </Modal>
      )}
      {cancel && (
        <Modal title="Cancel subscription" onClose={() => setCancel(null)}>
          <p>Stop usage for {cancel.customer}? This month remains billable.</p>
          <ActionForm
            submit="Cancel subscription"
            onDone={() => {
              setCancel(null);
              refresh();
            }}
            onSubmit={() =>
              api(`/organizations/${org.id}/subscriptions/${cancel.id}/cancel`, { method: 'POST' })
            }
          >
            <span />
          </ActionForm>
        </Modal>
      )}
      {history && (
        <Modal title={`${history.customer} · history`} onClose={() => setHistory(null)}>
          <ErrorNotice error={events.error} />
          {events.isPending ? (
            <Loading />
          ) : (
            events.data?.map((e, i) => (
              <div className="history-row" key={i}>
                <Badge value={e.action} />
                <span>{new Date(e.created_at).toLocaleString('en-GB')}</span>
              </div>
            ))
          )}
        </Modal>
      )}
    </>
  );
}
export function Invoices({ org }: { org: Organization }) {
  const [page, setPage] = useState(0),
    [status, setStatus] = useState(''),
    [generate, setGenerate] = useState(false),
    [selected, setSelected] = useState<Invoice | null>(null),
    [error, setError] = useState<unknown>(null);
  const refresh = useRefresh();
  const q = useQuery({
    queryKey: ['invoices', org.id, page, status],
    queryFn: () =>
      api<Invoice[]>(`/organizations/${org.id}/invoices?page=${page}&status=${status}`),
  });
  const subscriptions = useQuery({
    queryKey: ['subscriptions', org.id, 0],
    queryFn: () => api<Subscription[]>(`/organizations/${org.id}/subscriptions`),
  });
  const detail = useQuery({
    queryKey: ['invoice', org.id, selected?.id],
    enabled: !!selected,
    queryFn: () =>
      api<{
        lines: { description: string; quantity: number; unit_price: number; amount: number }[];
      }>(`/organizations/${org.id}/invoices/${selected?.id}`),
  });
  return (
    <>
      <div className="section-toolbar">
        <div className="segmented">
          {['', 'OPEN', 'PAID'].map((s) => (
            <button
              key={s}
              className={s === status ? 'active' : ''}
              onClick={() => {
                setStatus(s);
                setPage(0);
              }}
            >
              {s || 'All invoices'}
            </button>
          ))}
        </div>
        {write(org) && (
          <button className="button primary" onClick={() => setGenerate(true)}>
            <Plus size={16} />
            Generate invoice
          </button>
        )}
      </div>
      <ErrorNotice error={q.error || error} />
      <section className="panel">
        {q.isPending ? (
          <Loading />
        ) : q.data?.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Invoice</th>
                  <th>Customer</th>
                  <th>Period</th>
                  <th>Status</th>
                  <th className="numeric">Amount</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.map((i) => (
                  <tr key={i.id}>
                    <td>
                      <button className="text-link invoice-id" onClick={() => setSelected(i)}>
                        INV-{i.id.slice(0, 8).toUpperCase()}
                      </button>
                    </td>
                    <td>{i.customer}</td>
                    <td>{i.period_start.slice(0, 7)}</td>
                    <td>
                      <Badge value={i.status} />
                    </td>
                    <td className="numeric amount">{money(i.total)}</td>
                    <td>
                      <button
                        className="icon-button"
                        aria-label={`Download invoice ${i.id.slice(0, 8)}`}
                        onClick={() =>
                          download(
                            `/organizations/${org.id}/invoices/${i.id}/pdf`,
                            `invoice-${i.id}.pdf`,
                          ).catch(setError)
                        }
                      >
                        <Download size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty>No invoices in this view.</Empty>
        )}
        <Pager page={page} count={q.data?.length || 0} onChange={setPage} />
      </section>
      {generate && (
        <Modal title="Generate invoice" onClose={() => setGenerate(false)}>
          <ActionForm
            submit="Generate invoice"
            onDone={() => {
              setGenerate(false);
              refresh();
            }}
            onSubmit={(data) =>
              api(`/organizations/${org.id}/invoices`, {
                method: 'POST',
                body: JSON.stringify({
                  subscriptionId: data.get('subscriptionId'),
                  month: data.get('month') + '-01',
                }),
              })
            }
          >
            <label className="field">
              Subscription
              <select name="subscriptionId" required>
                <option value="">Choose a subscription</option>
                {subscriptions.data?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.customer} · {s.plan}
                  </option>
                ))}
              </select>
            </label>
            <Field label="Completed billing month" name="month" type="month" />
            <p className="muted">
              Closes the month to new events. Retrying returns the existing invoice.
            </p>
          </ActionForm>
        </Modal>
      )}
      {selected && (
        <Modal
          title={`Invoice ${selected.id.slice(0, 8).toUpperCase()}`}
          onClose={() => setSelected(null)}
        >
          <div className="invoice-summary">
            <div>
              <strong>{selected.customer}</strong>
              <p>{selected.period_start.slice(0, 7)}</p>
            </div>
            <Badge value={selected.status} />
          </div>
          <ErrorNotice error={detail.error} />
          {detail.isPending ? (
            <Loading />
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Line item</th>
                    <th>Units</th>
                    <th className="numeric">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.data?.lines.map((l, i) => (
                    <tr key={i}>
                      <td>
                        {l.description}
                        <small className="block muted">{l.unit_price} EUR / unit</small>
                      </td>
                      <td>{number(l.quantity)}</td>
                      <td className="numeric">{money(l.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="invoice-total">
            <span>Total</span>
            <strong>{money(selected.total)}</strong>
          </div>
          {selected.status === 'OPEN' && write(org) && (
            <ActionForm
              submit="Mark as paid"
              onSubmit={() =>
                api(`/organizations/${org.id}/invoices/${selected.id}/paid`, { method: 'POST' })
              }
              onDone={() => {
                setSelected(null);
                refresh();
              }}
            >
              <small>Records payment status only. No payment is collected.</small>
            </ActionForm>
          )}
        </Modal>
      )}
    </>
  );
}
export function OrganizationSettings({
  org,
  refresh,
}: {
  org: Organization;
  refresh: () => unknown;
}) {
  const [key, setKey] = useState(''),
    [copied, setCopied] = useState(false),
    [error, setError] = useState<unknown>(null);
  const keys = useQuery({
    queryKey: ['keys', org.id],
    enabled: write(org),
    queryFn: () =>
      api<{ id: string; name: string; prefix: string; revoked: boolean }[]>(
        `/organizations/${org.id}/api-keys`,
      ),
  });
  const members = useQuery({
    queryKey: ['members', org.id],
    queryFn: () =>
      api<{ id: string; name: string; email: string; role: string }[]>(
        `/organizations/${org.id}/members`,
      ),
  });
  return (
    <div className="settings-stack">
      <section className="panel settings-panel">
        <div>
          <h2>Organization</h2>
          <p>Workspace identity and external processing.</p>
        </div>
        {write(org) ? (
          <ActionForm
            key={org.id + org.ai_enabled}
            onSubmit={async (d) => {
              await api(`/organizations/${org.id}`, {
                method: 'PATCH',
                body: JSON.stringify({ name: d.get('name'), aiEnabled: d.get('ai') === 'on' }),
              });
              await refresh();
            }}
          >
            <Field label="Organization name" name="name" defaultValue={org.name} />
            <label className="checkbox-field">
              <input type="checkbox" name="ai" defaultChecked={org.ai_enabled} />
              <span>
                Enable external AI analysis
                <small>
                  Aggregated usage and billing totals are sent to Groq. No customer names, keys or
                  account details are sent. Provider retention policies apply.
                </small>
              </span>
            </label>
          </ActionForm>
        ) : (
          <Empty>Only owners and admins can change settings.</Empty>
        )}
      </section>
      <section className="panel settings-panel">
        <div>
          <h2>API keys</h2>
          <p>Tenant-scoped ingestion access. Keys are shown once.</p>
        </div>
        <div>
          <ErrorNotice error={error || keys.error} />
          {write(org) && (
            <ActionForm
              submit="Create API key"
              onSubmit={async (d) => {
                const result = await api<{ token: string }>(`/organizations/${org.id}/api-keys`, {
                  method: 'POST',
                  body: JSON.stringify({ name: d.get('name') }),
                });
                setKey(result.token);
                setCopied(false);
                await refresh();
              }}
            >
              <Field label="Key name" name="name" placeholder="Production ingestion" />
            </ActionForm>
          )}
          {key && (
            <div className="key-reveal">
              <label>
                Copy this key now
                <input readOnly value={key} aria-label="New API key" />
              </label>
              <button
                className="button small"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(key);
                    setCopied(true);
                  } catch (e) {
                    setError(e);
                  }
                }}
              >
                {copied ? <Check size={14} /> : <Copy size={14} />} {copied ? 'Copied' : 'Copy'}
              </button>
              <button className="text-link" onClick={() => setKey('')}>
                Dismiss
              </button>
            </div>
          )}
          {keys.data?.map((k) => (
            <div className="key-row" key={k.id}>
              <KeyRound size={17} />
              <div>
                <strong>{k.name}</strong>
                <small>{k.prefix}••••</small>
              </div>
              {k.revoked ? (
                <Badge value="REVOKED" />
              ) : (
                <button
                  className="text-link danger-text"
                  onClick={async () => {
                    if (!confirm(`Revoke ${k.name}? Ingestion with this key will stop.`)) return;
                    try {
                      await api(`/organizations/${org.id}/api-keys/${k.id}`, { method: 'DELETE' });
                      await refresh();
                    } catch (e) {
                      setError(e);
                    }
                  }}
                >
                  Revoke
                </button>
              )}
            </div>
          ))}
        </div>
      </section>
      <section className="panel settings-panel">
        <div>
          <h2>Members</h2>
          <p>Owners manage access. Viewers have read-only access.</p>
        </div>
        <div>
          <ErrorNotice error={members.error} />
          {members.data?.map((m) => (
            <div className="member-row" key={m.id}>
              <span className="avatar">{m.name[0]}</span>
              <div>
                <strong>{m.name}</strong>
                <small>{m.email}</small>
              </div>
              <Badge value={m.role} />
            </div>
          ))}
          {org.role === 'OWNER' && (
            <ActionForm
              submit="Add or update member"
              onSubmit={async (d) => {
                await api(`/organizations/${org.id}/members`, {
                  method: 'POST',
                  body: JSON.stringify(Object.fromEntries(d)),
                });
                await refresh();
              }}
            >
              <Field label="Registered user's email" name="email" type="email" />
              <label className="field">
                Role
                <select name="role">
                  <option value="VIEWER">Viewer</option>
                  <option value="ADMIN">Admin</option>
                  <option value="OWNER">Owner</option>
                </select>
              </label>
            </ActionForm>
          )}
        </div>
      </section>
    </div>
  );
}
export function Privacy({
  profile,
  refresh,
  onDeleted,
}: {
  profile: Profile;
  refresh: () => unknown;
  onDeleted: () => void;
}) {
  const [deleting, setDeleting] = useState(false),
    [error, setError] = useState<unknown>(null);
  return (
    <div className="settings-stack">
      <section className="panel settings-panel">
        <div>
          <h2>Profile</h2>
          <p>Your account details.</p>
        </div>
        <ActionForm
          onSubmit={async (d) => {
            await api('/me', { method: 'PATCH', body: JSON.stringify({ name: d.get('name') }) });
            await refresh();
          }}
        >
          <Field label="Name" name="name" defaultValue={profile.name} />
          <label className="field">
            Email
            <input readOnly value={profile.email} />
          </label>
        </ActionForm>
      </section>
      <section className="panel settings-panel">
        <div>
          <h2>Your data</h2>
          <p>Access and control your personal information.</p>
        </div>
        <div>
          <div className="privacy-item">
            <ShieldCheck size={22} />
            <div>
              <strong>Export personal data</strong>
              <p>Profile, memberships and session metadata. Secrets are excluded.</p>
            </div>
            <button
              className="button"
              onClick={() => download('/me/export', 'meterflow-personal-data.json').catch(setError)}
            >
              <Download size={16} />
              Export JSON
            </button>
          </div>
          <ErrorNotice error={error} />
          <div className="privacy-item">
            <Trash2 size={22} />
            <div>
              <strong>Delete account</strong>
              <p>
                Deletes your identity and sessions. Billing records are retained. Sole-owner
                workspaces are anonymized and keys revoked.
              </p>
            </div>
            <button className="button danger" onClick={() => setDeleting(true)}>
              Delete account
            </button>
          </div>
        </div>
      </section>
      {deleting && (
        <Modal title="Delete your account?" onClose={() => setDeleting(false)}>
          <p>This cannot be undone. Shared workspaces need another owner.</p>
          <ActionForm
            submit="Permanently delete account"
            onDone={onDeleted}
            onSubmit={(d) =>
              api('/me', {
                method: 'DELETE',
                body: JSON.stringify({ password: d.get('password') }),
              })
            }
          >
            <Field label="Confirm your password" name="password" type="password" />
          </ActionForm>
        </Modal>
      )}
    </div>
  );
}
type Analysis = {
  mode: string;
  explanation: string;
  facts: Summary;
  recommendations?: { plan: string; amount: number }[];
};
export function Analyst({ org, month }: { org: Organization; month: string }) {
  const [question, setQuestion] = useState('Why did my bill increase?'),
    [result, setResult] = useState<Analysis | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null);
  const questions = [
    'Why did my bill increase?',
    'Which service uses the most units?',
    'Compare this month with last month.',
    'Are there unusual usage spikes?',
    'Which plan is more cost-effective?',
  ];
  async function ask(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setResult(
        await api<Analysis>(`/organizations/${org.id}/analyst`, {
          method: 'POST',
          body: JSON.stringify({ question, month }),
        }),
      );
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="analyst-layout">
      <section className="panel analyst-panel">
        <div className="analyst-welcome">
          <span className="analyst-symbol">
            <Sparkles size={26} />
          </span>
          <h2>Your numbers, explained.</h2>
          <p>Explore usage, compare costs, and find unusual patterns.</p>
        </div>
        <div className="question-list">
          {questions.map((q) => (
            <button
              key={q}
              onClick={() => setQuestion(q)}
              className={question === q ? 'chosen' : ''}
            >
              {q}
              <ArrowUpRight size={15} />
            </button>
          ))}
        </div>
        <form className="analyst-input" onSubmit={ask}>
          <label className="sr-only" htmlFor="question">
            Billing question
          </label>
          <input
            id="question"
            value={question}
            maxLength={500}
            required
            onChange={(e) => setQuestion(e.target.value)}
          />
          <button className="button primary" disabled={busy} aria-label="Analyze billing">
            {busy ? 'Analyzing…' : <Send size={18} />}
          </button>
        </form>
        <ErrorNotice error={error} />
        {result && (
          <div className="analysis-result" aria-live="polite">
            <div className="result-heading">
              <strong>
                {result.mode === 'AI' ? 'AI-assisted analysis' : 'Verified analytics'}
              </strong>
              <Badge value={result.mode} />
            </div>
            <p>{result.explanation}</p>
            <dl className="fact-grid">
              <div>
                <dt>Usage</dt>
                <dd>{number(result.facts.totalUsage)} units</dd>
              </div>
              <div>
                <dt>Estimated bill</dt>
                <dd>{money(result.facts.estimatedBill)}</dd>
              </div>
              <div>
                <dt>Previous bill</dt>
                <dd>{money(result.facts.previousBill)}</dd>
              </div>
              <div>
                <dt>Unusual days</dt>
                <dd>{result.facts.anomalies.length}</dd>
              </div>
            </dl>
            {result.recommendations?.length && (
              <div>
                <h3>Plan comparison</h3>
                <p className="muted">
                  Illustrative cost for one subscription using the selected period’s total units.
                </p>
                {result.recommendations.map((r) => (
                  <div className="history-row" key={r.plan}>
                    <span>{r.plan}</span>
                    <strong>{money(r.amount)}</strong>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </section>
      <aside className="analyst-aside">
        <ShieldCheck size={22} />
        <h3>Grounded in your data</h3>
        <p>Amounts are calculated by MeterFlow, never by a language model.</p>
        <hr />
        <h3>{org.ai_enabled ? 'External AI enabled' : 'External AI is off'}</h3>
        <p>
          {org.ai_enabled
            ? 'Only aggregated billing facts are sent to the configured provider. Responses are not saved.'
            : 'Verified analytics work without an AI provider. Enable external processing in organization settings.'}
        </p>
        <p className="muted">The analyst cannot change plans, usage or invoices.</p>
      </aside>
    </div>
  );
}
