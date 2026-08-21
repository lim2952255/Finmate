import { getSession } from "./session.js";
import { readJson } from "./http.js";

// 투자 홈과 증권계좌 목록에 필요한 요약 데이터를 Spring 서버에 요청한다.
export async function getInvestmentOverview({ signal } = {}) {
  const response = await fetch("/api/investments", {
    headers: { Accept: "application/json" },
    signal
  });

  return readJson(response, "증권계좌 정보를 불러오지 못했습니다.");
}

// 선택한 증권계좌를 대표계좌로 변경하고 갱신된 투자 데이터를 반환한다.
export async function setPrimaryInvestment(investmentId) {
  // 상태를 변경하는 POST 요청에는 Spring Security가 발급한 CSRF 토큰이 필요하다.
  const session = await getSession();
  const response = await fetch("/api/investments/primary", {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      [session.csrf.headerName]: session.csrf.token
    },
    body: JSON.stringify({ investmentId })
  });

  return readJson(response, "대표 증권계좌를 변경하지 못했습니다.");
}
