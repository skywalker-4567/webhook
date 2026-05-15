import { useState } from 'react';
import { useReconciliation } from '../hooks/useReconciliation';
import Skeleton from '../components/Skeleton';
import ErrorBanner from '../components/ErrorBanner';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

export default function Reconciliation() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useReconciliation(page, PAGE_SIZE);

  return (
    <div className="space-y-4">
      {isError && (
        <ErrorBanner message="Failed to load reconciliation logs." onRetry={refetch} />
      )}

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <table>
          <thead>
            <tr>
              <th>Payment ID</th>
              <th>Internal Status</th>
              <th>Gateway Status</th>
              <th>Action Taken</th>
              <th>Skip Reason</th>
              <th>Reason</th>
              <th>Reconciled At</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <Skeleton rows={PAGE_SIZE} cols={7} />
            ) : !data?.items.length ? (
              <EmptyState cols={7} message="No reconciliation records." />
            ) : (
              data.items.map((r) => (
                <tr key={r.id}>
                  <td className="font-mono text-amber-400 text-xs">{r.paymentId}</td>
                  <td>
                    <span className="badge badge-blue">{r.internalStatus}</span>
                  </td>
                  <td>
                    <span className={
                      r.gatewayStatus === r.internalStatus
                        ? 'badge badge-green'
                        : 'badge badge-red'
                    }>
                      {r.gatewayStatus}
                    </span>
                  </td>
                  <td>
                    <span className={
                      r.actionTaken === 'CORRECTED'
                        ? 'badge badge-amber'
                        : 'badge badge-gray'
                    }>
                      {r.actionTaken}
                    </span>
                  </td>
                  <td className="text-gray-400 text-xs">{r.skipReason ?? '—'}</td>
                  <td className="text-gray-400 text-xs max-w-xs truncate">
                    {r.reason ?? '—'}
                  </td>
                  <td className="text-gray-400 text-xs">
                    {new Date(r.reconciledAt).toLocaleString()}
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