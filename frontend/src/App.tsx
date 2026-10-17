import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminSlotsPage from './pages/admin/AdminSlotsPage'
import ApplicationDetailPage from './pages/app/ApplicationDetailPage'
import ParticipantHomePage from './pages/app/ParticipantHomePage'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/app', element: <ParticipantHomePage /> },
  { path: '/app/applications/:id', element: <ApplicationDetailPage /> },
  { path: '/admin', element: <AdminHomePage /> },
  { path: '/admin/slots', element: <AdminSlotsPage /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
