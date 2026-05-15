import { useState } from 'react';
import { useLedger, useAccountBalance } from '../hooks/useLedger';
import Skeleton from '../components/Skeleton';
import ErrorBanner from '../components/ErrorBanner';
import EmptyState from '../components/EmptyState';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 50;
const ACCOUNT_TYPES = ['', 'CUSTOMER', 'MERCHANT', 'GATEWAY'];

function formatAmount(amount: number) {
  return (amount / 100).toFixed(2);
}

export default function Ledger() {
  const [page, setPage]               = useState(0);
  const [paymentId, setPaymentId]     = useState('');
  const [accountType, setAccountType] = useState('');
  const [from, setFrom]               = useState('');
  const [to, setTo]                   = useState('');
  const [applied, setApplied]         = useState<{
    paymentId?: string;
    accountType?: string;
    from?: string;
    to?: string;
    page: number;
    size: number;
  }>({ page: 0, size: PAGE_SIZE });

  function handleSearch() {
    setPage(0);
    setApplied({
      paymentId:   paymentId   || undefined,
      accountType: accountType || undefined,
      from:        from        || undefined,
      to:          to          || undefined,
      page: 0,
      size: PAGE_SIZE,
    });
  }

  const { data, isLoading, isError, refetch } = useLedger({
    ...applied,
    page,
  });

  const { data: balance } = useAccountBalance(applied.accountType ?? '');

  const imbalanced = data && data.totalDebit !== data.totalCredit;

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 space-y-3">
        <div className="flex flex-wrap gap-3">
          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-400">Payment ID</label>
            <input
              value={paymentId}
              onChange={(e) => setPaymentId(e.target.value)}
              placeholder="pay_xxx"
              className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                         rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500 w-48"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-400">Account Type</label>
            <select
              value={accountType}
              onChange={(e) => setAccountType(e.target.value)}
              className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                         rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500"
            >
              {ACCOUNT_TYPES.map((t) => (
                <option key={t} value={t}>{t || 'Any'}</option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-400">From (ISO)</label>
            <input
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              placeholder="2024-01-01T00:00:00Z"
              className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                         rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500 w-48"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-400">To (ISO)</label>
            <input
              value={to}
              onChange={(e) => setTo(e.target.value)}
              placeholder="2024-12-31T23:59:59Z"
              className="bg-gray-800 border border-gray-700 text-gray-200 text-sm
                         rounded-lg px-3 py-1.5 focus:outline-none focus:border-amber-500 w-48"
            />
          </div>

          <div className="flex items-end">
            <button
              onClick={handleSearch}
              className="px-4 py-1.5 rounded-lg bg-amber-500 hover:bg-amber-400
                         text-gray-950 text-sm font-semibold transition-colors"
            >
              Search
            </button>
          </div>
        </div>

        {balance && (
          <div className="text-xs text-gray-400">
            Current balance ({balance.accountType}):{' '}
            <span className="text-gray-100 font-mono">
              {formatAmount(balance.balance)} {balance.currency}
            </span>
          </div>
        )}
      </div>

      {/* Summary */}
      {data && (
        <div className="flex gap-4 text-sm">
          <div className="bg-gray-900 border border-gray-800 rounded-lg px-4 py-2 flex gap-2">
            <span className="text-gray-400">Total DR</span>
            <span className="text-red-400 font-mono">{formatAmount(data.totalDebit)}</span>
          </div>
          <div className="bg-gray-900 border border-gray-800 rounded-lg px-4 py-2 flex gap-2">
            <span className="text-gray-400">Total CR</span>
            <span className="text-green-400 font-mono">{formatAmount(data.totalCredit)}</span>
          </div>
        </div>
      )}

      {imbalanced && (
        <div className="px-4 py-3 rounded-lg bg-amber-950 border border-amber-700
                        text-amber-300 text-sm">
          ⚠ Ledger imbalance on this page: debit ≠ credit. Note: this check
          is page-scoped — a split across page boundaries may cause false positives.
        </div>
      )}

      {isError && (
        <ErrorBanner message="Failed to load ledger entries." onRetry={refetch} />
      )}

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <table>
          <thead>
            <tr>
              <th>Ref</th>
              <th>Type</th>
              <th>Entry</th>
              <th>Account</th>
              <th>Amount</th>
              <th>Currency</th>
              <th>Created At</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <Skeleton rows={10} cols={7} />
            ) : !data?.entries.length ? (
              <EmptyState cols={7} message="No ledger entries. Use filters above to search." />
            ) : (
              data.entries.map((e) => (
                <tr key={e.id}>
                  <td className="font-mono text-xs text-gray-400 max-w-xs truncate">
                    {e.transactionRef}
                  </td>
                  <td className="text-xs">{e.transactionType}</td>
                  <td>
                    <span className={
                      e.entryType === 'DEBIT' ? 'badge badge-red' : 'badge badge-green'
                    }>
                      {e.entryType}
                    </span>
                  </td>
                  <td className="text-xs">{e.accountType}</td>
                  <td className={`font-mono font-medium ${
                    e.entryType === 'DEBIT' ? 'text-red-400' : 'text-green-400'
                  }`}>
                    {formatAmount(e.amount)}
                  </td>
                  <td>{e.currency}</td>
                  <td className="text-gray-400 text-xs">
                    {new Date(e.createdAt).toLocaleString()}
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