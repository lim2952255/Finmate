import { formatReportDateTime } from "../../utils/reportFormatting.js";
import { NEWS_SENTIMENT_LABELS, normalizeNewsSentiment } from "../../utils/newsSentiment.js";

// 뉴스 API의 제목과 요약에 포함될 수 있는 HTML 문자열을 일반 텍스트로 변환한다.
function decodeText(value) {
  if (!value) return "";

  return new DOMParser()
    .parseFromString(String(value), "text/html")
    .body.textContent || "";
}

// 뉴스 원문 링크가 브라우저에서 안전하게 이동할 수 있는 HTTP 또는 HTTPS 주소인지 확인한다.
function safeArticleUrl(item) {
  // originalLink가 있으면 우선 사용하고, 없다면 link를 사용한다.
  const candidate = item.originalLink || item.link;
  if (!candidate) return null;

  try {
    const url = new URL(candidate);
    // http와 https가 아닌 주소는 사용할 수 없도록 null을 반환한다.
    return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
  } catch {
    // URL 객체로 변환할 수 없는 잘못된 주소도 null로 처리한다.
    return null;
  }
}

// 뉴스 객체 하나를 화면에 표시할 기사 카드 JSX로 변환하는 컴포넌트
export default function ReportCard({ item, index }) {
  const articleUrl = safeArticleUrl(item);

  // 원문 주소가 있으면 hostname을 언론사 출처로 표시하고, 없으면 기본 문구를 사용한다.
  const source = articleUrl
    ? new URL(articleUrl).hostname.replace(/^www\./, "")
    : "NAVER 뉴스 검색";
  const title = decodeText(item.title);
  const description = decodeText(item.description);
  const sentiment = normalizeNewsSentiment(item.sentiment);

  return (
    <article className="market-report-card">
      <div className="market-report-card-top">
        <span className="market-report-source">{source}</span>
        <div className="market-report-card-labels">
          {/* 종목 뉴스와 같은 색상 규칙으로 시장 기사의 감성 결과를 강조한다. */}
          <span className={`market-report-sentiment market-report-sentiment--${sentiment.toLowerCase()}`}>
            {NEWS_SENTIMENT_LABELS[sentiment]}
          </span>
          <span className="market-report-rank">{String(index + 1).padStart(2, "0")}</span>
        </div>
      </div>

      <h3>
        {/* 유효한 원문 주소가 있으면 제목을 링크로 만들고, 없으면 제목만 표시한다. */}
        {articleUrl ? (
          <a href={articleUrl} target="_blank" rel="noopener noreferrer">{title}</a>
        ) : title}
      </h3>

      {/* 기사 요약이 존재할 때만 요약 문단을 화면에 표시한다. */}
      {description && <p className="market-report-summary">{description}</p>}

      <div className="market-report-card-footer">
        <time dateTime={item.publishedAt || undefined}>
          {formatReportDateTime(item.publishedAt)}
        </time>
        {/* 유효한 원문 주소가 있을 때만 새 브라우저 탭으로 이동하는 링크를 표시한다. */}
        {articleUrl && (
          <a href={articleUrl} target="_blank" rel="noopener noreferrer">원문 보기</a>
        )}
      </div>
    </article>
  );
}
