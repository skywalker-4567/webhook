import { useParams, useNavigate } from 'react-router-dom';
import { usePayment } from '../hooks/usePayments';
import { useFraudChecks } from '../hooks/useFraudChecks';
import { useLedger } from '../hooks/useLedger';
import { useAuditLogs, useVerifyChain } from '../hooks/useAudit';
import ErrorBanner from '../components/ErrorBanner';
import { useState } from 'react';

function Field({ label, value, mono = false }: {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-gray-500 uppercase tracking-wider">{label}</span>
      <span className={`text-sm text-gray-100 ${mono ? 'font-mono' : ''}`}>
        {value ?? '—'}
      </span>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-800">
        <h2 className="text-sm font-semibold text-gray-300">{title}</h2>
      </div>
      <div className="p-5">{children}</div>
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

export default function PaymentDetail() {
  const { paymentId = '' } = useParams<{ paymentId: string }>();
  const navigate = useNavigate();
  const [verifyResult, setVerifyResult] = useState<{
    result: 'VALID' | 'BROKEN';
    brokenAtSequence: number | null;
    message: string;
  } | null>(null);

  const { data: payment, isLoading, isError } = usePayment(paymentId);
  const { data: fraudChecks } = useFraudChecks(paymentId);
  const { data: ledger } = useLedger({ paymentId, page: 0, size: 50 });
  const { data: auditLogs } = useAuditLogs(paymentId, 'PAYMENT', 0, 20);
  const { mutate: verifyChain, isPending: verifying } = useVerifyChain();

  function handleVerify() {
    verifyChain(
      { entityType: 'PAYMENT', entityId: paymentId },
      {
        onSuccess: (res) => setVerifyResult(res),
        onError: () =>
          setVerifyResult({
            result: 'BROKEN',
            brokenAtSequence: null,
            message: 'Verification failed.',
          }),
      }
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-48 bg-gray-800 rounded animate-pulse" />
        <div className="h-40 bg-gray-900 border border-gray-800 rounded-xl animate-pulse" />
      </div>
    );
  }

  if (isError || !payment) {
    return (
      <ErrorBanner
        message={`Payment "${paymentId}" not found.`}
        onRetry={() => navigate(-1)}
      />
    );
  }

  return (
    <div className="space-y-5">
      {/* Back */}
      <button
        onClick={() => navigate('/payments')}
        className="text-xs text-gray-400 hover:text-gray-200 transition-colors"
      >
        ← Back to Payments
      </button>

      {/* Payment Card */}
      <Section title="Payment Details">
        <div className="grid grid-cols-2 gap-x-8 gap-y-4 sm:grid-cols-3 lg:grid-cols-4">
          <Field label="Payment ID"     value={payment.paymentId}    mono />
          <Field label="Order ID"       value={payment.orderId}      mono />
          <Field label="Status"         value={statusBadge(payment.status)} />
          <Field label="Amount"
                 value={`${(payment.amount / 100).toFixed(2)} ${payment.currency}`} />
          <Field label="Method"         value={payment.method} />
          <Field label="Captured At"
                 value={payment.capturedAt
                   ? new Date(payment.capturedAt).toLocaleString()
                   : null} />
          <Field label="Retry Count"    value={payment.retryCount} />
          <Field label="Correlation ID" value={payment.correlationId} mono />
        </div>
      </Section>

      {/* Fraud Section */}
      <Section title="Fraud Checks">
        {!fraudChecks?.length ? (
          <p className="text-sm text-gray-500">No fraud checks recorded.</p>
        ) : (
          <div className="space-y-3">
            {fraudChecks.map((f) => (
              <div
                key={f.id}
                className={`rounded-lg border p-4 ${
                  f.isFraud
                    ? 'bg-red-950/40 border-red-800'
                    : 'bg-gray-800 border-gray-700'
                }`}
              >
                <div className="flex items-center gap-3 mb-2">
                  <span className={f.isFraud ? 'badge badge-red' : 'badge badge-green'}>
                    {f.isFraud ? '🚨 FRAUD' : '✅ CLEAN'}
                  </span>
                  <span className="text-xs text-gray-400">{f.eventType}</span>
                  <span className="text-xs text-gray-500">{f.paymentStatus}</span>
                </div>

                {f.triggeredRules.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-2">
                    {f.triggeredRules.map((rule) => (
                      <span key={rule} className="badge badge-amber">{rule}</span>
                    ))}
                  </div>
                )}

                {f.mlFraudScore != null && (
                  <div className="text-xs text-gray-400">
                    ML Score:{' '}
                    <span className="font-mono text-gray-200">
                      {f.mlFraudScore.toFixed(4)}
                    </span>
                    {f.mlIsAnomaly != null && (
                      <span className={`ml-2 badge ${
                        f.mlIsAnomaly ? 'badge-red' : 'badge-green'
                      }`}>
                        {f.mlIsAnomaly ? 'ANOMALY' : 'NORMAL'}
                      </span>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* Ledger Entries */}
      <Section title="Ledger Entries">
        {!ledger?.entries.length ? (
          <p className="text-sm text-gray-500">No ledger entries for this payment.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Entry</th>
                <th>Account</th>
                <th>Amount</th>
                <th>Currency</th>
                <th>Created At</th>
              </tr>
            </thead>
            <tbody>
              {ledger.entries.map((e) => (
                <tr key={e.id}>
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
                    {(e.amount / 100).toFixed(2)}
                  </td>
                  <td>{e.currency}</td>
                  <td className="text-gray-400 text-xs">
                    {new Date(e.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>

      {/* Audit Log */}
      <Section title="Audit Log">
        <div className="flex items-center gap-3 mb-4">
          <button
            onClick={handleVerify}
            disabled={verifying}
            className="px-3 py-1.5 rounded-lg bg-gray-700 hover:bg-gray-600
                       text-gray-200 text-xs font-medium transition-colors
                       disabled:opacity-40"
          >
            {verifying ? 'Verifying…' : '🔐 Verify Chain'}
          </button>

          {verifyResult && (
            <span className={`text-xs ${
              verifyResult.result === 'VALID' ? 'text-green-400' : 'text-red-400'
            }`}>
              {verifyResult.result === 'VALID' ? '✅' : '❌'} {verifyResult.message}
              {verifyResult.brokenAtSequence != null &&
                ` (seq #${verifyResult.brokenAtSequence})`}
            </span>
          )}
        </div>

        {!auditLogs?.items.length ? (
          <p className="text-sm text-gray-500">No audit entries.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Seq</th>
                <th>Action</th>
                <th>Actor</th>
                <th>Hash</th>
                <th>Created At</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.items.map((a) => (
                <tr key={a.id}>
                  <td className="font-mono text-xs text-gray-400">{a.sequenceNum}</td>
                  <td><span className="badge badge-blue">{a.action}</span></td>
                  <td className="text-xs">{a.actor}</td>
                  <td className="font-mono text-xs text-green-400 max-w-xs truncate">
                    {a.currentHash}
                  </td>
                  <td className="text-gray-400 text-xs">
                    {new Date(a.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>
    </div>
  );
}