import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';
import { fraudService } from '../services/fraudService';

export function useFraudChecks(paymentId: string) {
  return useQuery({
    queryKey: queryKeys.fraud.checks(paymentId),
    queryFn: () => fraudService.getFraudChecks(paymentId),
    staleTime: 30_000,
    enabled: paymentId.length > 0,
  });
}