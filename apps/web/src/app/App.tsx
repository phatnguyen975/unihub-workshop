import { BrowserRouter } from "react-router";
import { AppProviders } from "./providers/AppProviders";
import { AccessServices } from "./services/AccessServices";
import { LocaleProvider } from "@/shared/ui/locale";
import { AppRoutes } from "./routes/AppRoutes";
export default function App() {
  return (
    <AppProviders>
      <LocaleProvider>
        <BrowserRouter>
          <AccessServices>
            <AppRoutes />
          </AccessServices>
        </BrowserRouter>
      </LocaleProvider>
    </AppProviders>
  );
}
