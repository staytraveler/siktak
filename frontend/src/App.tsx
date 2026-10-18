import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import RequireAuth from './components/RequireAuth'
import AdminDemoPage from './pages/admin/AdminDemoPage'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminSlotsPage from './pages/admin/AdminSlotsPage'
import ApplicationDetailPage from './pages/app/ApplicationDetailPage'
import NotificationsPage from './pages/app/NotificationsPage'
import ParticipantHomePage from './pages/app/ParticipantHomePage'
import ProfilePage from './pages/app/ProfilePage'
import LoginPage from './pages/LoginPage'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/app', element: <RequireAuth><ParticipantHomePage /></RequireAuth> },
  { path: '/app/notifications', element: <RequireAuth><NotificationsPage /></RequireAuth> },
  { path: '/app/applications/:id', element: <RequireAuth><ApplicationDetailPage /></RequireAuth> },
  { path: '/app/profile', element: <RequireAuth><ProfilePage /></RequireAuth> },
  { path: '/admin', element: <RequireAuth role="OPERATOR"><AdminHomePage /></RequireAuth> },
  { path: '/admin/slots', element: <RequireAuth role="OPERATOR"><AdminSlotsPage /></RequireAuth> },
  { path: '/admin/demo', element: <RequireAuth role="OPERATOR"><AdminDemoPage /></RequireAuth> },
])

export default function App() {
  return <RouterProvider router={router} />
}
