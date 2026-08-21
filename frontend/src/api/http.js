// 로그인 페이지로 이동하기 전 사용자가 보던 주소를 쿼리 파라미터로 보존한다.
function moveToLogin() {
  const currentPath = `${window.location.pathname}${window.location.search}`;

  if (window.location.pathname !== "/login") {
    window.location.assign(`/login?redirect=${encodeURIComponent(currentPath)}`);
  }
}

// Spring Security가 미인증 API 요청에 반환한 401 응답을 모든 API 모듈에서 동일하게 처리한다.
export function handleAuthentication(response) {
  const redirectedToLogin = response.redirected
    && new URL(response.url, window.location.origin).pathname === "/login";

  if (response.status === 401 || redirectedToLogin) {
    moveToLogin();
    throw new Error("로그인이 필요합니다.");
  }

  return response;
}

// JSON 응답의 상태를 검사하고 오류 본문에 message가 있으면 사용자 메시지로 사용한다.
export async function readJson(response, fallbackMessage) {
  handleAuthentication(response);

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message || fallbackMessage);
  }

  return response.status === 204 ? null : response.json();
}

// 조회용 GET 요청에서 반복되는 Accept 헤더와 JSON 변환을 공통 처리한다.
export async function fetchJson(url, { signal, fallbackMessage = "화면 정보를 불러오지 못했습니다." } = {}) {
  const response = await fetch(url, {
    headers: { Accept: "application/json" },
    signal
  });

  return readJson(response, fallbackMessage);
}
