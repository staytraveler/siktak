import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import AdminLayout from './components/AdminLayout'
import RequireAuth from './components/RequireAuth'
import AdminExceptionsPage from './pages/admin/AdminExceptionsPage'
import AdminMatchingPage from './pages/admin/AdminMatchingPage'
import AdminTableDetailPage from './pages/admin/AdminTableDetailPage'
import AdminDemoPage from './pages/admin/AdminDemoPage'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminSlotsPage from './pages/admin/AdminSlotsPage'
import AdminReportsPage from './pages/admin/AdminReportsPage'
import ApplicationDetailPage from './pages/app/ApplicationDetailPage'
import NotificationsPage from './pages/app/NotificationsPage'
import HistoryPage from './pages/app/HistoryPage'
import ParticipantHomePage from './pages/app/ParticipantHomePage'
import ProfilePage from './pages/app/ProfilePage'
import LoginPage from './pages/LoginPage'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/app', element: <RequireAuth><ParticipantHomePage /></RequireAuth> },
  { path: '/app/notifications', element: <RequireAuth><NotificationsPage /></RequireAuth> },
  { path: '/app/applications/:id', element: <RequireAuth><ApplicationDetailPage /></RequireAuth> },
  { path: '/app/history', element: <RequireAuth><HistoryPage /></RequireAuth> },
  { path: '/app/profile', element: <RequireAuth><ProfilePage /></RequireAuth> },
  {
    path: '/admin',
    element: <RequireAuth role="OPERATOR"><AdminLayout /></RequireAuth>,
    children: [
      { index: true, element: <AdminHomePage /> },
      { path: 'exceptions', element: <AdminExceptionsPage /> },
      { path: 'matching', element: <AdminMatchingPage /> },
      { path: 'tables/:id', element: <AdminTableDetailPage /> },
      { path: 'slots', element: <AdminSlotsPage /> },
      { path: 'reports', element: <AdminReportsPage /> },
      { path: 'demo', element: <AdminDemoPage /> },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
