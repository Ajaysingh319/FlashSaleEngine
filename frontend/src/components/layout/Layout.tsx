import { Outlet, ScrollRestoration } from "react-router-dom";
import { DemoModeBanner } from "../ui/DemoModeBanner";
import { Footer } from "./Footer";
import { Header } from "./Header";

export function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <DemoModeBanner />
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      <ScrollRestoration />
    </div>
  );
}
