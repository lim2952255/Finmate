import { Navigate, Outlet, useLocation } from "react-router-dom";
import useSession from "../../hooks/useSession.js";

// Spring이 HTML을 만들던 시절에는 화면 URL 요청을 Security가 바로 차단했다.
// React 내부 이동은 서버에 문서 요청을 보내지 않으므로, 보호 화면 진입 전에 세션을 직접 확인한다.
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
    const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  // 인증된 경우에만 App.jsx에서 이 Route 아래에 중첩한 실제 페이지를 렌더링한다.
  return <Outlet />;
}
