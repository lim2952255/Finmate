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
          <div className="page-heading"><h1>주문·체결 내역</h1><p>일반 주문, 예약 주문과 체결 결과를 확인합니다.</p></div>
          {!data && !error && <PageLoading message="주문과 체결 내역을 불러오고 있습니다." />}
          {error && <p className="overview-error" role="alert">{error.message}</p>}
          {data && (
            <>
              <form className="orders-toolbar"><label>증권계좌
                <select value={data.selectedInvestmentId || ""} onChange={(event) => navigate(event.target.value ? `/investments/orders?investmentId=${event.target.value}` : "/investments/orders")}>
                  <option value="">전체 계좌</option>
                  {data.accounts.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
                </select></label>
              </form>
              <HistorySection title="일반 주문" columns={["#", ...(data.allAccounts ? ["계좌"] : []), "종목", "주문", "상태", "수량", "가격", "일시", "관리"]} rows={data.orders} render={(item, index) => <><td className="number-cell"><span className="history-index">{index + 1}</span></td>{data.allAccounts && <AccountCell account={data.accounts.find((account) => account.id === item.investmentId)} />}<StockCell item={item} /><td><div className="history-badges"><span className={`badge badge-${item.side.toLowerCase()}`}>{sideLabels[item.side]}</span><span className="badge badge-neutral">{orderTypeLabels[item.orderType]}</span></div></td><td><span className={`badge badge-${item.status.toLowerCase().replaceAll("_", "-")}`}>{orderStatusLabels[item.status] || item.status}</span></td><td><div className="history-quantity"><strong>{formatDecimal(item.quantity)}</strong><span className="history-secondary-value">체결 {formatDecimal(item.executedQuantity)}</span></div></td><td className="money-cell"><div className="history-price"><strong>{item.price ? formatDecimal(item.price) : "시장가"}</strong>{item.averageExecutionPrice && <span className="history-secondary-value execution-price">평균 체결 {formatDecimal(item.averageExecutionPrice)}</span>}</div></td><td><div className="history-time"><strong>{formatDate(item.createdAt)}</strong><span className="history-secondary-value">만료 {formatDate(item.expiresAt)}</span></div></td><td>{["SUBMITTED", "PARTIALLY_FILLED"].includes(item.status) ? <button type="button" onClick={() => cancel("orders", item.id)}>취소</button> : <span className="history-secondary-value">처리 완료</span>}</td></>} />
              <HistorySection title="예약 주문" columns={["#", ...(data.allAccounts ? ["계좌"] : []), "종목", "예약 주문", "상태", "실행 조건", "수량", "만료", "관리"]} rows={data.reservations} render={(item, index) => <><td className="number-cell"><span className="history-index">{index + 1}</span></td>{data.allAccounts && <AccountCell account={data.accounts.find((account) => account.id === item.investmentId)} />}<StockCell item={item} /><td><div className="history-badges"><span className={`badge badge-${item.side.toLowerCase()}`}>{sideLabels[item.side]}</span><span className="badge badge-neutral">{orderTypeLabels[item.orderType]}</span></div></td><td><span className={`badge badge-${item.status.toLowerCase()}`}>{reservationStatusLabels[item.status] || item.status}</span></td><td><div className="history-price"><span className="badge badge-neutral">{conditionLabels[item.condition] || item.condition}</span><strong>{formatDecimal(item.triggerPrice)}</strong></div></td><td><strong>{formatDecimal(item.quantity)}</strong></td><td>{formatDate(item.expiresAt)}</td><td>{item.status === "ACTIVE" ? <button type="button" onClick={() => cancel("reservations", item.id)}>취소</button> : <span className="history-secondary-value">처리 완료</span>}</td></>} />
              <HistorySection title="체결 내역" columns={["#", ...(data.allAccounts ? ["계좌"] : []), "종목", "구분", "수량", "체결가", "정산금액", "체결시각"]} rows={data.trades} render={(item, index) => <><td className="number-cell"><span className="history-index">{index + 1}</span></td>{data.allAccounts && <AccountCell account={data.accounts.find((account) => account.id === item.investmentId)} />}<StockCell item={item} /><td><span className={`badge badge-${item.side.toLowerCase()}`}>{sideLabels[item.side]}</span></td><td><strong>{formatDecimal(item.quantity)}</strong></td><td className="money-cell"><strong className="execution-price">{formatDecimal(item.price)} {item.currency}</strong></td><td className="money-cell"><div className="history-price"><strong>{formatDecimal(item.netAmount, 2)} {item.currency}</strong><span className="history-secondary-value">수수료 {formatDecimal(item.commission, 2)} · 세금 {formatDecimal(item.tax, 2)}</span></div></td><td>{formatDate(item.executedAt)}</td></>} />
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

function HistorySection({ title, columns, rows, render }) {
  return (
    <section className="history-section">
      <div className="history-section-header"><div className="history-section-title"><h2>{title}</h2></div><span className="history-count">{rows.length}건</span></div>
      {rows.length === 0
        ? <p className="history-empty">내역이 없습니다.</p>
        : <div className="table-scroll"><table className="records-table history-table"><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((item, index) => <tr key={item.id}>{render(item, index)}</tr>)}</tbody></table></div>}
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

  return (
    <div className="page">
      <Header />
      <main className="main">
        <section className="content">
          {!data && !error && <PageLoading message="주문 가능 계좌와 시세를 확인하고 있습니다." />}
          {error && <p className="overview-error" role="alert">{error.message}</p>}
          {data && <><div className="page-heading"><h1>{data.stockName} 주문</h1><p>{data.symbol} · 현재가 {data.tradePrice || "-"} · 매수 {data.buyExecutablePrice || "-"} · 매도 {data.sellExecutablePrice || "-"} · {data.tradingTimeDescription}</p></div><div className="order-form-grid"><OrderForm data={data} onSubmit={(event) => submit(event, false)} /><OrderForm data={data} reservation onSubmit={(event) => submit(event, true)} /></div></>}
        </section>
      </main>
    </div>
  );
}

function OrderForm({ data, reservation, onSubmit }) {
  return (
    <form className="order-entry-form" onSubmit={onSubmit}>
      <h2>{reservation ? "예약 주문" : "일반 주문"}</h2>
      <label>증권계좌<select name="investmentId" defaultValue={data.defaultInvestmentId}>{data.accounts.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>
      <label>매수/매도<select name="side">{(data.sides || ["BUY", "SELL"]).map((value) => <option key={value} value={value}>{sideLabels[value] || value}</option>)}</select></label>
      <label>주문유형<select name="orderType">{(data.orderTypes || ["MARKET", "LIMIT"]).map((value) => <option key={value} value={value}>{orderTypeLabels[value] || value}</option>)}</select></label>
      <label>수량<input name="quantity" type="number" min="0" step="0.000001" required /></label>
      <label>주문가격<input name="orderPrice" type="number" min="0" step={data.inputStep || "0.01"} /></label>
      {reservation && <><label>실행조건<select name="triggerCondition">{(data.triggerConditions || ["PRICE_AT_OR_BELOW", "PRICE_AT_OR_ABOVE"]).map((value) => <option key={value} value={value}>{conditionLabels[value] || value}</option>)}</select></label><label>조건가격<input name="triggerPrice" type="number" min="0" step={data.inputStep || "0.01"} required /></label></>}
      <label>만료시각<input name="expiresAt" type="datetime-local" /></label>
      <button disabled={!data.tradingAvailable && !reservation}>{reservation ? "예약 등록" : "주문 접수"}</button>
    </form>
  );
}
