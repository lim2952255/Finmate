import { useState } from "react";
import Header from "../components/layout/Header.jsx";
import ReportCard from "../components/reports/ReportCard.jsx";
import ReportTabs from "../components/reports/ReportTabs.jsx";
import { MARKET_REPORT_TOPICS } from "../data/marketReportTopics.js";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import useMarketReport from "../hooks/useMarketReport.js";
import { formatReportDateTime } from "../utils/reportFormatting.js";
import { countNewsSentiments } from "../utils/newsSentiment.js";
import "../styles/market-reports.css";

// 시장 리포트 페이지에서 처음 선택할 주제를 주제 배열의 첫 번째 항목으로 설정한다.
const DEFAULT_TOPIC = MARKET_REPORT_TOPICS[0].code;

// 시장 리포트 API의 요청 상태에 따라 로딩 또는 오류 화면을 반환하는 컴포넌트
function ReportStatus({ status, error, onRetry }) {
  // 서버 응답을 기다리는 동안 로딩 화면을 반환한다.
  if (status === "loading") {
    return (
      <div className="market-report-state" role="status" aria-live="polite">
        <span className="report-loader" aria-hidden="true" />
        <strong>시장 뉴스를 정리하고 있습니다.</strong>
        <p>관련도순 후보에서 핵심 뉴스를 선별합니다.</p>
      </div>
    );
  }

  // API 요청에 실패하면 오류 메시지와 다시 시도 버튼을 반환한다.
  if (status === "error") {
    return (
      <div className="market-report-state" role="alert">
        <strong>시장 뉴스를 불러오지 못했습니다.</strong>
        <p>{error?.message || "잠시 후 다시 시도해 주세요."}</p>
        {/* 버튼 클릭 시 useMarketReport에서 전달받은 retry 함수가 실행된다. */}
        <button className="market-report-retry" type="button" onClick={onRetry}>
          다시 시도
        </button>
      </div>
    );
  }
  return null;
}

// 서버에서 받은 뉴스 배열을 순회하며 ReportCard 목록을 만드는 컴포넌트
function ReportNews({ items }) {
  // 요청은 성공했지만 뉴스가 없다면 빈 목록 안내 화면을 반환한다.
  if (items.length === 0) {
    return (
      <div className="market-report-state" role="status">
        <strong>표시할 시장 뉴스가 없습니다.</strong>
        <p>다음 갱신 시점에 새로운 기사를 확인해 주세요.</p>
      </div>
    );
  }

  return (
    <div className="market-report-news" aria-live="polite">
      {/* 각 뉴스 객체를 ReportCard의 props로 전달하여 기사별 UI를 생성한다. */}
      {items.map((item, index) => (
        <ReportCard
          key={item.originalLink || item.link || `${item.publishedAt}-${index}`}
          item={item}
          index={index}
        />
      ))}
    </div>
  );
}

// 뉴스 / 시장 리포트 페이지의 전체 화면을 구성하는 JSX를 반환하는 컴포넌트
export default function MarketReportsPage() {
  // 브라우저 탭에 표시되는 문서 제목을 변경하는 Custom Hook
  useDocumentTitle("뉴스 / 시장 리포트 | FinMate");

  // 현재 선택된 시장 주제 코드를 state로 관리한다.
  // ReportTabs에서 setActiveTopicCode를 호출하면 이 컴포넌트가 다시 실행된다.
  const [activeTopicCode, setActiveTopicCode] = useState(DEFAULT_TOPIC);

  // 선택된 주제의 리포트와 API 요청 상태를 관리하는 Custom Hook
  const { status, report, error, retry } = useMarketReport(activeTopicCode);

  // MARKET_REPORT_TOPICS에서 현재 선택된 주제의 기본 화면 정보를 찾는다.
  const activeTopic = MARKET_REPORT_TOPICS.find(
    (topic) => topic.code === activeTopicCode
  ) || MARKET_REPORT_TOPICS[0];

  // API 응답 전에는 로컬 주제 정보를 사용하고, 응답 후에는 서버의 리포트 정보를 사용한다.
  const heading = report || activeTopic;

  // 서버 응답의 items와 keywords가 배열일 때만 사용하고, 아니면 빈 배열을 사용한다.
  const items = Array.isArray(report?.items) ? report.items : [];
  const keywords = Array.isArray(report?.keywords) ? report.keywords : [];
  const sentimentCounts = countNewsSentiments(items);

  return (
    <div className="page">
      {/* 모든 React 페이지에서 공통으로 사용하는 Header 컴포넌트 */}
      <Header />
      <main className="market-report-main">
        <section className="market-report-hero">
          <div className="market-report-hero-copy">
            <span className="market-report-eyebrow">MARKET BRIEFING</span>
            <h1>뉴스 / 시장 리포트</h1>
            <p>
              주요 지수와 금리·환율 뉴스를 한곳에서 확인하세요.
              관련도순 후보를 주제별 핵심 키워드와 최신순으로 정리합니다.
            </p>
          </div>
          <div className="market-report-hero-stats" aria-label="뉴스 제공 기준">
            <div><strong>{MARKET_REPORT_TOPICS.length}</strong><span>시장 주제</span></div>
            <div><strong>40</strong><span>주제별 후보</span></div>
            <div><strong>6H</strong><span>공유 캐시</span></div>
          </div>
        </section>

        <section className="market-report-shell" data-active-topic={activeTopicCode}>
          <div className="market-topic-navigation">
            <div>
              <span className="section-kicker">TOPICS</span>
              <h2>어떤 시장을 살펴볼까요?</h2>
            </div>
            {/* 주제 목록과 현재 선택값, 선택값을 변경할 함수를 props로 전달한다. */}
            <ReportTabs
              topics={MARKET_REPORT_TOPICS}
              activeTopicCode={activeTopicCode}
              onChange={setActiveTopicCode}
            />
          </div>

          <div
            id="market-report-panel"
            className="market-report-content"
            role="tabpanel"
            aria-labelledby={`market-topic-${activeTopicCode}`}
          >
            <header className="market-report-heading">
              <div className="market-report-identity">
                <span className="market-report-symbol" aria-hidden="true">{heading.symbol}</span>
                <div>
                  <span className="section-kicker">{heading.category}</span>
                  <h2>{heading.displayName} 시장 뉴스</h2>
                  <p>{heading.description}</p>
                </div>
              </div>
              <div className="market-report-meta">
                <span>
                  {/* report가 도착하기 전에는 확인 중 문구를, 도착한 뒤에는 수정 시각을 표시한다. */}
                  {report ? `업데이트 ${formatReportDateTime(report.updatedAt)}` : "업데이트 확인 중"}
                </span>
                <span>{report ? `검색 기준 ${report.query}` : "검색 기준 확인 중"}</span>
              </div>
            </header>

            <div className="market-keyword-area">
              <div className="market-keyword-group">
                <span className="market-keyword-title">선별 키워드</span>
                <div className="market-keywords" aria-live="polite">
                  {/* 서버가 반환한 키워드마다 span 태그를 하나씩 생성한다. */}
                  {keywords.map((keyword) => <span key={keyword}>{keyword}</span>)}
                </div>
              </div>
              <div className="market-sentiment-summary" aria-label="시장 뉴스 감성 분석 요약">
                <span className="market-sentiment-count market-sentiment-count--positive">호재: <strong>{sentimentCounts.POSITIVE}개</strong></span>
                <span className="market-sentiment-count market-sentiment-count--neutral">보통: <strong>{sentimentCounts.NEUTRAL}개</strong></span>
                <span className="market-sentiment-count market-sentiment-count--negative">악재: <strong>{sentimentCounts.NEGATIVE}개</strong></span>
              </div>
            </div>

            {/* API의 로딩 또는 오류 상태를 표시한다. */}
            <ReportStatus status={status} error={error} onRetry={retry} />
            {/* 요청 성공 시에만 뉴스 카드 목록을 화면에 표시한다. */}
            {status === "success" && <ReportNews items={items} />}
          </div>
        </section>

        <p className="market-report-footnote">
          뉴스 제목과 요약을 확인한 뒤 원문 링크를 통해 해당 언론사 페이지로 이동합니다.
        </p>
      </main>
    </div>
  );
}
