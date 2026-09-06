import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { getJson, postJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import PageLoading from "../components/common/PageLoading.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

const sideLabels = { BUY: "매수", SELL: "매도" };
const orderTypeLabels = { MARKET: "시장가", LIMIT: "지정가" };
const orderStatusLabels = { PENDING: "대기", SUBMITTED: "접수", PARTIALLY_FILLED: "부분체결", FILLED: "체결", CANCELED: "취소", EXPIRED: "만료", REJECTED: "거절" };
const reservationStatusLabels = { ACTIVE: "대기", TRIGGERED: "실행", CANCELED: "취소", EXPIRED: "만료", FAILED: "실패" };
const conditionLabels = { PRICE_AT_OR_BELOW: "가격 이하", PRICE_AT_OR_ABOVE: "가격 이상" };
const formatDate = (value) => value ? value.replace("T", " ").slice(0, 16) : "-";
const formatDecimal = (value, maximumFractionDigits = 6) => value == null ? "-" : Number(value).toLocaleString("ko-KR", { maximumFractionDigits });
const formatOrderPrice = (value, currency) => value == null || value === ""
  ? "-"
  : `${formatDecimal(value, 2)}${currency ? ` ${currency}` : ""}`;
const positiveRealtimePrice = (value) => {
  const price = Number(value);
  return Number.isFinite(price) && price > 0 ? String(value) : null;
};

export function TradingHistoryPage() {
  useDocumentTitle("주문·체결 내역 | FinMate");
  const location = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    getJson(`/api/trading/history${location.search}`, { signal: controller.signal })
      .then((history) => { setData(history); setError(null); })
      .catch((requestError) => {
        if (requestError.name !== "AbortError") setError(requestError);
      });
    return () => controller.abort();
  }, [location.search]);

  const cancel = async (kind, id) => {
    try {
      await postJson(`/api/trading/${kind}/${id}/cancel`, {});
      setData(await getJson(`/api/trading/history${location.search}`));
      setError(null);
    } catch (requestError) {
      setError(requestError);
    }
  };

  return (
    <div className="page">
      <Header />
      <main className="main">
        <section className="content">
          <div className="page-heading"><h1>주문·체결 내역</h1><p>주문, 예약 주문, 체결 내역을 계좌별 또는 전체 증권계좌 기준으로 확인합니다.</p></div>
          {!data && !error && <PageLoading message="주문과 체결 내역을 불러오고 있습니다." />}
          {error && <p className="overview-error" role="alert">{error.message}</p>}
          {data && (
            <>
              <form className="orders-toolbar" onSubmit={(event) => event.preventDefault()}><label>증권계좌
                <select value={data.selectedInvestmentId || ""} onChange={(event) => navigate(event.target.value ? `/investments/orders?investmentId=${event.target.value}` : "/investments/orders")}>
                  <option value="">전체 계좌</option>
                  {data.accounts.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
                </select></label><button type="submit">조회</button><Link className="button secondary" to="/investments/portfolio">포트폴리오</Link>
              </form>
              <HistorySection title="주문 내역" description="주문 조건과 실제 평균 체결가를 함께 확인할 수 있습니다." columns={["#", ...(data.allAccounts ? ["계좌"] : []), "종목", "주문", "상태", "수량", "가격", "일시", "관리"]} rows={data.orders} render={(item, index) => <><td className="number-cell"><span className="history-index">{index + 1}</span></td>{data.allAccounts && <AccountCell account={data.accounts.find((account) => account.id === item.investmentId)} />}<StockCell item={item} /><td><div className="history-badges"><span className={`badge badge-${item.side.toLowerCase()}`}>{sideLabels[item.side]}</span><span className="badge badge-neutral">{orderTypeLabels[item.orderType]}</span></div></td><td><span className={`badge badge-${item.status.toLowerCase().replaceAll("_", "-")}`}>{orderStatusLabels[item.status] || item.status}</span></td><td><div className="history-quantity"><span className="history-primary-value">{formatDecimal(item.quantity)}주</span><span className="history-secondary-value">체결 {formatDecimal(item.executedQuantity)}주</span></div></td><td className="money-cell"><div className="history-price"><span className="history-primary-value">{item.price ? `주문 ${formatDecimal(item.price)}` : "주문 시장가"}</span>{item.averageExecutionPrice ? <span className="history-secondary-value execution-price">평균 체결 {formatDecimal(item.averageExecutionPrice)}</span> : <span className="history-secondary-value">체결가 대기</span>}</div></td><td><div className="history-time"><span className="history-primary-value">{formatDate(item.createdAt)}</span><span className="history-secondary-value">{item.expiresAt ? `만료 ${formatDate(item.expiresAt)}` : "즉시 체결 주문"}</span></div></td><td>{["SUBMITTED", "PARTIALLY_FILLED"].includes(item.status) ? <button type="button" onClick={() => cancel("orders", item.id)}>주문 취소</button> : <span className="history-secondary-value">처리 완료</span>}</td></>} />
              <HistorySection title="예약 주문" description="조건 충족을 기다리는 주문과 실행 결과입니다." columns={["#", ...(data.allAccounts ? ["계좌"] : []), "종목", "예약 주문", "상태", "실행 조건", "수량", "만료", "관리"]} rows={data.reservations} render={(item, index) => <><td className="number-cell"><span className="history-index">{index + 1}</span></td>{data.allAccounts && <AccountCell account={data.accounts.find((account) => account.id === item.investmentId)} />}<StockCell item={item} /><td><div className="history-badges"><span className={`badge badge-${item.side.toLowerCase()}`}>{sideLabels[item.side]}</span><span className="badge badge-neutral">{orderTypeLabels[item.orderType]}</span></div></td><td><span className={`badge badge-${item.status.toLowerCase()}`}>{reservationStatusLabels[item.status] || item.status}</span></td><td><div className="history-price"><span className="badge badge-neutral">{conditionLabels[item.condition] || item.condition}</span><span className="history-primary-value">{formatDecimal(item.triggerPrice)}</span></div></td><td><span className="history-primary-value">{formatDecimal(item.quantity)}주</span></td><td><span className="history-primary-value">{formatDate(item.expiresAt)}</span></td><td>{item.status === "ACTIVE" ? <button type="button" onClick={() => cancel("reservations", item.id)}>예약 취소</button> : <span className="history-secondary-value">처리 완료</span>}</td></>} />
              <HistorySection title="체결 내역" description="실제 체결가격과 수수료·세금이 반영된 정산금액입니다." columns={["#", ...(data.allAccounts ? ["계좌"] : []), "종목", "구분", "수량", "체결가", "정산금액", "체결시각"]} rows={data.trades} render={(item, index) => <><td className="number-cell"><span className="history-index">{index + 1}</span></td>{data.allAccounts && <AccountCell account={data.accounts.find((account) => account.id === item.investmentId)} />}<StockCell item={item} /><td><span className={`badge badge-${item.side.toLowerCase()}`}>{sideLabels[item.side]}</span></td><td><span className="history-primary-value">{formatDecimal(item.quantity)}주</span></td><td className="money-cell"><span className="history-primary-value execution-price">{formatDecimal(item.price)} {item.currency}</span></td><td className="money-cell"><div className="history-price"><span className="history-primary-value">{formatDecimal(item.netAmount, 2)} {item.currency}</span><span className="history-secondary-value">수수료 {formatDecimal(item.commission, 2)} · 세금 {formatDecimal(item.tax, 2)}</span></div></td><td><span className="history-primary-value">{formatDate(item.executedAt)}</span></td></>} />
              <p className="history-footer-links"><Link className="button secondary" to="/investments">투자 홈</Link><Link className="button secondary" to="/investments/portfolio">포트폴리오</Link></p>
            </>
          )}
        </section>
      </main>
    </div>
  );
}

function AccountCell({ account }) {
  return <td><div className="account-cell"><span className="primary-line">{account?.companyName || "-"}</span><span className="secondary-line">{account?.accountNumber || "-"}</span></div></td>;
}

function StockCell({ item }) {
  return <td><div className="stock-cell"><Link className="primary-line" to={`/investments/stocks/detail?stockId=${item.stockId}`}>{item.stockName}</Link><span className="secondary-line">{item.symbol}</span></div></td>;
}

function HistorySection({ title, description, columns, rows, render }) {
  return (
    <section className="history-section">
      <div className="history-section-header"><div className="history-section-title"><h2>{title}</h2><p>{description}</p></div><span className="history-count">{rows.length}건</span></div>
      {rows.length === 0
        ? <p className="history-empty">내역이 없습니다.</p>
        : <div className="table-scroll"><table className="records-table history-table"><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((item, index) => <tr key={item.id} data-side={item.side}>{render(item, index)}</tr>)}</tbody></table></div>}
    </section>
  );
}

export function OrderPage() {
  const { stockId } = useParams();
  useDocumentTitle("주문 | FinMate");
  const navigate = useNavigate();
  const location = useLocation();
  const selectedId = new URLSearchParams(location.search).get("investmentId");
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    getJson(`/api/trading/order-page/${stockId}${selectedId ? `?investmentId=${selectedId}` : ""}`, { signal: controller.signal })
      .then((pageData) => { setData(pageData); setError(null); })
      .catch((requestError) => {
        if (requestError.name !== "AbortError") setError(requestError);
      });
    return () => controller.abort();
  }, [selectedId, stockId]);

  useEffect(() => {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/stocks`);
    socket.addEventListener("open", () => socket.send(JSON.stringify({ type: "SUBSCRIBE_ORDER_STOCK", stockId: Number(stockId) })));
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (Number(message.stockId) !== Number(stockId)) return;

      if (message.type === "STOCK_TRADE") {
        setData((current) => {
          if (!current) return current;
          const tradePrice = positiveRealtimePrice(message.currentPrice) ?? current.tradePrice;
          const buyExecutablePrice = positiveRealtimePrice(message.bestAskPrice) ?? current.buyExecutablePrice ?? tradePrice;
          const sellExecutablePrice = positiveRealtimePrice(message.bestBidPrice) ?? current.sellExecutablePrice ?? tradePrice;
          return {
            ...current,
            tradePrice,
            buyExecutablePrice,
            sellExecutablePrice,
            // 체결가와 양방향 주문 기준가가 모두 준비되면 주문 버튼을 즉시 활성화한다.
            realtimePriceAvailable: Boolean(tradePrice && buyExecutablePrice && sellExecutablePrice)
          };
        });
      }

      if (message.type === "STOCK_ORDERBOOK") {
        setData((current) => {
          if (!current) return current;
          const buyExecutablePrice = positiveRealtimePrice(message.askLevels?.[0]?.price) ?? current.buyExecutablePrice;
          const sellExecutablePrice = positiveRealtimePrice(message.bidLevels?.[0]?.price) ?? current.sellExecutablePrice;
          return {
            ...current,
            buyExecutablePrice,
            sellExecutablePrice,
            realtimePriceAvailable: Boolean(current.tradePrice && buyExecutablePrice && sellExecutablePrice)
          };
        });
      }
    });
    return () => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "UNSUBSCRIBE_ORDER_STOCK", stockId: Number(stockId) }));
      socket.close();
    };
  }, [stockId]);

  const submit = async (event, reservation) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    const body = Object.fromEntries(Object.entries({
      ...form,
      stockId,
      investmentId: Number(form.investmentId),
      quantity: form.quantity,
      ...(reservation ? { triggerPrice: form.triggerPrice, triggerCondition: form.triggerCondition } : {})
    }).filter(([, value]) => value !== ""));

    try {
      await postJson(reservation ? "/api/trading/reservations" : "/api/trading/orders", body);
      navigate(`/investments/orders?investmentId=${body.investmentId}`);
    } catch (requestError) {
      setError(requestError);
    }
  };

  // 일반 주문은 거래 시간과 실시간 시세가 모두 준비된 경우에만 접수할 수 있다.
  const orderSubmissionAvailable = Boolean(data?.tradingAvailable && data?.realtimePriceAvailable);

  return (
    <div className="page">
      <Header />
      <main className="main">
        <section className="content">
          {!data && !error && <PageLoading message="주문 가능 계좌와 시세를 확인하고 있습니다." />}
          {error && <p className="overview-error" role="alert">{error.message}</p>}
          {data && <>
            <div className="page-heading order-page-heading">
              <span className="eyebrow">STOCK ORDER</span>
              <div className="order-title-row">
                <div><h1>{data.stockName} 주문</h1><p className="order-symbol">{data.symbol}{data.currency ? ` · ${data.currency}` : ""}</p></div>
                <span className={`order-market-status ${orderSubmissionAvailable ? "open" : "closed"}`}>{!data.tradingAvailable ? "예약 주문 가능" : data.realtimePriceAvailable ? "거래 가능" : "시세 수신 대기"}</span>
              </div>
              <div className="order-quote-grid" aria-label="주문 기준 시세">
                <div className="order-quote-card current"><span>현재가</span><strong>{formatOrderPrice(data.tradePrice, data.currency)}</strong></div>
                <div className="order-quote-card buy"><span>매수 기준가</span><strong>{formatOrderPrice(data.buyExecutablePrice, data.currency)}</strong></div>
                <div className="order-quote-card sell"><span>매도 기준가</span><strong>{formatOrderPrice(data.sellExecutablePrice, data.currency)}</strong></div>
              </div>
              {!data.realtimePriceAvailable && <p className="order-quote-unavailable" role="status">실시간 주가정보가 수신되지 않아 일반 주문은 사용할 수 없습니다. 예약 주문은 등록할 수 있습니다.</p>}
              <TradingHours description={data.tradingTimeDescription} />
            </div>
            <div className="order-form-grid"><OrderForm data={data} submissionAvailable={orderSubmissionAvailable} onSubmit={(event) => submit(event, false)} /><OrderForm data={data} reservation submissionAvailable onSubmit={(event) => submit(event, true)} /></div>
          </>}
        </section>
      </main>
    </div>
  );
}

function TradingHours({ description }) {
  const venues = String(description || "-").split(" / ").map((session) => {
    const separatorIndex = session.indexOf(" ");
    return separatorIndex < 0
      ? { name: session, hours: "" }
      : { name: session.slice(0, separatorIndex), hours: session.slice(separatorIndex + 1).replace("대한민국 시간 기준 ", "") };
  });

  return (
    <section className="order-trading-hours" aria-label="거래 가능 시간">
      <div className="order-trading-hours-heading"><strong>거래 가능 시간</strong><span>각 거래소의 주문 접수 시간입니다.</span></div>
      <div className="order-trading-hours-grid">
        {venues.map((venue, index) => <div className="order-trading-venue" key={`${venue.name}-${index}`}><strong>{venue.name}</strong><span>{venue.hours}</span></div>)}
      </div>
    </section>
  );
}

function OrderForm({ data, reservation, submissionAvailable, onSubmit }) {
  const [investmentId, setInvestmentId] = useState(String(data.defaultInvestmentId ?? data.accounts?.[0]?.id ?? ""));
  const [side, setSide] = useState(data.sides?.[0] || "BUY");
  const [orderType, setOrderType] = useState(data.orderTypes?.[0] || "MARKET");
  const [limitPrice, setLimitPrice] = useState("");
  const marketOrder = orderType === "MARKET";
  const displayedOrderPrice = marketOrder ? (reservation ? "" : data.tradePrice ?? "") : limitPrice;
  const accountSummary = data.summaries?.find((summary) => String(summary.investmentId) === investmentId);

  return (
    <form className="order-entry-form" onSubmit={onSubmit}>
      <h2>{reservation ? "예약 주문" : "일반 주문"}</h2>
      <label>증권계좌<select name="investmentId" value={investmentId} onChange={(event) => setInvestmentId(event.target.value)}>{data.accounts.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>
      {accountSummary && <div className="order-account-summary" aria-label="선택 계좌 주문 가능 자산">
        <span>주문 가능 예수금 <strong>{formatOrderPrice(accountSummary.availableCashBalance, data.currency)}</strong></span>
        <span>매도 가능 수량 <strong>{formatDecimal(accountSummary.availableQuantity)}주</strong></span>
      </div>}
      <label>매수/매도<select name="side" value={side} onChange={(event) => setSide(event.target.value)}>{(data.sides || ["BUY", "SELL"]).map((value) => <option key={value} value={value}>{sideLabels[value] || value}</option>)}</select></label>
      <label>주문유형<select name="orderType" value={orderType} onChange={(event) => setOrderType(event.target.value)}>{(data.orderTypes || ["MARKET", "LIMIT"]).map((value) => <option key={value} value={value}>{orderTypeLabels[value] || value}</option>)}</select></label>
      <label>수량<input name="quantity" type="number" min="0" step="0.000001" required /></label>
      <label>{marketOrder ? (reservation ? "주문가격 (실행 시 결정)" : "주문가격 (현재가 기준)") : "주문가격"}
        <input
          name="orderPrice"
          type="number"
          min="0"
          step={data.inputStep || "0.01"}
          value={displayedOrderPrice}
          onChange={(event) => setLimitPrice(event.target.value)}
          disabled={marketOrder}
          required={!marketOrder}
          placeholder={marketOrder && reservation ? "실행 시점의 시장가 적용" : undefined}
        />
        {marketOrder && <small className="order-field-note">{reservation ? "조건 충족 후 주문이 실행되는 시점의 시장가가 적용됩니다." : "현재가는 참고 기준이며 실제 체결가는 호가 상황에 따라 달라질 수 있습니다."}</small>}
      </label>
      {reservation && <><label>실행조건<select name="triggerCondition">{(data.triggerConditions || ["PRICE_AT_OR_BELOW", "PRICE_AT_OR_ABOVE"]).map((value) => <option key={value} value={value}>{conditionLabels[value] || value}</option>)}</select></label><label>조건가격<input name="triggerPrice" type="number" min="0" step={data.inputStep || "0.01"} required /></label></>}
      <label>만료시각<input name="expiresAt" type="datetime-local" disabled={!reservation && marketOrder} required={reservation || !marketOrder} /></label>
      <button disabled={!submissionAvailable}>{reservation ? "예약 등록" : "주문 접수"}</button>
    </form>
  );
}
