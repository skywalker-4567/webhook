export const queryKeys = {
  payments: {
    all: ['payments'] as const,
    list: (page: number, size: number, status?: string) =>
      ['payments', { page, size, status }] as const,
    detail: (id: string) => ['payments', id] as const,
  },

  webhooks: {
    events: (params: Record<string, unknown>) =>
      ['webhooks', 'events', params] as const,
    stats: ['webhooks', 'stats'] as const,
  },

  ledger: {
    entries: (params: Record<string, unknown>) =>
      ['ledger', 'entries', params] as const,
    balance: (accountType: string) =>
      ['ledger', 'balance', accountType] as const,
  },

  reconciliation: {
    list: (page: number, size: number) =>
      ['reconciliation', { page, size }] as const,
  },

  audit: {
    logs: (
      entityId: string,
      entityType: string,
      page: number,
      size: number
    ) => ['audit', 'logs', { entityId, entityType, page, size }] as const,
    verify: (entityType: string, entityId: string) =>
      ['audit', 'verify', { entityType, entityId }] as const,
  },

  fraud: {
    checks: (paymentId: string) => ['fraud', paymentId] as const,
  },

  orders: {
    detail: (id: string) => ['orders', id] as const,
  },
} as const;