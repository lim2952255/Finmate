import { readJson } from "./http.js";

// Spring 서버에 시장별 거래량 / 거래대금 TOP10 데이터를 비동기적으로 요청한다.
export async function getMarketMovers({ signal } = {}) {
  const response = await fetch("/api/stocks/market-movers", {
    headers: { Accept: "application/json" },
    signal
  });

  return readJson(response, "시장 움직임을 불러오지 못했습니다.");
}
