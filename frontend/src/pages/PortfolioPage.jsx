import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import PageLoading from "../components/common/PageLoading.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import "../styles/portfolio.css";

const SEPARATED = "SEPARATED";

function toNumber(value) {
  if (value === null || value === undefined || value === "") return Number.NaN;
  return Number(String(value).replaceAll(",", ""));
}

function fractionDigits(currency) {
  return currency === "USD" ? 2 : 0;
}

function formatNumber(value, digits = 0) {
  if (!Number.isFinite(value)) return "-";
  return new Intl.NumberFormat("ko-KR", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  }).format(value);
}

function formatQuantity(value) {
  if (!Number.isFinite(value)) return "-";
  return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 6 }).format(value);
}

function directionOf(value) {
  return value > 0 ? "positive" : value < 0 ? "negative" : "flat";
}

function signed(value, digits) {
  return `${value > 0 ? "+" : ""}${formatNumber(value, digits)}`;
}

function convertCurrency(value, fromCurrency, toCurrency, usdKrwRate) {
  if (!Number.isFinite(value) || fromCurrency === toCurrency) return value;
  if (!Number.isFinite(usdKrwRate) || usdKrwRate <= 0) return Number.NaN;
  if (fromCurrency === "USD" && toCurrency === "KRW") return value * usdKrwRate;
  if (fromCurrency === "KRW" && toCurrency === "USD") return value / usdKrwRate;
  return Number.NaN;
}

function SummaryCard({ summary, converted }) {
  const direction = directionOf(summary.profit);
  const digits = fractionDigits(summary.currency);
  const complete = summary.receivedCount === summary.totalCount;

  return (
    <article className={`portfolio-summary-card ${complete ? direction : "waiting"}`}>
      <div className="portfolio-summary-heading">
        <span>{converted ? "환산 총 평가금액" : "총 평가금액"}</span>
        <span className="portfolio-summary-currency">{summary.currency}</span>
      </div>
      <strong className="portfolio-summary-value">
        {complete ? `${formatNumber(summary.marketValue, digits)} ${summary.currency}` : "수신 대기"}
      </strong>
      <div className="portfolio-summary-grid">
        <div className="portfolio-summary-item"><span className="portfolio-summary-label">{converted ? "환산 총 평가손익" : "총 평가손익"}</span><strong className={direction}>{complete ? `${signed(summary.profit, digits)} ${summary.currency}` : "-"}</strong></div>
        <div className="portfolio-summary-item"><span className="portfolio-summary-label">{converted ? "환산 총 수익률" : "총 수익률"}</span><strong className={direction}>{complete ? `${summary.profitRate.toFixed(2)}%` : "-"}</strong></div>
      </div>
      <small className="portfolio-summary-status">매입금액 {formatNumber(summary.purchaseValue, digits)} {summary.currency}{!complete && ` · ${summary.receivedCount}/${summary.totalCount} 종목 반영`}</small>
    </article>
  );
}

function UnifiedWaitingCard() {
  return (
    <article className="portfolio-summary-card waiting">
      <div className="portfolio-summary-heading">
        <span>환산 총 평가금액</span>
        <span className="portfolio-summary-currency">KRW</span>
      </div>
      <strong className="portfolio-summary-value">수신 대기</strong>
      <div className="portfolio-summary-grid">
        <div className="portfolio-summary-item"><span className="portfolio-summary-label">환산 총 평가손익</span><strong>-</strong></div>
        <div className="portfolio-summary-item"><span className="portfolio-summary-label">환산 총 수익률</span><strong>-</strong></div>
      </div>
      <small className="portfolio-summary-status">환산 계산 중</small>
    </article>
  );
}

function IndustryAllocations({ allocations }) {
  if (!allocations.length) return null;

  return (
    <section className="industry-allocation">
      <h2>업종별 비중</h2>
      <div className="industry-allocation-grid">
        {allocations.map((item) => (
          <article className="industry-allocation-card" key={`${item.currency}-${item.groupName}-${item.industryName}`}>
            <div className="industry-allocation-heading">
              <span className={`industry-chip${item.industryName === "없음" ? " is-empty" : ""}`}>{item.industryName}</span>
              <span className="industry-allocation-tags">
                <span className="industry-allocation-currency">{item.groupName}</span>
                <span className="industry-allocation-currency">{item.currency}</span>
              </span>
            </div>
            <div className="industry-allocation-bar" aria-hidden="true"><span style={{ width: `${Math.min(item.percentage, 100)}%` }} /></div>
            <p className="industry-allocation-value">{item.percentage.toFixed(2)}% · 매입금액 {formatNumber(item.purchaseAmount, fractionDigits(item.currency))} {item.currency}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

export default function PortfolioPage() {
  useDocumentTitle("포트폴리오 | FinMate");
  const location = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [currencyView, setCurrencyView] = useState(SEPARATED);
  const [realtimePrices, setRealtimePrices] = useState({});
  const [pendingInvestmentId, setPendingInvestmentId] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    getJson(`/api/investment-read/portfolio${location.search}`, { signal: controller.signal })
      .then((response) => {
        setData(response);
        setError(null);
        setPendingInvestmentId(response.selectedInvestmentId ? String(response.selectedInvestmentId) : "");
        setRealtimePrices(Object.fromEntries(response.holdings
          .filter((holding) => holding.valuationPrice !== null)
          .map((holding) => [holding.stockId, { price: toNumber(holding.valuationPrice), source: holding.valuationPriceSource }])));
      })
      .catch((requestError) => {
        if (requestError.name !== "AbortError") setError(requestError);
      });
    return () => controller.abort();
  }, [location.search]);

  // 보유 종목만 구독하고 실시간 체결가가 오면 현재가 상태를 갱신한다.
  useEffect(() => {
    const stockIds = [...new Set((data?.holdings || []).map((holding) => holding.stockId))];
    if (!stockIds.length || !window.WebSocket) return undefined;

    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/stocks`);
    socket.addEventListener("open", () => stockIds.forEach((stockId) => socket.send(JSON.stringify({ type: "SUBSCRIBE_PORTFOLIO_STOCK", stockId }))));
    socket.addEventListener("message", (event) => {
      try {
        const message = JSON.parse(event.data);
        const price = toNumber(message.currentPrice);
        if (message.type === "STOCK_TRADE" && Number.isFinite(price)) {
          setRealtimePrices((current) => ({ ...current, [Number(message.stockId)]: { price, source: "실시간 시세" } }));
        }
      } catch {
        // 다른 형식의 WebSocket 메시지는 이 화면의 시세 갱신 대상이 아니다.
      }
    });

    return () => {
      if (socket.readyState === WebSocket.OPEN) {
        stockIds.forEach((stockId) => socket.send(JSON.stringify({ type: "UNSUBSCRIBE_PORTFOLIO_STOCK", stockId })));
      }
      socket.close();
    };
  }, [data]);

  const usdKrwRate = toNumber(data?.usdKrwExchangeRate?.price);
  const holdings = useMemo(() => (data?.holdings || []).map((holding) => ({
    ...holding,
    quantityValue: toNumber(holding.quantity),
    averagePriceValue: toNumber(holding.averagePrice),
    currentPriceValue: realtimePrices[holding.stockId]?.price ?? Number.NaN,
    priceSource: realtimePrices[holding.stockId]?.source || ""
  })), [data, realtimePrices]);

  const summaries = useMemo(() => {
    const byCurrency = new Map();
    holdings.forEach((holding) => {
      const targetCurrency = currencyView === SEPARATED ? holding.currency : currencyView;
      const summary = byCurrency.get(targetCurrency) || { currency: targetCurrency, purchaseValue: 0, marketValue: 0, receivedCount: 0, totalCount: 0 };
      summary.purchaseValue += convertCurrency(holding.averagePriceValue * holding.quantityValue, holding.currency, targetCurrency, usdKrwRate);
      summary.totalCount += 1;
      if (Number.isFinite(holding.currentPriceValue)) {
        summary.marketValue += convertCurrency(holding.currentPriceValue * holding.quantityValue, holding.currency, targetCurrency, usdKrwRate);
        summary.receivedCount += 1;
      }
      byCurrency.set(targetCurrency, summary);
    });
    return [...byCurrency.values()].map((summary) => {
      const profit = summary.marketValue - summary.purchaseValue;
      return { ...summary, profit, profitRate: summary.purchaseValue === 0 ? 0 : profit / summary.purchaseValue * 100 };
    });
  }, [currencyView, holdings, usdKrwRate]);

  const allocations = useMemo(() => {
    if (currencyView === SEPARATED) {
      return (data?.industryAllocations || []).map((item) => ({ ...item, purchaseAmount: toNumber(item.purchaseAmount), percentage: toNumber(item.percentage) }));
    }

    const grouped = new Map();
    let total = 0;
    holdings.forEach((holding) => {
      const purchaseAmount = convertCurrency(holding.averagePriceValue * holding.quantityValue, holding.currency, currencyView, usdKrwRate);
      const key = `${holding.industryGroup}\u0000${holding.industry}`;
      const current = grouped.get(key) || { currency: currencyView, groupName: holding.industryGroup, industryName: holding.industry, purchaseAmount: 0 };
      current.purchaseAmount += purchaseAmount;
      grouped.set(key, current);
      total += purchaseAmount;
    });
    return [...grouped.values()].map((item) => ({ ...item, percentage: total ? item.purchaseAmount / total * 100 : 0 })).sort((left, right) => right.purchaseAmount - left.purchaseAmount);
  }, [currencyView, data, holdings, usdKrwRate]);

  const displayHolding = (holding) => {
    const targetCurrency = currencyView === SEPARATED ? holding.currency : currencyView;
    const digits = fractionDigits(targetCurrency);
    const averagePrice = convertCurrency(holding.averagePriceValue, holding.currency, targetCurrency, usdKrwRate);
    const currentPrice = convertCurrency(holding.currentPriceValue, holding.currency, targetCurrency, usdKrwRate);
    const marketValue = convertCurrency(holding.currentPriceValue * holding.quantityValue, holding.currency, targetCurrency, usdKrwRate);
    const purchaseValue = convertCurrency(holding.averagePriceValue * holding.quantityValue, holding.currency, targetCurrency, usdKrwRate);
    const profit = marketValue - purchaseValue;
    const profitRate = purchaseValue === 0 ? 0 : profit / purchaseValue * 100;
    return { targetCurrency, digits, averagePrice, currentPrice, marketValue, profit, profitRate };
  };

  return (
    <div className="page">
      <Header />
      <main className="main portfolio-main">
        <section className="content portfolio-content">
          <div className="page-heading">
            <span className="eyebrow">PORTFOLIO</span>
            <h1>내 포트폴리오</h1>
            <p>보유 종목의 실시간 평가금액과 손익, 업종별 투자 비중을 한눈에 확인하세요.</p>
          </div>

          {!data && !error && <PageLoading message="포트폴리오와 최근 시세를 불러오고 있습니다." />}
          {error && <p className="overview-error" role="alert">{error.message}</p>}
          {data && (
            <>
              {!!data.accounts.length && <form className="portfolio-toolbar" onSubmit={(event) => {
                event.preventDefault();
                navigate(pendingInvestmentId ? `/investments/portfolio?investmentId=${pendingInvestmentId}` : "/investments/portfolio");
              }}>
                <label>증권 계좌
                  <select value={pendingInvestmentId} onChange={(event) => setPendingInvestmentId(event.target.value)}>
                    <option value="">전체 증권계좌</option>
                    {data.accounts.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
                  </select>
                </label>
                <button type="submit">조회</button>
                <Link to={data.selectedInvestmentId ? `/investments/orders?investmentId=${data.selectedInvestmentId}` : "/investments/orders"}>주문 내역</Link>
                <div className="portfolio-currency-view">
                  <strong>표시 통화</strong>
                  <div className="portfolio-currency-view-buttons">
                    <button className={currencyView === SEPARATED ? "is-active" : ""} type="button" onClick={() => setCurrencyView(SEPARATED)}>통화별</button>
                    <button className={currencyView === "KRW" ? "is-active" : ""} type="button" disabled={!Number.isFinite(usdKrwRate)} onClick={() => setCurrencyView("KRW")}>원화 환산</button>
                    <button className={currencyView === "USD" ? "is-active" : ""} type="button" disabled={!Number.isFinite(usdKrwRate)} onClick={() => setCurrencyView("USD")}>달러 환산</button>
                  </div>
                </div>
                <p className="portfolio-exchange-rate">{Number.isFinite(usdKrwRate)
                  ? `적용 환율 1 USD = ${formatNumber(usdKrwRate, 2)} KRW${data.usdKrwExchangeRate?.receivedAt ? ` · 기준 ${data.usdKrwExchangeRate.receivedAt.replace("T", " ").slice(0, 19)}` : ""} · 표시용 환산 금액`
                  : "USD/KRW 환율을 조회하지 못해 통화별 보기만 사용할 수 있습니다."}</p>
              </form>}

              {!data.accounts.length && <div className="empty-state"><strong>아직 포트폴리오가 비어 있습니다.</strong><p>증권 계좌를 만든 뒤 종목을 주문하면 평가금액과 손익을 확인할 수 있습니다.</p><Link to="/investments/open">증권 계좌 개설</Link></div>}
              {!!data.accounts.length && !holdings.length && <div className="empty-state"><strong>보유 종목이 없습니다.</strong><Link to="/investments/stocks/search">종목 검색</Link></div>}

              {!!summaries.length && <div className="portfolio-summary">
                {summaries.map((summary) => <SummaryCard key={summary.currency} summary={summary} converted={currencyView !== SEPARATED} />)}
                {currencyView === SEPARATED && <UnifiedWaitingCard />}
              </div>}
              <IndustryAllocations allocations={allocations} />

              {!!holdings.length && (
                <div className="table-scroll">
                    <table className="records-table portfolio-table">
                      <thead><tr>{data.allAccounts && <th>계좌</th>}<th>종목</th><th>시장</th><th>업종</th><th>수량</th><th>평균단가</th><th>현재가</th><th>평가금액</th><th>평가손익</th><th>수익률</th><th>주문</th></tr></thead>
                      <tbody>{holdings.map((holding) => {
                        const display = displayHolding(holding);
                        const hasPrice = Number.isFinite(holding.currentPriceValue);
                        const direction = directionOf(display.profit);
                        return (
                          <tr key={holding.id}>
                            {data.allAccounts && <td className="portfolio-account"><div className="account-cell"><span className="primary-line">{holding.securitiesCompany}</span><small className="secondary-line">{holding.accountNumber}</small></div></td>}
                            <td className="portfolio-stock"><div className="stock-cell"><Link className="primary-line" to={`/investments/stocks/detail?stockId=${holding.stockId}`}>{holding.stockName}</Link><small className="secondary-line">{holding.symbol}</small></div></td>
                            <td><span className="market-chip">{holding.market}</span></td>
                            <td><span className="industry-chip">{holding.industry}</span></td>
                            <td className="money-cell">{formatQuantity(holding.quantityValue)}</td>
                            <td className="money-cell"><span>{formatNumber(display.averagePrice, display.digits)}</span><small>{currencyView === SEPARATED ? display.targetCurrency : `${formatNumber(holding.averagePriceValue, fractionDigits(holding.currency))} ${holding.currency}`}</small></td>
                            <td className="portfolio-current-price money-cell"><span>{hasPrice ? `${formatNumber(display.currentPrice, display.digits)} ${display.targetCurrency}` : "수신 대기"}</span>{hasPrice && currencyView !== SEPARATED && display.targetCurrency !== holding.currency && <small>{formatNumber(holding.currentPriceValue, fractionDigits(holding.currency))} {holding.currency}</small>}<small>{holding.priceSource}</small></td>
                            <td className="portfolio-market-value money-cell"><span>{hasPrice ? `${formatNumber(display.marketValue, display.digits)} ${display.targetCurrency}` : "-"}</span>{hasPrice && currencyView !== SEPARATED && display.targetCurrency !== holding.currency && <small>{formatNumber(holding.currentPriceValue * holding.quantityValue, fractionDigits(holding.currency))} {holding.currency}</small>}</td>
                            <td className={`portfolio-profit money-cell ${direction}`}><span>{hasPrice ? `${signed(display.profit, display.digits)} ${display.targetCurrency}` : "-"}</span>{hasPrice && currencyView !== SEPARATED && display.targetCurrency !== holding.currency && <small>{signed((holding.currentPriceValue - holding.averagePriceValue) * holding.quantityValue, fractionDigits(holding.currency))} {holding.currency}</small>}</td>
                            <td className={`portfolio-profit-rate ${direction}`}>{hasPrice ? `${display.profitRate.toFixed(2)}%` : "-"}</td>
                            <td>{holding.tradingAvailable ? <Link className="table-action-link" title={`거래 가능 시간: ${holding.tradingTimeDescription}`} to={`/investments/stocks/order/${holding.stockId}?investmentId=${holding.investmentId}`}>주문</Link> : <span className="status-chip is-closed" title={`거래 가능 시간: ${holding.tradingTimeDescription}`}>거래 시간 아님</span>}</td>
                          </tr>
                        );
                      })}</tbody>
                    </table>
                </div>
              )}
              <p className="portfolio-footer-links"><Link to="/investments">투자 홈</Link><Link to="/investments/stocks/search">종목 검색</Link></p>
            </>
          )}
        </section>
      </main>
    </div>
  );
}
