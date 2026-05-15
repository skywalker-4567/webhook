import { api } from './api';
import type { PageResponse, ReconciliationLog } from '../types';

export const reconciliationService = {
  getLogs: async (
    page: number,
    size: number
  ): Promise<PageResponse<ReconciliationLog>> => {
    const { data } = await api.get<PageResponse<ReconciliationLog>>(
      '/reconciliation',
      { params: { page, size } }
    );
    return data;
  },
};