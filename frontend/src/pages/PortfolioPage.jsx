import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import PageLoading from "../components/common/PageLoading.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import "../styles/portfolio.css";

const SEPARATED = "SEPARATED";
const DOMESTIC = "DOMESTIC";
const OVERSEAS = "OVERSEAS";
const ALLOCATION_COLORS = ["#315bea", "#0ea5e9", "#f59e0b", "#f97316", "#8b5cf6", "#ec4899", "#84cc16"];
const DONUT_CENTER = { x: 150, y: 90 };
const DONUT_RADIUS = 64;
const DONUT_LABEL_RADIUS = 76;
const DONUT_LABEL_LIMIT = 6;
const DONUT_LABEL_MIN_Y = 18;
const DONUT_LABEL_MAX_Y = 162;
const DONUT_LABEL_GAP = 23;

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

function isDomesticMarket(market) {
  return market === "KOSPI" || market === "KOSDAQ";
}

function distributeDonutLabels(labels) {
  const positioned = [...labels].sort((left, right) => left.anchorY - right.anchorY);
  positioned.forEach((label, index) => {
    const previousY = positioned[index - 1]?.labelY ?? DONUT_LABEL_MIN_Y - DONUT_LABEL_GAP;
    label.labelY = Math.max(DONUT_LABEL_MIN_Y, label.anchorY, previousY + DONUT_LABEL_GAP);
  });

  if (positioned.at(-1)?.labelY > DONUT_LABEL_MAX_Y) {
    positioned[positioned.length - 1].labelY = DONUT_LABEL_MAX_Y;
    for (let index = positioned.length - 2; index >= 0; index -= 1) {
      positioned[index].labelY = Math.min(positioned[index].labelY, positioned[index + 1].labelY - DONUT_LABEL_GAP);
    }
  }

  return positioned;
}

function formatDonutLabelName(name) {
  return name.length > 7 ? `${name.slice(0, 6)}…` : name;
}

function buildDonutLabels(items) {
  const visibleItems = new Set([...items]
    .sort((left, right) => right.percentage - left.percentage)
    .slice(0, DONUT_LABEL_LIMIT));
  let offset = 0;
  const labels = items.flatMap((item) => {
    const middleAngle = -90 + (offset + item.percentage / 2) * 3.6;
    offset += item.percentage;
    if (!visibleItems.has(item)) return [];

    const radians = middleAngle * Math.PI / 180;
    const anchorX = DONUT_CENTER.x + Math.cos(radians) * DONUT_LABEL_RADIUS;
    const anchorY = DONUT_CENTER.y + Math.sin(radians) * DONUT_LABEL_RADIUS;
    return [{ ...item, anchorX, anchorY, side: anchorX >= DONUT_CENTER.x ? "right" : "left" }];
  });

  return [
    ...distributeDonutLabels(labels.filter((label) => label.side === "left")),
    ...distributeDonutLabels(labels.filter((label) => label.side === "right"))
  ];
}

function AllocationDonut({ allocation, marketView, onMarketViewChange }) {
  if (!allocation.items.length && !allocation.unavailable) return null;
  const donutLabels = buildDonutLabels(allocation.items);

  return (
    <section className="portfolio-allocation" aria-labelledby="portfolio-allocation-title">
      <div className="portfolio-allocation-header">
        <div>
          <span className="eyebrow">ASSET MIX</span>
          <h2 id="portfolio-allocation-title">테마별 자산 비중</h2>
          <p>선택한 시장은 업종별로, 반대 시장은 하나의 비중으로 묶어 보여줍니다.</p>
        </div>
        <div className="portfolio-market-view" role="group" aria-label="테마 비중 시장 선택">
          <button type="button" className={marketView === DOMESTIC ? "is-active" : ""} aria-pressed={marketView === DOMESTIC} onClick={() => onMarketViewChange(DOMESTIC)}>국내 종목 비중</button>
          <button type="button" className={marketView === OVERSEAS ? "is-active" : ""} aria-pressed={marketView === OVERSEAS} onClick={() => onMarketViewChange(OVERSEAS)}>해외 종목 비중</button>
        </div>
      </div>

      {allocation.unavailable ? (
        <p className="portfolio-allocation-empty" role="status">USD/KRW 환율을 조회하지 못해 국내·해외 통합 비중을 계산할 수 없습니다.</p>
      ) : (
        <div className="portfolio-allocation-body">
          <div className="portfolio-donut-wrap">
            <svg className="portfolio-donut" viewBox="0 0 300 180" role="img" aria-label={allocation.items.map((item) => `${item.name} ${item.percentage.toFixed(2)}%`).join(", ")}>
              <circle className="portfolio-donut-track" cx={DONUT_CENTER.x} cy={DONUT_CENTER.y} r={DONUT_RADIUS} pathLength="100" />
              {allocation.items.map((item, index) => {
                const currentOffset = allocation.items
                  .slice(0, index)
                  .reduce((sum, previous) => sum + previous.percentage, 0);
                return (
                  <circle key={`${item.kind}-${item.name}`} className="portfolio-donut-slice" cx={DONUT_CENTER.x} cy={DONUT_CENTER.y} r={DONUT_RADIUS} pathLength="100"
                    stroke={item.color} strokeDasharray={`${item.percentage} ${100 - item.percentage}`} strokeDashoffset={-currentOffset}
                    transform={`rotate(-90 ${DONUT_CENTER.x} ${DONUT_CENTER.y})`}>
                    <title>{item.name} {item.percentage.toFixed(2)}%</title>
                  </circle>
                );
              })}
              <g className="portfolio-donut-labels" aria-hidden="true">
                {donutLabels.map((item) => {
                  const right = item.side === "right";
                  const elbowX = right ? 230 : 70;
                  const lineEndX = right ? 292 : 8;
                  const textX = right ? 288 : 12;
                  return (
                    <g key={`${item.kind}-${item.name}`}>
                      <polyline points={`${item.anchorX},${item.anchorY} ${elbowX},${item.labelY} ${lineEndX},${item.labelY}`} />
                      <circle cx={item.anchorX} cy={item.anchorY} r="2.2" fill={item.color} />
                      <text x={textX} y={item.labelY - 4} textAnchor={right ? "end" : "start"}>{formatDonutLabelName(item.name)}</text>
                      <text className="portfolio-donut-label-value" x={textX} y={item.labelY + 7} textAnchor={right ? "end" : "start"}>{item.percentage.toFixed(2)}%</text>
                    </g>
                  );
                })}
              </g>
              <g className="portfolio-donut-center" aria-hidden="true">
                <text className="portfolio-donut-center-value" x={DONUT_CENTER.x} y={DONUT_CENTER.y - 2}>100%</text>
                <text className="portfolio-donut-center-caption" x={DONUT_CENTER.x} y={DONUT_CENTER.y + 13}>{marketView === DOMESTIC ? "국내 기준" : "해외 기준"}</text>
              </g>
            </svg>
          </div>
          <ul className="portfolio-allocation-legend">
            {allocation.items.map((item) => (
              <li key={`${item.kind}-${item.name}`}>
                <span className="portfolio-allocation-swatch" style={{ backgroundColor: item.color }} aria-hidden="true" />
                <span className="portfolio-allocation-name">{item.name}</span>
                <strong>{item.percentage.toFixed(2)}%</strong>
                <small>{formatNumber(item.amount, 0)} KRW</small>
              </li>
            ))}
          </ul>
        </div>
      )}
      {!allocation.unavailable && <p className="portfolio-allocation-note">총 구성자산 {formatNumber(allocation.total, 0)} KRW · 종목은 매입금액, 현금은 주문 예약금을 포함한 총 예수금을 원화로 환산해 계산합니다.</p>}
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
  const [marketView, setMarketView] = useState(DOMESTIC);
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

  const allocation = useMemo(() => {
    const selectedThemes = new Map();
    let counterpartAmount = 0;
    let cashAmount = 0;
    let unavailable = false;

    const toKrw = (amount, currency) => {
      const converted = convertCurrency(amount, currency, "KRW", usdKrwRate);
      if (!Number.isFinite(converted)) unavailable = true;
      return converted;
    };

    holdings.forEach((holding) => {
      const purchaseAmount = toKrw(holding.averagePriceValue * holding.quantityValue, holding.currency);
      if (!Number.isFinite(purchaseAmount) || purchaseAmount <= 0) return;
      const domestic = isDomesticMarket(holding.market);
      const selected = marketView === DOMESTIC ? domestic : !domestic;
      if (selected) {
        const theme = holding.industry && holding.industry !== "없음" ? holding.industry : "미분류";
        selectedThemes.set(theme, (selectedThemes.get(theme) || 0) + purchaseAmount);
      } else {
        counterpartAmount += purchaseAmount;
      }
    });

    (data?.cashBalances || []).forEach((balance) => {
      const nativeAmount = toNumber(balance.totalBalance);
      if (!Number.isFinite(nativeAmount) || nativeAmount <= 0) return;
      const amount = toKrw(nativeAmount, balance.currency);
      if (Number.isFinite(amount) && amount > 0) cashAmount += amount;
    });

    if (unavailable) return { items: [], total: 0, unavailable: true };

    const themeItems = [...selectedThemes.entries()]
      .map(([name, amount]) => ({ name, amount, kind: "theme" }))
      .sort((left, right) => right.amount - left.amount || left.name.localeCompare(right.name, "ko"));
    const rawItems = themeItems.slice(0, 7);
    const otherThemesAmount = themeItems.slice(7).reduce((sum, item) => sum + item.amount, 0);
    if (otherThemesAmount > 0) rawItems.push({ name: "기타", amount: otherThemesAmount, kind: "other-themes" });
    if (counterpartAmount > 0) rawItems.push({ name: marketView === DOMESTIC ? "해외" : "국내", amount: counterpartAmount, kind: "counterpart" });
    if (cashAmount > 0) rawItems.push({ name: "현금", amount: cashAmount, kind: "cash" });
    rawItems.sort((left, right) => right.amount - left.amount || left.name.localeCompare(right.name, "ko"));
    let themeColorIndex = 0;
    const total = rawItems.reduce((sum, item) => sum + item.amount, 0);
    return {
      total,
      unavailable: false,
      items: rawItems.map((item) => ({
        ...item,
        percentage: total ? item.amount / total * 100 : 0,
        color: item.kind === "cash"
          ? "#14b8a6"
          : item.kind === "counterpart"
            ? "#64748b"
            : item.kind === "other-themes"
              ? "#94a3b8"
              : ALLOCATION_COLORS[themeColorIndex++ % ALLOCATION_COLORS.length]
      }))
    };
  }, [data, holdings, marketView, usdKrwRate]);

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
              <AllocationDonut allocation={allocation} marketView={marketView} onMarketViewChange={setMarketView} />

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
