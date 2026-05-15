import { api } from './api';
import type { PageResponse, Payment } from '../types';

export const paymentService = {
  getPayments: async (
    page: number,
    size: number,
    status?: string
  ): Promise<PageResponse<Payment>> => {
    const params: Record<string, unknown> = { page, size };
    if (status) params.status = status;
    const { data } = await api.get<PageResponse<Payment>>('/payments', { params });
    return data;
  },

  getPaymentById: async (id: string): Promise<Payment> => {
    const { data } = await api.get<Payment>(`/payments/${id}`);
    return data;
  },
};