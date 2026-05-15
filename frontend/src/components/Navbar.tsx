import { useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../services/api';

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':      'Dashboard',
  '/payments':       'Payments',
  '/webhooks':       'Webhooks',
  '/ledger':         'Ledger',
  '/reconciliation': 'Reconciliation',
  '/audit':          'Audit',
};

function useHealthCheck() {
  return useQuery({
    queryKey: ['health'],
    queryFn: async () => {
      const { data } = await api.get('/actuator/health');
      return data;
    },
    refetchInterval: 30_000,
    retry: false,
  });
}

export default function Navbar() {
  const { pathname } = useLocation();
  const { data: health, isError } = useHealthCheck();

  const title =
    Object.entries(PAGE_TITLES).find(([path]) =>
      pathname.startsWith(path)
    )?.[1] ?? 'Dashboard';

  const isUp = !isError && health?.status === 'UP';

  return (
    <header className="h-14 shrink-0 bg-gray-900 border-b border-gray-800
                       flex items-center justify-between px-6">
      <h1 className="text-gray-100 font-semibold text-base">{title}</h1>

      <div className="flex items-center gap-2 text-xs text-gray-400">
        <span
          className={[
            'w-2 h-2 rounded-full',
            isUp ? 'bg-green-400' : 'bg-red-500',
          ].join(' ')}
        />
        <span>{isUp ? 'API UP' : 'API DOWN'}</span>
      </div>
    </header>
  );
}