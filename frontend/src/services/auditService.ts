import { api } from './api';
import type { AuditLog, AuditVerifyResponse, PageResponse } from '../types';

export const auditService = {
  getLogs: async (
    entityId: string,
    entityType: string,
    page: number,
    size: number
  ): Promise<PageResponse<AuditLog>> => {
    const { data } = await api.get<PageResponse<AuditLog>>('/audit/logs', {
      params: { entityId, entityType, page, size },
    });
    return data;
  },

  verifyChain: async (
    entityType: string,
    entityId: string
  ): Promise<AuditVerifyResponse> => {
    const { data } = await api.get<AuditVerifyResponse>('/audit/verify', {
      params: { entityType, entityId },
    });
    return data;
  },
};