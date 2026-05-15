import { api } from './api';
import type { AccountBalance, LedgerResponse } from '../types';

export interface LedgerParams {
  paymentId?: string;
  orderId?: string;
  transactionId?: string;
  accountType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const ledgerService = {
  getLedgerEntries: async (params: LedgerParams): Promise<LedgerResponse> => {
    const { data } = await api.get<LedgerResponse>('/ledger', { params });
    return data;
  },

  getAccountBalance: async (accountType: string): Promise<AccountBalance> => {
    const { data } = await api.get<AccountBalance>(
      `/ledger/accounts/${accountType}/balance`
    );
    return data;
  },
};