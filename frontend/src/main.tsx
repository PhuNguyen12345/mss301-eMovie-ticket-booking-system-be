import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/noto-sans";
import "./style.css";
import { ServicesProvider } from "./app/ServicesProvider";
import { ShowtimesPage } from "./features/showtimes/components/ShowtimesPage";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ServicesProvider>
      <ShowtimesPage />
    </ServicesProvider>
  </StrictMode>,
);
