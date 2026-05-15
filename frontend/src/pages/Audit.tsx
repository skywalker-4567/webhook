import { useState } from 'react';
import { useAuditLogs, useVerifyChain } from '../hooks/useAudit';
import Skeleton from '../components/Skeleton';
import ErrorBanner from '../components/ErrorBanner';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;
const ENTITY_TYPES = ['PAYMENT', 'ORDER', 'REFUND', 'WEBHOOK'];

export default function Audit() {
  const [page, setPage]             = useState(0);
  const [entityId, setEntityId]     = useState('');
  const [entityType, setEntityType] = useState('PAYMENT');
  const [appliedId, setAppliedId]   = useState('');
  const [appliedType, setAppliedType] = useState('PAYMENT');
  const [verifyResult, setVerifyResult] = useState<{
    result: 'VALID' | 'BROKEN';
    brokenAtSequence: number | null;
    message: string;
  } | null>(null);

  const { data, isLoading, isError, refetch } = useAuditLogs(
    appliedId,
    appliedType,
    page,
    PAGE_SIZE
  );

  const { mutate: verifyChain, isPending: verifying } = useVerifyChain();

  function handleSearch() {
    setPage(0);
    setAppliedId(entityId.trim());
    setAppliedType(entityType);
    setVerifyResult(null);
  }

  function handleVerify() {
    verifyChain(
      { entityType: appliedType, entityId: appliedId },
      {
        onSuccess: (res) => setVerifyResult(res),
        onError: () =>
          setVerifyResult({
            result: 'BROKEN',
            brokenAtSequence: null,
            message: 'Verification request failed.',
          }),
      }
    );
  }

  return (
    <div className="space-y-4">
      {/* Controls */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
        <div className="flex flex-wrap gap-3 items-end">
          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-400">Entity Type</label>
            <select
              value={entityType}
              onChange={(e) => setEntityType(e.target.value)}
              className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                         rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500"
            >
              {ENTITY_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-400">Entity ID</label>
            <input
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              placeholder="pay_xxx or order UUID"
              className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                         rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500 w-64"
            />
          </div>

          <button
            onClick={handleSearch}
            disabled={!entityId.trim()}
            className="px-4 py-1.5 rounded-lg bg-amber-500 hover:bg-amber-400
                       text-gray-950 text-sm font-semibold transition-colors
                       disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Search
          </button>

          {appliedId && (
            <button
              onClick={handleVerify}
              disabled={verifying}
              className="px-4 py-1.5 rounded-lg bg-gray-700 hover:bg-gray-600
                         text-gray-200 text-sm font-medium transition-colors
                         disabled:opacity-40"
            >
              {verifying ? 'Verifying…' : '🔐 Verify Chain'}
            </button>
          )}
        </div>
      </div>

      {/* Verify result */}
      {verifyResult && (
        <div className={`px-4 py-3 rounded-lg border text-sm ${
          verifyResult.result === 'VALID'
            ? 'bg-green-950 border-green-700 text-green-300'
            : 'bg-red-950 border-red-700 text-red-300'
        }`}>
          {verifyResult.result === 'VALID' ? '✅' : '❌'}{' '}
          {verifyResult.message}
          {verifyResult.brokenAtSequence != null && (
            <span className="ml-2 font-mono text-xs">
              (seq #{verifyResult.brokenAtSequence})
            </span>
          )}
        </div>
      )}

      {isError && <ErrorBanner message="Failed to load audit logs." onRetry={refetch} />}

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <table>
          <thead>
            <tr>
              <th>Seq</th>
              <th>Action</th>
              <th>Actor</th>
              <th>Previous Hash</th>
              <th>Current Hash</th>
              <th>Created At</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <Skeleton rows={PAGE_SIZE} cols={6} />
            ) : !data?.items.length ? (
              <EmptyState cols={6} message="No audit logs. Enter an entity ID above." />
            ) : (
              data.items.map((a) => (
                <tr key={a.id}>
                  <td className="font-mono text-gray-400 text-xs">{a.sequenceNum}</td>
                  <td>
                    <span className="badge badge-blue">{a.action}</span>
                  </td>
                  <td className="text-xs">{a.actor}</td>
                  <td className="font-mono text-xs text-gray-500 max-w-xs truncate">
                    {a.previousHash}
                  </td>
                  <td className={`font-mono text-xs max-w-xs truncate ${
                    a.currentHash === 'PENDING'
                      ? 'text-amber-400'
                      : 'text-green-400'
                  }`}>
                    {a.currentHash}
                  </td>
                  <td className="text-gray-400 text-xs">
                    {new Date(a.createdAt).toLocaleString()}
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