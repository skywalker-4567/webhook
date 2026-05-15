export type WebhookStatus = 'RECEIVED' | 'PROCESSING' | 'PROCESSED' | 'FAILED';

export interface PageResponse<T> {
  items: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

export interface Payment {
  paymentId: string;
  orderId: string | null;
  amount: number;
  currency: string;
  status: 'AUTHORIZED' | 'CAPTURED' | 'FAILED' | 'REFUNDED';
  method: string | null;
  capturedAt: string | null;
  retryCount: number;
  correlationId: string | null;
}

export interface WebhookEvent {
  eventId: string;
  eventType: string;
  paymentId: string | null;
  status: WebhookStatus;
  failureType: string | null;
  failureReason: string | null;
  receivedAt: string;
  processedAt: string | null;
  retryCount: number;
  correlationId: string | null;
}

export interface WebhookStats {
  total: number;
  processed: number;
  failed: number;
  processing: number;
  received: number;
}

export interface LedgerEntry {
  id: string;
  transactionRef: string;
  transactionType: string;
  entryType: 'DEBIT' | 'CREDIT';
  accountType: string;
  amount: number;
  currency: string;
  description: string | null;
  transactionId: string | null;
  createdAt: string;
  correlationId: string | null;
}

export interface LedgerResponse {
  entries: LedgerEntry[];
  totalDebit: number;
  totalCredit: number;
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

export interface AccountBalance {
  accountType: string;
  balance: number;
  currency: string;
  asOf: string;
}

export interface ReconciliationLog {
  id: string;
  paymentId: string;
  internalStatus: string;
  gatewayStatus: string;
  actionTaken: string;
  skipReason: string | null;
  reason: string | null;
  reconciledAt: string;
}

export interface AuditLog {
  id: string;
  sequenceNum: number;
  entityType: string;
  entityId: string;
  action: string;
  actor: string;
  oldValue: string | null;
  newValue: string | null;
  previousHash: string;
  currentHash: string;
  createdAt: string;
  correlationId: string | null;
}

export interface AuditVerifyResponse {
  result: 'VALID' | 'BROKEN';
  brokenAtSequence: number | null;
  message: string;
}

export interface FraudCheck {
  id: string;
  paymentId: string;
  eventType: string;
  paymentStatus: string;
  isFraud: boolean;
  triggeredRules: string[];
  mlFraudScore: number | null;
  mlIsAnomaly: boolean | null;
  createdAt: string;
}

export interface PaymentSummary {
  paymentId: string;
  method: string | null;
  capturedAt: string | null;
}

export interface Order {
  orderId: string;
  razorpayOrderId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: string;
  payment: PaymentSummary | null;
  createdAt: string;
}