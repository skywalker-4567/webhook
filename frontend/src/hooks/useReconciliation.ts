import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';
import { reconciliationService } from '../services/reconciliationService';

export function useReconciliation(page: number, size: number) {
  return useQuery({
    queryKey: queryKeys.reconciliation.list(page, size),
    queryFn: () => reconciliationService.getLogs(page, size),
    staleTime: 30_000,
  });
}