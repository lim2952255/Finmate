// 시장 리포트 화면에서 사용할 주제의 기본 정보를 배열로 관리한다.
// API 응답을 기다리는 동안에도 탭 이름, 기호, 설명을 먼저 화면에 표시할 수 있다.
export const MARKET_REPORT_TOPICS = [
  {
    code: "kospi",
    displayName: "KOSPI",
    symbol: "K",
    category: "국내 대표 지수",
    description: "유가증권시장의 수급과 지수 흐름을 살펴봅니다."
  },
  {
    code: "kosdaq",
    displayName: "KOSDAQ",
    symbol: "KQ",
    category: "국내 성장주 지수",
    description: "코스닥시장의 수급과 성장주 흐름을 살펴봅니다."
  },
  {
    code: "nasdaq",
    displayName: "NASDAQ",
    symbol: "N",
    category: "미국 기술주 지수",
    description: "나스닥과 미국 기술주의 주요 움직임을 살펴봅니다."
  },
  {
    code: "sp500",
    displayName: "S&P 500",
    symbol: "S&P",
    category: "미국 대형주 지수",
    description: "미국 대표 대형주의 흐름과 월가의 시장 전망을 살펴봅니다."
  },
  {
    code: "interest-rate",
    displayName: "금리",
    symbol: "%",
    category: "통화정책과 채권시장",
    description: "한국은행과 연준의 정책 방향 및 시장금리 변화를 살펴봅니다."
  },
  {
    code: "exchange-rate",
    displayName: "환율",
    symbol: "FX",
    category: "외환시장",
    description: "원·달러를 중심으로 주요 통화와 외환시장 흐름을 살펴봅니다."
  }
];
