import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import AppErrorBoundary from "./components/errors/AppErrorBoundary.jsx";
import "./styles/common.css";
import "./styles/react.css";

// index.html내의 root 객체를 받아서, Url에 따라 적절한 컴포넌트를 호출하는 App 컴포넌트를 호출하고, 결과를 index.html에 반영한다.
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AppErrorBoundary>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </AppErrorBoundary>
  </StrictMode>
);
