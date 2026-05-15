import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';
import { paymentService } from '../services/paymentService';

export function usePayments(page: number, size: number, status?: string) {
  return useQuery({
    queryKey: queryKeys.payments.list(page, size, status),
    queryFn: () => paymentService.getPayments(page, size, status),
    staleTime: 30_000,
  });
}

export function usePayment(paymentId: string) {
  return useQuery({
    queryKey: queryKeys.payments.detail(paymentId),
    queryFn: () => paymentService.getPaymentById(paymentId),
    staleTime: 30_000,
    enabled: paymentId.length > 0,
  });
}