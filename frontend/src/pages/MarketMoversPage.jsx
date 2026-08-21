import { Link } from "react-router-dom";
import Header from "../components/layout/Header.jsx";
import RankingBoard from "../components/market-movers/RankingBoard.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import useMarketMovers from "../hooks/useMarketMovers.js";
import "../styles/market-movers.css";

// 시장 움직임 API의 최초 로딩 또는 오류 상태를 표시하는 컴포넌트
function MarketMoversStatus({ status, error, onRetry }) {
  if (status === "loading") {
    return <p className="ranking-state" role="status">시장 순위를 불러오는 중입니다.</p>;
  }

  if (status === "error") {
    return (
      <div className="ranking-state" role="alert">
        <p>{error?.message || "시장 순위를 불러오지 못했습니다."}</p>
        {/* 버튼 클릭 시 useMarketMovers에서 전달한 retry 함수가 실행된다. */}
        <button type="button" onClick={onRetry}>다시 불러오기</button>
      </div>
    );
  }

  return null;
}

// 거래량 / 거래대금 TOP10 시장 움직임 화면 전체를 구성하는 컴포넌트
export default function MarketMoversPage() {
  // 브라우저 탭에 표시되는 문서 제목을 변경한다.
  useDocumentTitle("시장 움직임 | FinMate");

  // Custom Hook으로 최초 조회와 5초 polling의 상태와 결과를 가져온다.
  const { status, pageInfo, error, retry } = useMarketMovers();

  // 서버 응답에 rankingBoards 배열이 있을 때만 사용하고, 응답 전에는 빈 배열을 사용한다.
  const rankingBoards = Array.isArray(pageInfo?.rankingBoards)
    ? pageInfo.rankingBoards
    : [];

  return (
    <div className="page">
      {/* React 화면에서 공통으로 사용하는 Header 컴포넌트 */}
      <Header />

      <main className="main market-movers-main">
        <section className="content market-movers-content">
          <div className="ranking-toolbar">
            <div>
              <h1>거래량 / 거래대금 TOP10</h1>
              <p>KOSPI, KOSDAQ, NASDAQ 시장별 순위</p>
            </div>
            <Link to="/investments">투자 홈</Link>
          </div>

          {/* 최초 요청의 loading 또는 error 상태에 맞는 안내 화면을 표시한다. */}
          <MarketMoversStatus status={status} error={error} onRetry={retry} />

          {/* 요청에 성공하면 시장과 순위 종류별 RankingBoard를 반복해서 생성한다. */}
          {status === "success" && (
            <div className="ranking-grid" aria-live="polite">
              {rankingBoards.map((board) => (
                <RankingBoard
                  key={`${board.marketType}-${board.rankingType}`}
                  board={board}
                />
              ))}
            </div>
          )}

          {/* 이전 데이터가 유지된 상태에서 polling만 실패한 경우 작은 안내 문구를 표시한다. */}
          {status === "success" && error && (
            <p className="ranking-refresh-warning" role="status">
              최근 갱신에 실패했습니다. 기존 순위를 표시하고 다음 갱신을 기다립니다.
            </p>
          )}
        </section>
      </main>
    </div>
  );
}
