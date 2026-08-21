import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/accounts": {
        target: "http://localhost:8080",
        bypass(request) {
          const pathname = request.url?.split("?")[0];
          const reactPagePaths = ["/accounts", "/accounts/list", "/accounts/open", "/accounts/transactions", "/accounts/transfer", "/accounts/transfer-investment", "/accounts/transfer-limit"];
          return reactPagePaths.includes(pathname) ? request.url : undefined;
        }
      },
      "/investments": {
        target: "http://localhost:8080",
        bypass(request) {
          const pathname = request.url?.split("?")[0];
          // React가 담당하는 페이지 주소는 Spring으로 보내지 않고 Vite가 index.html을 반환하게 한다.
          const reactPagePaths = [
            "/investments",
            "/investments/list",
            "/investments/open",
            "/investments/transfer",
            "/investments/currency-exchange",
            "/investments/stocks/search",
            "/investments/stocks/watchlist",
            "/investments/securityCashTransaction",
            "/investments/currency-exchange/transactions",
            "/investments/portfolio",
            "/investments/market-data",
            "/investments/exchanges",
            "/investments/indices",
            "/investments/stocks/detail",
            "/investments/orders",
            "/investments/reports",
            "/investments/stocks/market-movers"
          ];
          return reactPagePaths.includes(pathname) || pathname?.startsWith("/investments/stocks/order/")
            ? request.url
            : undefined;
        }
      },
      "/login": {
        target: "http://localhost:8080",
        bypass(request) {
          return request.method === "GET" ? request.url : undefined;
        }
      },
      "/signup": {
        target: "http://localhost:8080",
        bypass(request) {
          return request.method === "GET" ? request.url : undefined;
        }
      },
      "/logout": "http://localhost:8080",
      "/oauth2": "http://localhost:8080",
      "/css": "http://localhost:8080",
      "/images": "http://localhost:8080",
      "/ws": {
        target: "ws://localhost:8080",
        ws: true
      }
    }
  }
});
