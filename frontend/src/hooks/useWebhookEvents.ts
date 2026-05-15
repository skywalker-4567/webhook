import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';
import { webhookService, type WebhookEventParams } from '../services/webhookService';

export function useWebhookEvents(params: WebhookEventParams) {
  return useQuery({
    queryKey: queryKeys.webhooks.events(params as Record<string, unknown>),
    queryFn: () => webhookService.getEvents(params),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });
}

export function useWebhookStats() {
  return useQuery({
    queryKey: queryKeys.webhooks.stats,
    queryFn: () => webhookService.getStats(),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });
}