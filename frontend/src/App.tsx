import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { AuthProvider } from "./auth/AuthProvider";
import { RequireAuth } from "./auth/RequireAuth";
import { Layout } from "./components/layout/Layout";
import { ToastProvider } from "./components/ui/Toast";
import { LoginPage, RegisterPage } from "./pages/AuthPages";
import { CheckoutPage } from "./pages/CheckoutPage";
import { EventDetailsPage } from "./pages/EventDetailsPage";
import { HomePage } from "./pages/HomePage";
import { MyOrdersPage } from "./pages/MyOrdersPage";
import { MyReservationsPage } from "./pages/MyReservationsPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { SalesPage } from "./pages/SalesPage";

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/sales", element: <SalesPage /> },
      { path: "/events/:eventId", element: <EventDetailsPage /> },
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
      { path: "/checkout/:reservationId", element: <RequireAuth><CheckoutPage /></RequireAuth> },
      { path: "/orders", element: <RequireAuth><MyOrdersPage /></RequireAuth> },
      { path: "/reservations", element: <RequireAuth><MyReservationsPage /></RequireAuth> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);

export function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </AuthProvider>
  );
}
