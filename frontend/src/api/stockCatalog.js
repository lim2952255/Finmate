import { getSession } from "./session.js";
import { readJson } from "./http.js";

export async function searchStocks(params, { signal } = {}) {
  const query = new URLSearchParams(params);
  const response = await fetch(`/api/stocks/search?${query}`, {
    headers: { Accept: "application/json" },
    signal
  });
  return readJson(response, "종목 검색 결과를 불러오지 못했습니다.");
}

export async function getWatchlist(page, { signal } = {}) {
  const response = await fetch(`/api/stocks/watchlist?page=${page}`, {
    headers: { Accept: "application/json" },
    signal
  });
  return readJson(response, "관심 종목을 불러오지 못했습니다.");
}

export async function toggleFavorite(stockId) {
  const session = await getSession();
  const response = await fetch("/api/stocks/favorite", {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      [session.csrf.headerName]: session.csrf.token
    },
    body: JSON.stringify({ stockId })
  });
  return readJson(response, "관심 종목을 변경하지 못했습니다.");
}
