import { Link } from "react-router-dom";

// 순위 데이터 배열을 표의 행으로 변환하는 컴포넌트
function RankingRows({ items }) {
  return items.map((item) => (
    <tr key={`${item.marketType}-${item.rankingType}-${item.rank}-${item.symbol}`}>
      <td className="rank-cell">{item.rank}</td>
      <td className="stock-cell">
        {/* stockId가 있으면 React 종목 상세 화면으로 이동할 수 있는 링크를 만든다. */}
        {item.stockId ? (
          <Link to={`/investments/stocks/detail?stockId=${encodeURIComponent(item.stockId)}`}>
            {item.displayName}
          </Link>
        ) : (
          <strong>{item.displayName}</strong>
        )}
        <span className="stock-symbol">{item.symbol}</span>
      </td>
      <td className="numeric-cell">{item.displayCurrentPrice}</td>
      <td className="numeric-cell">
        {/* 등락 방향에 따라 up, down, flat CSS class를 적용하여 글자 색상을 바꾼다. */}
        <span className={`change-rate ${item.changeRateClass}`}>
          {item.displayChangeRate}
        </span>
      </td>
      <td className="numeric-cell">{item.displayAccumulatedVolume}</td>
      <td className="numeric-cell">{item.displayAccumulatedTradeAmount}</td>
    </tr>
  ));
}

// 하나의 시장과 순위 종류에 해당하는 TOP10 보드를 만드는 컴포넌트
export default function RankingBoard({ board }) {
  // API 응답의 items가 배열이 아닐 가능성까지 고려하여 빈 배열을 기본값으로 사용한다.
  const items = Array.isArray(board.items) ? board.items : [];
  const rankingTypeName = RANKING_TYPE_NAMES[board.rankingType] || board.rankingType;

  return (
    <article className="ranking-board">
      <div className="ranking-board-header">
        <div className="ranking-board-title">
          <strong>{board.marketType} {rankingTypeName} TOP10</strong>
          <span>갱신 <span>{board.displayRefreshedAt || "-"}</span></span>
        </div>
        {/* marketOpen 값에 따라 장중 또는 장마감 상태와 CSS class를 설정한다. */}
        <span className={`market-status${board.marketOpen ? " open" : ""}`}>
          {board.marketOpen ? "장중" : "장마감"}
        </span>
      </div>

      {/* 순위 항목이 있으면 표를, 없다면 캐시 데이터가 없다는 안내 문구를 표시한다. */}
      {items.length > 0 ? (
        <div className="ranking-table-wrap">
          <table className="ranking-table">
            <thead>
              <tr>
                <th className="rank-cell">순위</th>
                <th>종목</th>
                <th className="numeric-cell">현재가</th>
                <th className="numeric-cell">등락률</th>
                <th className="numeric-cell">거래량</th>
                <th className="numeric-cell">거래대금</th>
              </tr>
            </thead>
            <tbody>
              <RankingRows items={items} />
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-ranking">아직 캐시된 순위 데이터가 없습니다.</div>
      )}
    </article>
  );
}
// API에서 enum 이름으로 받은 순위 종류를 사용자에게 보여줄 한글 이름으로 변환한다.
const RANKING_TYPE_NAMES = {
  TRADE_AMOUNT: "거래대금",
  VOLUME: "거래량"
};
