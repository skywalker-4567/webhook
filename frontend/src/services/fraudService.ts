import { api } from './api';
import type { FraudCheck } from '../types';

export const fraudService = {
  getFraudChecks: async (paymentId: string): Promise<FraudCheck[]> => {
    const { data } = await api.get<FraudCheck[]>('/fraud-checks', {
      params: { paymentId },
    });
    return data;
  },
};