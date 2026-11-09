import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/noto-sans";
import "./style.css";
import { ServicesProvider } from "./app/ServicesProvider";
import { RouterProvider } from "react-router-dom";
import { router } from "./app/router";
import "./app.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ServicesProvider>
      <RouterProvider router={router} />
    </ServicesProvider>
  </StrictMode>,
);
