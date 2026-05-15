import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';
import { ledgerService, type LedgerParams } from '../services/ledgerService';

export function useLedger(params: LedgerParams) {
  return useQuery({
    queryKey: queryKeys.ledger.entries(params as Record<string, unknown>),
    queryFn: () => ledgerService.getLedgerEntries(params),
    staleTime: 30_000,
    enabled:
      params.paymentId != null ||
      params.orderId != null ||
      params.transactionId != null ||
      (params.accountType != null && params.from != null && params.to != null),
  });
}

export function useAccountBalance(accountType: string) {
  return useQuery({
    queryKey: queryKeys.ledger.balance(accountType),
    queryFn: () => ledgerService.getAccountBalance(accountType),
    staleTime: 30_000,
    enabled: accountType.length > 0,
  });
}