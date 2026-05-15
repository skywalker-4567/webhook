import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useWebhookEvents } from '../hooks/useWebhookEvents';
import Skeleton from '../components/Skeleton';
import ErrorBanner from '../components/ErrorBanner';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

function statusBadge(status: string) {
  const map: Record<string, string> = {
    PROCESSED:  'badge badge-green',
    FAILED:     'badge badge-red',
    PROCESSING: 'badge badge-amber',
    RECEIVED:   'badge badge-blue',
  };
  return <span className={map[status] ?? 'badge badge-gray'}>{status}</span>;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString();
}

export default function Webhooks() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useWebhookEvents({
    page,
    size: PAGE_SIZE,
  });

  function handleRowClick(paymentId: string | null) {
    if (!paymentId || paymentId === 'UNKNOWN') return;
    navigate(`/payments/${paymentId}`);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-gray-500">Auto-refreshes every 30s</p>
        <button
          onClick={() => refetch()}
          className="text-xs text-amber-400 hover:text-amber-300 transition-colors"
        >
          ↻ Refresh now
        </button>
      </div>

      {isError && (
        <ErrorBanner message="Failed to load webhook events." onRetry={refetch} />
      )}

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <table>
          <thead>
            <tr>
              <th>Event ID</th>
              <th>Event Type</th>
              <th>Payment ID</th>
              <th>Status</th>
              <th>Retries</th>
              <th>Received At</th>
              <th>Failure Reason</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <Skeleton rows={PAGE_SIZE} cols={7} />
            ) : !data?.items.length ? (
              <EmptyState cols={7} message="No webhook events found." />
            ) : (
              data.items.map((e) => (
                <tr
                  key={e.eventId}
                  onClick={() => handleRowClick(e.paymentId)}
                  className={
                    e.paymentId && e.paymentId !== 'UNKNOWN'
                      ? 'cursor-pointer'
                      : ''
                  }
                >
                  <td className="font-mono text-xs text-gray-400">{e.eventId}</td>
                  <td className="text-xs">{e.eventType}</td>
                  <td className="font-mono text-xs text-amber-400">
                    {e.paymentId ?? '—'}
                  </td>
                  <td>{statusBadge(e.status)}</td>
                  <td className="text-center">{e.retryCount}</td>
                  <td className="text-gray-400 text-xs">{formatDate(e.receivedAt)}</td>
                  <td className="text-red-400 text-xs truncate max-w-xs">
                    {e.failureReason ?? '—'}
                  </td>
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