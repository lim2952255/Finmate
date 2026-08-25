import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const springProxy = {
  target: "http://localhost:8080",
  changeOrigin: true,
  xfwd: true,
  autoRewrite: true
};

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": { ...springProxy },
      "/login": {
        ...springProxy,
        bypass(request) {
          const pathname = request.url?.split("?")[0];
          return request.method === "GET" && pathname === "/login" ? request.url : undefined;
        }
      },
      "/logout": { ...springProxy },
      "/oauth2": { ...springProxy },
      "/ws": {
        target: "ws://localhost:8080",
        changeOrigin: true,
        xfwd: true,
        ws: true
      }
    }
  }
});
