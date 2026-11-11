'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
} from 'recharts';
import {
  ArrowUpRight,
  ArrowRight,
  Download,
  ChartNoAxesCombined,
  Info,
  Check,
  TriangleAlert,
} from 'lucide-react';
import { api, Summary, Organization, Invoice, money, number } from '@/lib/api';
import { ErrorNotice, Loading, Empty, Badge } from './ui';
export function Overview({
  org,
  month,
  detailed,
}: {
  org: Organization;
  month: string;
  detailed: boolean;
}) {
  const [chart, setChart] = useState<'area' | 'bar'>('area'),
    [service, setService] = useState('all');
  const summary = useQuery({
    queryKey: ['analytics', org.id, month],
    refetchInterval: 30000,
    queryFn: () => api<Summary>(`/organizations/${org.id}/analytics?month=${month}`),
  });
  const invoices = useQuery({
    queryKey: ['invoices', org.id, 0, ''],
    refetchInterval: 30000,
    queryFn: () => api<Invoice[]>(`/organizations/${org.id}/invoices`),
  });
  if (summary.isPending) return <Loading />;
  if (summary.isError)
    return (
      <>
        <ErrorNotice error={summary.error} />
        <button className="button" onClick={() => summary.refetch()}>
          Retry
        </button>
      </>
    );
  const s = summary.data,
    largest = s.services[0];
  const filtered = service === 'all' ? s.services : s.services.filter((x) => x.service === service);
  function exportUsage() {
    const text = 'Date,Units\n' + s.daily.map((x) => `${x.date},${x.units}`).join('\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `usage-${month}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
  const chartProps = { data: s.daily, margin: { top: 15, right: 10, left: -18, bottom: 0 } };
  const axes = (
    <>
      <CartesianGrid strokeDasharray="3 4" vertical={false} stroke="var(--border)" />
      <XAxis
        dataKey="date"
        tickFormatter={(x) => String(Number(x.slice(-2)))}
        tickLine={false}
        axisLine={false}
        minTickGap={30}
        tick={{ fill: 'var(--muted)', fontSize: 11 }}
        dy={9}
      />
      <YAxis
        tickFormatter={(x) => (x >= 1000 ? `${x / 1000}k` : String(x))}
        tickLine={false}
        axisLine={false}
        tick={{ fill: 'var(--muted)', fontSize: 11 }}
      />
      <Tooltip
        contentStyle={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 6,
          fontSize: 12,
          color: 'var(--text)',
        }}
        labelFormatter={(label) => String(label)}
        formatter={(value) => [number(Number(value)), 'Units']}
      />
    </>
  );
  return (
    <>
      <section className="metric-strip" aria-label="Billing summary">
        <div>
          <span>Total usage</span>
          <strong>
            {number(s.totalUsage)}
            <small>units</small>
          </strong>
          <p
            className={
              s.usageChangePercent !== null && s.usageChangePercent > 0 ? 'trend' : 'muted'
            }
          >
            {s.usageChangePercent === null ? (
              'No previous usage'
            ) : (
              <>
                <ArrowUpRight size={14} />
                {s.usageChangePercent > 0 ? '+' : ''}
                {s.usageChangePercent}%
              </>
            )}
            <span>vs. previous full month</span>
          </p>
        </div>
        <div>
          <span>Estimated bill</span>
          <strong>{money(s.estimatedBill)}</strong>
          <p>
            <span>Previous month</span> {money(s.previousBill)}
          </p>
        </div>
        <div>
          <span>Active subscriptions</span>
          <strong>{s.activeSubscriptions.toString().padStart(2, '0')}</strong>
          <p>
            <span className="status-dot" />
            Monthly billing
          </p>
        </div>
        <div>
          <span>Usage anomalies</span>
          <strong>
            {s.anomalies.length.toString().padStart(2, '0')}
            <small>{s.anomalies.length === 1 ? 'spike' : 'spikes'}</small>
          </strong>
          <p>
            {s.anomalies.length ? (
              <>
                <TriangleAlert size={13} />
                <span>Review usage patterns</span>
              </>
            ) : (
              <>
                <Check size={14} />
                <span>Within expected range</span>
              </>
            )}
          </p>
        </div>
      </section>
      <div className="overview-grid">
        <section className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <h2>Usage over time</h2>
              <p>Daily metered units</p>
            </div>
            <div className="segmented" role="group" aria-label="Chart style">
              <button
                aria-pressed={chart === 'area'}
                className={chart === 'area' ? 'active' : ''}
                onClick={() => setChart('area')}
              >
                Trend
              </button>
              <button
                aria-pressed={chart === 'bar'}
                className={chart === 'bar' ? 'active' : ''}
                onClick={() => setChart('bar')}
              >
                Bars
              </button>
            </div>
          </div>
          <div className="chart-legend">
            <span className="legend-dot" />
            Usage{' '}
            <span className="chart-month">
              {new Date(month + 'T12:00:00Z').toLocaleDateString('en-GB', {
                month: 'long',
                year: 'numeric',
                timeZone: 'UTC',
              })}
            </span>
          </div>
          <div
            className="usage-chart"
            role="img"
            aria-label={`Daily usage chart: ${number(s.totalUsage)} units in selected month`}
          >
            <ResponsiveContainer width="100%" height="100%">
              {chart === 'area' ? (
                <AreaChart {...chartProps}>
                  {axes}
                  <Area
                    isAnimationActive={false}
                    type="monotone"
                    dataKey="units"
                    stroke="#087f8c"
                    strokeWidth={2.5}
                    fill="#087f8c"
                    fillOpacity={0.09}
                    activeDot={{ r: 4 }}
                  />
                </AreaChart>
              ) : (
                <BarChart {...chartProps}>
                  {axes}
                  <Bar
                    isAnimationActive={false}
                    dataKey="units"
                    fill="#087f8c"
                    radius={[3, 3, 0, 0]}
                  />
                </BarChart>
              )}
            </ResponsiveContainer>
          </div>
          <div className="chart-footer">
            <Info size={14} />
            <span>Current month is month-to-date. Estimates include full monthly fees.</span>
          </div>
        </section>
        <section className="panel service-panel">
          <div className="panel-heading">
            <div>
              <h2>Usage by service</h2>
              <p>Where your units go</p>
            </div>
            <ChartNoAxesCombined size={18} className="muted" />
          </div>
          {s.services.length === 0 ? (
            <Empty>Usage will appear after your first event.</Empty>
          ) : (
            <>
              <div className="service-total">
                <strong>
                  {largest ? Math.round((largest.units / Math.max(s.totalUsage, 1)) * 100) : 0}%
                </strong>
                <span>from {largest?.service.replaceAll('-', ' ')}</span>
              </div>
              <div className="distribution-bar">
                {s.services.map((x, i) => (
                  <span
                    key={x.service}
                    style={{
                      width: `${(x.units / Math.max(s.totalUsage, 1)) * 100}%`,
                      background: ['#087f8c', '#65a9ac', '#a9ced0', '#dce9e9'][i % 4],
                    }}
                  />
                ))}
              </div>
              <div className="service-list">
                {s.services.map((x, i) => (
                  <div key={x.service}>
                    <span>
                      <i
                        style={{ background: ['#087f8c', '#65a9ac', '#a9ced0', '#dce9e9'][i % 4] }}
                      />
                      {x.service.replaceAll('-', ' ')}
                    </span>
                    <strong>{number(x.units)}</strong>
                    <small>{Math.round((x.units / Math.max(s.totalUsage, 1)) * 100)}%</small>
                  </div>
                ))}
              </div>
            </>
          )}
          <Link className="panel-link" href={detailed ? '/analyst' : '/usage'}>
            {detailed ? 'Ask the billing analyst' : 'Explore usage'}
            <ArrowRight size={15} />
          </Link>
        </section>
      </div>
      {detailed ? (
        <>
          <section className="panel">
            <div className="panel-heading">
              <h2>Usage breakdown</h2>
              <div className="toolbar">
                <select
                  aria-label="Filter service"
                  value={service}
                  onChange={(e) => setService(e.target.value)}
                >
                  <option value="all">All services</option>
                  {s.services.map((x) => (
                    <option key={x.service}>{x.service}</option>
                  ))}
                </select>
                <button className="button small" onClick={exportUsage}>
                  <Download size={15} />
                  Export daily CSV
                </button>
              </div>
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Service</th>
                    <th className="numeric">Units</th>
                    <th className="numeric">Share</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((x) => (
                    <tr key={x.service}>
                      <td>{x.service}</td>
                      <td className="numeric">{number(x.units)}</td>
                      <td className="numeric">
                        {((x.units / Math.max(s.totalUsage, 1)) * 100).toFixed(1)}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="panel anomaly-panel">
            <h2>Unusual usage</h2>
            <p className="muted">Days above twice the daily mean, with at least 100 units.</p>
            {s.anomalies.length ? (
              s.anomalies.map((d) => (
                <div className="anomaly" key={d.date}>
                  <TriangleAlert size={17} />
                  <span>{d.date}</span>
                  <strong>{number(d.units)} units</strong>
                </div>
              ))
            ) : (
              <Empty>No spikes detected in this period.</Empty>
            )}
          </section>
          <details className="panel daily-details">
            <summary>Daily values · accessible chart data</summary>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Units</th>
                  </tr>
                </thead>
                <tbody>
                  {s.daily.map((d) => (
                    <tr key={d.date}>
                      <td>{d.date}</td>
                      <td>{number(d.units)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      ) : (
        <>
          <section className="insight-banner">
            <span className="insight-icon">
              <ChartNoAxesCombined size={20} />
            </span>
            <div>
              <strong>Largest usage share</strong>
              <p>
                {largest
                  ? `${largest.service.replaceAll('-', ' ')} is your most-used service this month.`
                  : 'Connect your first service to start tracking usage.'}
              </p>
            </div>
            <Link href="/analyst" className="button">
              Explore insights
              <ArrowUpRight size={15} />
            </Link>
          </section>
          <section className="panel">
            <div className="panel-heading">
              <div>
                <h2>Recent invoices</h2>
                <p>Your latest billing activity</p>
              </div>
              <Link className="text-link" href="/invoices">
                View all invoices
                <ArrowRight size={15} />
              </Link>
            </div>
            <ErrorNotice error={invoices.error} />
            {invoices.data?.length ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Invoice</th>
                      <th>Customer</th>
                      <th>Billing period</th>
                      <th>Status</th>
                      <th className="numeric">Amount</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {invoices.data.slice(0, 5).map((i) => (
                      <tr key={i.id}>
                        <td className="invoice-id">INV-{i.id.slice(0, 8).toUpperCase()}</td>
                        <td>{i.customer}</td>
                        <td className="muted">
                          {new Date(i.period_start + 'T12:00Z').toLocaleDateString('en-GB', {
                            month: 'short',
                            year: 'numeric',
                            timeZone: 'UTC',
                          })}
                        </td>
                        <td>
                          <Badge value={i.status} />
                        </td>
                        <td className="numeric amount">{money(i.total)}</td>
                        <td>
                          <Link
                            className="icon-button"
                            aria-label={`View invoice ${i.id.slice(0, 8)}`}
                            href="/invoices"
                          >
                            <ArrowUpRight size={15} />
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <Empty>No invoices yet. Completed billing months are invoiced automatically.</Empty>
            )}
          </section>
        </>
      )}
    </>
  );
}
