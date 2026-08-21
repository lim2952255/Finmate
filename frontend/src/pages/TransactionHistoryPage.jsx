import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import Pagination from "../components/stocks/Pagination.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import { formatMoney } from "../utils/moneyFormatting.js";

const configurations = {
  account: { title: "거래내역", description: "일반 계좌의 입출금과 이체 흐름을 기간별로 확인합니다.", listTitle: "거래내역 목록", api: "/api/history/accounts", accountParam: "accountNumber" },
  cash: { title: "예수금 입출금 내역", description: "증권계좌 예수금의 입금과 출금 흐름을 확인합니다.", listTitle: "예수금 거래내역 목록", api: "/api/history/cash", accountParam: "investmentNumber" },
  exchange: { title: "환전 내역", description: "증권계좌에서 처리한 통화별 환전 내역을 확인합니다.", listTitle: "환전 내역 목록", api: "/api/history/exchanges", accountParam: "investmentId" }
};

const typeLabels = {
  TRANSFER_OUT: "이체출금", TRANSFER_IN: "이체입금", DEPOSIT: "입금", WITHDRAW: "출금", EXCHANGE: "환전"
};

const formatDate = (value) => value?.replace("T", " ").slice(0, 16) || "-";
const sign = (type) => ["TRANSFER_OUT", "WITHDRAW"].includes(type) ? "-" : "+";

function AccountSelector({ type, data, onSelect, onClear }) {
  return (
    <section className="account-selector">
      <h2>{type === "account" ? "계좌 선택" : "증권계좌 선택"}</h2>
      <p className="action-row">
        <button className="button secondary" type="button" onClick={onClear}>
          전체 {type === "account" ? "계좌" : "증권계좌"}
        </button>
      </p>
      <div className="table-scroll">
        <table className="records-table">
          <thead><tr><th>번호</th><th>{type === "account" ? "계좌" : "증권계좌"}</th><th>{type === "account" ? "통화" : "예수금"}</th><th>조회</th></tr></thead>
          <tbody>{data.accounts.map((account, index) => (
            <tr key={account.id}>
              <td className="number-cell">{index + 1}</td>
              <td><div className="account-cell"><span className="primary-line">{account.companyName}</span><span className="secondary-line">{account.accountNumber}</span></div></td>
              <td>{type === "account" ? <span className="badge badge-neutral">{account.currency}</span> : <ul className="compact-list">{account.cashBalances.map((balance) => <li key={balance.currency}><strong>{balance.currency}</strong> {formatMoney(balance.availableBalance)}</li>)}</ul>}</td>
              <td><button className="button secondary" type="button" onClick={() => onSelect(account)}>내역 조회</button></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  );
}

function Summary({ summaries }) {
  if (!summaries?.length) return null;
  const summaryValue = (value, kind) => {
    const numeric = Number(value);
    if (numeric === 0) return { className: "neutral", text: formatMoney(value) };
    if (kind === "deposit") return { className: "positive", text: `+${formatMoney(value)}` };
    if (kind === "withdrawal") return { className: "negative", text: `-${formatMoney(value)}` };
    return { className: numeric > 0 ? "positive" : "negative", text: `${numeric > 0 ? "+" : ""}${formatMoney(value)}` };
  };
  return (
    <section>
      <h2>거래 요약</h2>
      <div className="currency-summary-list">{summaries.map((summary, index) => {
        const deposit = summaryValue(summary.totalDepositAmount, "deposit");
        const withdrawal = summaryValue(summary.totalWithdrawalAmount, "withdrawal");
        const net = summaryValue(summary.netAmount, "net");
        return <article className="currency-summary-card" key={summary.currency || index}>
          <div className="currency-summary-header"><span className="badge badge-neutral">{summary.currency || "전체"}</span></div>
          <div className="summary-grid">
            <div className="metric-card"><span className="metric-label">총 입금액</span><strong className={`metric-value ${deposit.className}`}>{deposit.text}</strong></div>
            <div className="metric-card"><span className="metric-label">총 출금액</span><strong className={`metric-value ${withdrawal.className}`}>{withdrawal.text}</strong></div>
            <div className="metric-card"><span className="metric-label">순액</span><strong className={`metric-value ${net.className}`}>{net.text}</strong></div>
          </div>
        </article>;
      })}</div>
    </section>
  );
}

function TransactionRows({ type, rows, pageNumber }) {
  if (!rows.length) return <div className="empty-state"><strong>조회 기간에 해당하는 내역이 없습니다.</strong></div>;
  if (type === "exchange") {
    return <div className="table-scroll"><table className="records-table transaction-table exchange-transaction-table"><thead><tr><th>번호</th><th>환전일시</th><th>증권계좌</th><th>환전 방향</th><th>환전 금액</th><th>적용 환율</th><th>출금 통화 잔고</th><th>입금 통화 잔고</th></tr></thead><tbody>{rows.map((row, index) => <tr key={`${row.date}-${index}`}><td className="number-cell">{pageNumber * 20 + index + 1}</td><td className="date-cell">{formatDate(row.date)}</td><td><div className="account-cell"><span className="primary-line">{row.accountName}</span><span className="secondary-line">{row.accountNumber}</span></div></td><td><span className="badge badge-neutral">{row.currency} → {row.toCurrency}</span></td><td><div className="exchange-flow"><span className="exchange-flow-value">{formatMoney(row.amount)} {row.currency}</span><span className="exchange-flow-arrow">→</span><span className="exchange-flow-value">{formatMoney(row.toAmount)} {row.toCurrency}</span></div></td><td className="money-cell">{Number(row.exchangeRate).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}</td><td><div className="balance-flow"><span>{formatMoney(row.beforeAmount)}</span><span>→</span><strong>{formatMoney(row.afterAmount)} {row.currency}</strong></div></td><td><div className="balance-flow"><span>{formatMoney(row.toBalanceBefore)}</span><span>→</span><strong>{formatMoney(row.toBalanceAfter)} {row.toCurrency}</strong></div></td></tr>)}</tbody></table></div>;
  }
  return <div className="table-scroll"><table className="records-table transaction-table"><thead><tr><th>번호</th><th>거래일시</th><th>{type === "account" ? "계좌" : "증권계좌"}</th><th>거래유형</th><th>거래금액</th><th>거래 전 잔액</th><th>거래 후 잔액</th><th>상대 기관</th><th>상대 계좌</th><th>상대 이름</th><th>설명</th></tr></thead><tbody>{rows.map((row, index) => <tr key={`${row.date}-${index}`}><td className="number-cell">{pageNumber * 20 + index + 1}</td><td className="date-cell">{formatDate(row.date)}</td><td><div className="account-cell"><span className="primary-line">{row.accountName}</span><span className="secondary-line">{row.accountNumber}</span></div></td><td><span className={`badge ${sign(row.type) === "+" ? "badge-deposit" : "badge-withdraw"}`}>{typeLabels[row.type] || row.type}</span></td><td className={`money-cell ${sign(row.type) === "+" ? "positive" : "negative"}`}>{sign(row.type)}{formatMoney(row.amount)} {row.currency}</td><td className="balance-cell">{formatMoney(row.beforeAmount)} {row.currency}</td><td className="balance-cell">{formatMoney(row.afterAmount)} {row.currency}</td><td>{row.counterpartyCompany || "-"}</td><td>{row.counterpartyAccountNumber || "-"}</td><td>{row.counterpartyName || "-"}</td><td>{row.detail || "-"}</td></tr>)}</tbody></table></div>;
}

export default function TransactionHistoryPage({ type }) {
  const config = configurations[type];
  useDocumentTitle(`${config.title} | FinMate`);
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState({ status: "loading", data: null, error: null });

  useEffect(() => {
    const controller = new AbortController();
    getJson(`${config.api}${location.search}`, { signal: controller.signal })
      .then((data) => setState({ status: "success", data, error: null }))
      .catch((error) => error.name !== "AbortError" && setState({ status: "error", data: null, error }));
    return () => controller.abort();
  }, [config.api, location.search]);

  const selected = state.data?.accounts.find((item) => String(item.id) === state.data.selected || item.accountNumber === state.data.selected);
  const createParams = (account = selected, period = state.data.period) => {
    const params = new URLSearchParams();
    if (account) {
      params.set(config.accountParam, type === "exchange" ? account.id : account.accountNumber);
      if (type === "account") params.set("bankCode", account.companyCode);
      if (type === "cash") params.set("securitiesCompanyCode", account.companyCode);
    }
    params.set("period", period);
    params.set("page", "0");
    return params;
  };
  const selectAccount = (account) => navigate(`${location.pathname}?${createParams(account)}`);
  const clearAccount = () => navigate(`${location.pathname}?${createParams(null)}`);
  const update = (event) => { event.preventDefault(); navigate(`${location.pathname}?${createParams(selected, new FormData(event.currentTarget).get("period"))}`); };
  const pageUrl = (page) => { const params = new URLSearchParams(location.search); params.set("page", page); return `${location.pathname}?${params}`; };

  return <div className="page"><Header /><main className="main"><section className="content transaction-page"><div className="page-heading"><span className="eyebrow">TRANSACTION HISTORY</span><h1>{config.title}</h1><p>{config.description}</p></div>
    {state.status === "loading" && <p>불러오는 중입니다.</p>}{state.error && <p className="overview-error">{state.error.message}</p>}
    {state.data && <><AccountSelector type={type} data={state.data} onSelect={selectAccount} onClear={clearAccount} /><section className="filter-panel"><h2>조회 조건</h2><p className="selected-context">{selected ? `${selected.companyName} ${selected.accountNumber}` : `전체 ${type === "account" ? "계좌" : "증권계좌"}`}의 내역을 조회합니다.</p><form onSubmit={update}><label>기간<select name="period" defaultValue={state.data.period}>{state.data.periods.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><button>조회</button></form></section><Summary summaries={state.data.summaries} /><section className="transaction-list"><h2>{config.listTitle}</h2><p>최신 거래부터 표시합니다.</p><TransactionRows type={type} rows={state.data.rows} pageNumber={state.data.page.number} /></section><Pagination page={state.data.page} createUrl={pageUrl} label={`${config.title} 페이지`} /><p className="action-row"><Link className="button secondary" to="/accounts">계좌 홈</Link><Link className="button secondary" to="/investments">투자 홈</Link></p></>}
  </section></main></div>;
}
