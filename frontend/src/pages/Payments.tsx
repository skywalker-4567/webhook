import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePayments } from '../hooks/usePayments';
import Skeleton from '../components/Skeleton';
import ErrorBanner from '../components/ErrorBanner';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';

const STATUS_OPTIONS = ['', 'CAPTURED', 'FAILED', 'AUTHORIZED', 'REFUNDED'];
const PAGE_SIZE = 20;

function statusBadge(status: string) {
  const map: Record<string, string> = {
    CAPTURED:   'badge badge-green',
    FAILED:     'badge badge-red',
    AUTHORIZED: 'badge badge-amber',
    REFUNDED:   'badge badge-blue',
  };
  return <span className={map[status] ?? 'badge badge-gray'}>{status}</span>;
}

function formatAmount(amount: number, currency: string) {
  return `${(amount / 100).toFixed(2)} ${currency}`;
}

function formatDate(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}

export default function Payments() {
  const navigate = useNavigate();
  const [page, setPage]     = useState(0);
  const [status, setStatus] = useState('');

  const { data, isLoading, isError, refetch } = usePayments(
    page,
    PAGE_SIZE,
    status || undefined
  );

  function handleStatusChange(val: string) {
    setStatus(val);
    setPage(0);
  }

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex items-center gap-3">
        <label className="text-xs text-gray-400 font-medium">Status</label>
        <select
          value={status}
          onChange={(e) => handleStatusChange(e.target.value)}
          className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                     rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{s || 'All'}</option>
          ))}
        </select>
      </div>

      {isError && <ErrorBanner message="Failed to load payments." onRetry={refetch} />}

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <table>
          <thead>
            <tr>
              <th>Payment ID</th>
              <th>Order ID</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Method</th>
              <th>Captured At</th>
              <th>Retries</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <Skeleton rows={PAGE_SIZE} cols={7} />
            ) : !data?.items.length ? (
              <EmptyState cols={7} message="No payments found." />
            ) : (
              data.items.map((p) => (
                <tr
                  key={p.paymentId}
                  onClick={() => navigate(`/payments/${p.paymentId}`)}
                  className="cursor-pointer"
                >
                  <td className="font-mono text-amber-400">{p.paymentId}</td>
                  <td className="font-mono text-gray-400 text-xs">{p.orderId ?? '—'}</td>
                  <td>{formatAmount(p.amount, p.currency)}</td>
                  <td>{statusBadge(p.status)}</td>
                  <td>{p.method ?? '—'}</td>
                  <td className="text-gray-400 text-xs">{formatDate(p.capturedAt)}</td>
                  <td className="text-center">{p.retryCount}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>

        {data && (
          <div className="px-4 border-t border-gray-800">
            <Pagination
              currentPage={page}
              totalPages={data.totalPages}
              onPrev={() => setPage((p) => Math.max(0, p - 1))}
              onNext={() => setPage((p) => p + 1)}
            />
          </div>
        )}
      </div>
    </div>
  );
}