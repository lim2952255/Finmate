import { readJson } from "./http.js";

// 선택한 시장 주제의 리포트를 Spring 서버에 비동기적으로 요청한다.
export async function getMarketReport(topicCode, { signal } = {}) {
  // topicCode를 URL에서 안전하게 사용할 수 있도록 변환하여 GET 요청을 보낸다.
  const response = await fetch(
    `/api/market-reports/${encodeURIComponent(topicCode)}`,
    {
      headers: { Accept: "application/json" },
      signal
    }
  );

  return readJson(response, "시장 리포트를 불러오지 못했습니다.");
}
