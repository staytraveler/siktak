import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import RequireAuth from './components/RequireAuth'
import AdminExceptionsPage from './pages/admin/AdminExceptionsPage'
import AdminMatchingPage from './pages/admin/AdminMatchingPage'
import AdminTableDetailPage from './pages/admin/AdminTableDetailPage'
import AdminDemoPage from './pages/admin/AdminDemoPage'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminSlotsPage from './pages/admin/AdminSlotsPage'
import AdminVenuesPage from './pages/admin/AdminVenuesPage'
import AdminSettingsPage from './pages/admin/AdminSettingsPage'
import AdminApplicantsPage from './pages/admin/AdminApplicantsPage'
import AdminFeedbackPage from './pages/admin/AdminFeedbackPage'
import AdminAttendancePage from './pages/admin/AdminAttendancePage'
import AdminMessagesPage from './pages/admin/AdminMessagesPage'
import AdminPaymentsPage from './pages/admin/AdminPaymentsPage'
import AdminReportsPage from './pages/admin/AdminReportsPage'
import ApplicationDetailPage from './pages/app/ApplicationDetailPage'
import NotificationsPage from './pages/app/NotificationsPage'
import HistoryPage from './pages/app/HistoryPage'
import ParticipantHomePage from './pages/app/ParticipantHomePage'
import ProfilePage from './pages/app/ProfilePage'
import SlotsPage from './pages/app/SlotsPage'
import TablePage from './pages/app/TablePage'
import LoginPage from './pages/LoginPage'
import SplashScreen from './components/SplashScreen'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/app', element: <RequireAuth><ParticipantHomePage /></RequireAuth> },
  { path: '/app/notifications', element: <RequireAuth><NotificationsPage /></RequireAuth> },
  { path: '/app/applications/:id', element: <RequireAuth><ApplicationDetailPage /></RequireAuth> },
  { path: '/app/applications/:id/table', element: <RequireAuth><TablePage /></RequireAuth> },
  { path: '/app/slots', element: <RequireAuth><SlotsPage /></RequireAuth> },
  { path: '/app/history', element: <RequireAuth><HistoryPage /></RequireAuth> },
  { path: '/app/profile', element: <RequireAuth><ProfilePage /></RequireAuth> },
  { path: '/admin', element: <RequireAuth role="OPERATOR"><AdminHomePage /></RequireAuth> },
  { path: '/admin/slots', element: <RequireAuth role="OPERATOR"><AdminSlotsPage /></RequireAuth> },
  { path: '/admin/venues', element: <RequireAuth role="OPERATOR"><AdminVenuesPage /></RequireAuth> },
  { path: '/admin/settings', element: <RequireAuth role="OPERATOR"><AdminSettingsPage /></RequireAuth> },
  { path: '/admin/applicants', element: <RequireAuth role="OPERATOR"><AdminApplicantsPage /></RequireAuth> },
  { path: '/admin/payments', element: <RequireAuth role="OPERATOR"><AdminPaymentsPage /></RequireAuth> },
  { path: '/admin/attendance', element: <RequireAuth role="OPERATOR"><AdminAttendancePage /></RequireAuth> },
  { path: '/admin/messages', element: <RequireAuth role="OPERATOR"><AdminMessagesPage /></RequireAuth> },
  { path: '/admin/feedback', element: <RequireAuth role="OPERATOR"><AdminFeedbackPage /></RequireAuth> },
  { path: '/admin/reports', element: <RequireAuth role="OPERATOR"><AdminReportsPage /></RequireAuth> },
  { path: '/admin/demo', element: <RequireAuth role="OPERATOR"><AdminDemoPage /></RequireAuth> },
  { path: '/admin/exceptions', element: <RequireAuth role="OPERATOR"><AdminExceptionsPage /></RequireAuth> },
  { path: '/admin/matching', element: <RequireAuth role="OPERATOR"><AdminMatchingPage /></RequireAuth> },
  { path: '/admin/tables/:id', element: <RequireAuth role="OPERATOR"><AdminTableDetailPage /></RequireAuth> },
])

export default function App() {
  return (
    <>
      <RouterProvider router={router} />
      <SplashScreen />
    </>
  )
}
