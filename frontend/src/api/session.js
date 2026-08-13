
// Spring 서버에 비동기적으로 세션 데이터를 요청한다.
export async function getSession({ signal } = {}) {
  const response = await fetch("/api/session", {
    headers: { Accept: "application/json" },
    signal // AbortController 시그널 -> 해당 시그널은 Spring 서버로 전달되는 것이 아니라, fetch할때 요청을 보낼지 말지를 결정하는 용도로 활용된다.
  });

  if (!response.ok) {
    throw new Error("로그인 상태를 확인하지 못했습니다.");
  }

  // 스프링 서버의 HTTP응답 body에 들어있는 JSON 문자열을 JS에서 사용하기 편한 객체로 변환하여 리턴한다.
  return response.json();
}
