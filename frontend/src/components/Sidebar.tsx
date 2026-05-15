import { NavLink, useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';

interface NavItem {
  label: string;
  path: string;
  icon: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard',      path: '/dashboard',      icon: '📊' },
  { label: 'Payments',       path: '/payments',       icon: '💳' },
  { label: 'Webhooks',       path: '/webhooks',       icon: '🔔' },
  { label: 'Ledger',         path: '/ledger',         icon: '📒' },
  { label: 'Reconciliation', path: '/reconciliation', icon: '⚖️'  },
  { label: 'Audit',          path: '/audit',          icon: '🔍' },
];

export default function Sidebar() {
  const navigate = useNavigate();

  function handleLogout() {
    authService.logout();
    navigate('/login');
  }

  return (
    <aside className="w-60 shrink-0 bg-gray-900 border-r border-gray-800 flex flex-col">
      {/* Brand */}
      <div className="px-5 py-5 border-b border-gray-800">
        <span className="text-amber-400 font-bold text-lg tracking-tight">
          ⚡ Razorpay Ops
        </span>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              [
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                  : 'text-gray-400 hover:bg-gray-800 hover:text-gray-100',
              ].join(' ')
            }
          >
            <span className="text-base">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Logout */}
      <div className="px-3 py-4 border-t border-gray-800">
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm
                     font-medium text-gray-400 hover:bg-red-900/30 hover:text-red-400
                     transition-colors"
        >
          <span>🚪</span>
          Logout
        </button>
      </div>
    </aside>
  );
}