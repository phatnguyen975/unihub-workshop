import { Link, Route, Routes } from "react-router";

export function AppRoutes() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <main className="grid min-h-svh place-items-center">
            <h1 className="text-2xl font-medium">UniHub Web</h1>
          </main>
        }
      />
      <Route
        path="*"
        element={
          <main className="grid min-h-svh place-content-center gap-4 text-center">
            <h1 className="text-2xl font-medium">Page not found</h1>
            <Link className="underline focus-visible:outline-2" to="/">
              Return home
            </Link>
          </main>
        }
      />
    </Routes>
  );
}
