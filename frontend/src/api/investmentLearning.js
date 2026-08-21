import { readJson } from "./http.js";

async function getJson(url, { signal } = {}) {
  const response = await fetch(url, {
    headers: { Accept: "application/json" },
    signal
  });

  return readJson(response, "투자 학습 데이터를 불러오지 못했습니다.");
}

// 투자학습 카탈로그 정보를 조회한다.
export function getLearningCatalog(options) {
  return getJson("/api/investment-learning/catalog", options);
}

// 특정 개념의 상세 정보를 조회한다.
export function getLearningConcept(conceptCode, options) {
  return getJson(
    `/api/investment-learning/concepts/${encodeURIComponent(conceptCode)}`,
    options
  );
}
