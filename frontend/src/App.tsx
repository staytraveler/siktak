import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import AdminHomePage from './pages/admin/AdminHomePage'
import ParticipantHomePage from './pages/app/ParticipantHomePage'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/app', element: <ParticipantHomePage /> },
  { path: '/admin', element: <AdminHomePage /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
