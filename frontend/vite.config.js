import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const springProxy = {
  target: "http://localhost:8080",
  // 브라우저가 접속한 localhost:5173 Host를 유지해 Spring의 동일 출처 검사와 redirect 기준을 맞춘다.
  changeOrigin: false,
  // WebSocket 요청에 X-Forwarded-Proto: ws가 추가되면 Spring의 로그인 redirect 생성이 실패할 수 있다.
  // 로컬에서는 원래 Host만 유지하면 Nginx와 동일하게 외부 주소를 복원할 수 있으므로 전달하지 않는다.
  xfwd: false,
  autoRewrite: true
};

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 브라우저는 http://localhost:5173/api/... 로 요청하고 URL에 8080을 사용하지 않는다.
      // Vite 개발 서버가 이 요청을 받은 뒤 서버 내부에서만 http://localhost:8080의 Spring으로 대신 전달한다.
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
        changeOrigin: false,
        xfwd: false,
        ws: true
      }
    }
  }
});
