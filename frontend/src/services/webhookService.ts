import { api } from './api';
import type { PageResponse, WebhookEvent, WebhookStats } from '../types';

export interface WebhookEventParams {
  paymentId?: string;
  status?: string;
  eventType?: string;
  page?: number;
  size?: number;
}

export const webhookService = {
  getEvents: async (
    params: WebhookEventParams
  ): Promise<PageResponse<WebhookEvent>> => {
    const { data } = await api.get<PageResponse<WebhookEvent>>(
      '/webhooks/events',
      { params }
    );
    return data;
  },

  getStats: async (): Promise<WebhookStats> => {
    const { data } = await api.get<WebhookStats>('/webhooks/stats');
    return data;
  },
};