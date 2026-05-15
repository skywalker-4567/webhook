import { useWebhookStats } from '../hooks/useWebhookEvents';
import { usePayments } from '../hooks/usePayments';
import Skeleton from '../components/Skeleton';
import ErrorBanner from '../components/ErrorBanner';
import EmptyState from '../components/EmptyState';

interface StatCardProps {
  label: string;
  value: number | undefined;
  accent?: string;
}

function StatCard({ label, value, accent = 'text-gray-100' }: StatCardProps) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">
        {label}
      </p>
      <p className={`text-3xl font-bold ${accent}`}>
        {value ?? '—'}
      </p>
    </div>
  );
}

function statusBadge(status: string) {
  const map: Record<string, string> = {
    CAPTURED:   'badge badge-green',
    FAILED:     'badge badge-red',
    AUTHORIZED: 'badge badge-amber',
    REFUNDED:   'badge badge-blue',
  };
  return <span className={map[status] ?? 'badge badge-gray'}>{status}</span>;
}

export default function Dashboard() {
  const { data: stats, isLoading: statsLoading, isError: statsError, refetch: refetchStats } =
    useWebhookStats();
  const { data: payments, isLoading: paymentsLoading, isError: paymentsError } =
    usePayments(0, 5);

  return (
    <div className="space-y-8">
      {/* Stat Cards */}
      <section>
        <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-4">
          Webhook Overview
        </h2>
        {statsError && (
          <ErrorBanner message="Failed to load stats." onRetry={refetchStats} />
        )}
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {statsLoading ? (
            Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
                <div className="h-3 w-20 bg-gray-800 rounded animate-pulse mb-3" />
                <div className="h-8 w-16 bg-gray-800 rounded animate-pulse" />
              </div>
            ))
          ) : (
            <>
              <StatCard label="Total"      value={stats?.total}      />
              <StatCard label="Processed"  value={stats?.processed}  accent="text-green-400" />
              <StatCard label="Failed"     value={stats?.failed}     accent="text-red-400"   />
              <StatCard label="Processing" value={stats?.processing} accent="text-amber-400" />
            </>
          )}
        </div>
      </section>

      {/* Recent Payments */}
      <section>
        <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-4">
          Recent Payments
        </h2>
        {paymentsError && <ErrorBanner message="Failed to load payments." />}
        <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
          <table>
            <thead>
              <tr>
                <th>Payment ID</th>
                <th>Amount</th>
                <th>Currency</th>
                <th>Status</th>
                <th>Method</th>
              </tr>
            </thead>
            <tbody>
              {paymentsLoading ? (
                <Skeleton rows={5} cols={5} />
              ) : !payments?.items.length ? (
                <EmptyState cols={5} message="No recent payments." />
              ) : (
                payments.items.map((p) => (
                  <tr key={p.paymentId}>
                    <td className="font-mono text-amber-400">{p.paymentId}</td>
                    <td>{(p.amount / 100).toFixed(2)}</td>
                    <td>{p.currency}</td>
                    <td>{statusBadge(p.status)}</td>
                    <td className="text-gray-400">{p.method ?? '—'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}