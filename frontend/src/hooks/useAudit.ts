import { useMutation, useQuery } from '@tanstack/react-query';
import { queryKeys } from '../lib/queryKeys';
import { auditService } from '../services/auditService';
import type { AuditVerifyResponse } from '../types';

export function useAuditLogs(
  entityId: string,
  entityType: string,
  page: number,
  size: number
) {
  return useQuery({
    queryKey: queryKeys.audit.logs(entityId, entityType, page, size),
    queryFn: () => auditService.getLogs(entityId, entityType, page, size),
    staleTime: 30_000,
    enabled: entityId.length > 0,
  });
}

export function useVerifyChain() {
  return useMutation<AuditVerifyResponse, Error, { entityType: string; entityId: string }>({
    mutationFn: ({ entityType, entityId }) =>
      auditService.verifyChain(entityType, entityId),
  });
}