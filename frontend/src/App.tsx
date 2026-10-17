import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import RequireAuth from './components/RequireAuth'
import AdminDemoPage from './pages/admin/AdminDemoPage'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminSlotsPage from './pages/admin/AdminSlotsPage'
import ApplicationDetailPage from './pages/app/ApplicationDetailPage'
import ParticipantHomePage from './pages/app/ParticipantHomePage'
import LoginPage from './pages/LoginPage'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/app', element: <RequireAuth><ParticipantHomePage /></RequireAuth> },
  { path: '/app/applications/:id', element: <RequireAuth><ApplicationDetailPage /></RequireAuth> },
  { path: '/admin', element: <RequireAuth role="OPERATOR"><AdminHomePage /></RequireAuth> },
  { path: '/admin/slots', element: <RequireAuth role="OPERATOR"><AdminSlotsPage /></RequireAuth> },
  { path: '/admin/demo', element: <RequireAuth role="OPERATOR"><AdminDemoPage /></RequireAuth> },
])

export default function App() {
  return <RouterProvider router={router} />
}
