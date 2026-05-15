import { Routes, Route, Navigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import PrivateRoute from './components/PrivateRoute';
import Layout from './layout/Layout';
import LoginPage from './pages/LoginPage';

const Dashboard      = lazy(() => import('./pages/Dashboard'));
const Payments       = lazy(() => import('./pages/Payments'));
const PaymentDetail  = lazy(() => import('./pages/PaymentDetail'));
const Webhooks       = lazy(() => import('./pages/Webhooks'));
const Ledger         = lazy(() => import('./pages/Ledger'));
const Reconciliation = lazy(() => import('./pages/Reconciliation'));
const Audit          = lazy(() => import('./pages/Audit'));

const Loading = () => (
  <div className="flex items-center justify-center h-64 text-gray-400">
    Loading…
  </div>
);

export default function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route element={<PrivateRoute />}>
          <Route element={<Layout />}>
            <Route path="/"                    element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard"           element={<Dashboard />} />
            <Route path="/payments"            element={<Payments />} />
            <Route path="/payments/:paymentId" element={<PaymentDetail />} />
            <Route path="/webhooks"            element={<Webhooks />} />
            <Route path="/ledger"              element={<Ledger />} />
            <Route path="/reconciliation"      element={<Reconciliation />} />
            <Route path="/audit"               element={<Audit />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  );
}