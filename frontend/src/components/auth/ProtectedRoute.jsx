import { Navigate, Outlet, useLocation } from "react-router-dom";
import useSession from "../../hooks/useSession.js";

// /investments/portfolio 같은 화면 주소는 Spring이 아니라 Vite/Nginx가 React index.html로 처리한다.
// React는 화면을 보여주기 전에 공개 API인 /api/session으로 Spring 로그인 상태만 확인한다.
export default function ProtectedRoute() {
  const location = useLocation();
  const { status, session } = useSession();

  if (status === "loading") {
    return (
      <main className="route-state" role="status">
        <div className="route-state-card">
          <span className="route-spinner" aria-hidden="true" />
          <strong>로그인 상태를 확인하고 있습니다.</strong>
        </div>
      </main>
    );
  }

  if (status === "error") {
    return (
      <main className="route-state" role="alert">
        <div className="route-state-card">
          <strong>서버와 연결하지 못했습니다.</strong>
          <button type="button" onClick={() => window.location.reload()}>다시 시도</button>
        </div>
      </main>
    );
  }

  if (!session?.authenticated) {
    // 정상적인 SPA 흐름에서는 Spring Security RequestCache가 아니라 React가 원래 화면 경로를 기억한다.
    // 예: /investments/portfolio -> /login?redirect=%2Finvestments%2Fportfolio
    const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  // 인증된 경우에만 App.jsx에서 이 Route 아래에 중첩한 실제 페이지를 렌더링한다.
  return <Outlet />;
}
