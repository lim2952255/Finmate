import { Link } from "react-router-dom";

function formatDate(value) {
  if (!value) return "";
  return value.replace("T", " ").slice(0, 16);
}

function tradingStatus(stock) {
  if (!stock.tradable) return { label: "거래 불가", description: "종목 상태상 현재 주문할 수 없습니다." };
  if (!stock.tradingAvailable) return { label: "장 마감", description: `거래 가능 시간: ${stock.tradingTimeDescription}` };
  return { label: "거래 가능", description: `거래 가능 시간: ${stock.tradingTimeDescription}` };
}

// 종목 검색과 관심종목 화면이 공유하는 종목 표
export default function StockTable({ stocks, onToggleFavorite, updatingId, showCreatedAt = false }) {
  return (
    <div className="table-scroll">
      <table className="records-table stock-list-table">
        <thead>
          <tr>
            <th>관심</th><th>종목코드</th><th>종목명</th><th>시장</th><th>업종</th>
            <th>상품유형</th><th>통화</th><th>거래 상태</th>
            {showCreatedAt && <th>등록일시</th>}
          </tr>
        </thead>
        <tbody>
          {stocks.map((stock) => {
            const status = tradingStatus(stock);
            return <tr key={stock.id}>
              <td>
                <button
                  className={`favorite-button${stock.favorite ? " active" : ""}`}
                  type="button"
                  disabled={updatingId !== null}
                  onClick={() => onToggleFavorite(stock.id)}
                  aria-label={stock.favorite ? "관심 종목 해제" : "관심 종목 등록"}
                >
                  {updatingId === stock.id ? "…" : stock.favorite ? "★" : "☆"}
                </button>
              </td>
              <td><Link className="code-chip" to={`/investments/stocks/detail?stockId=${stock.id}`}>{stock.symbol}</Link></td>
              <td>
                <div className="stock-cell">
                  <Link className="primary-line" to={`/investments/stocks/detail?stockId=${stock.id}`}>{stock.nameKo}</Link>
                  {stock.nameEn && <span className="secondary-line">{stock.nameEn}</span>}
                </div>
              </td>
              <td><span className="market-chip">{stock.marketType}</span></td>
              <td><span className={`industry-chip${stock.industryName === "없음" ? " is-empty" : ""}`}>{stock.industryName}</span></td>
              <td><span className="security-chip">{stock.securityType}</span></td>
              <td><span className="currency-chip">{stock.currency}</span></td>
              <td><span className={`status-chip${stock.tradingAvailable ? "" : " is-closed"}`} title={status.description}>{status.label}</span></td>
              {showCreatedAt && <td className="date-cell">{formatDate(stock.favoriteCreatedAt)}</td>}
            </tr>
          })}
        </tbody>
      </table>
    </div>
  );
}
