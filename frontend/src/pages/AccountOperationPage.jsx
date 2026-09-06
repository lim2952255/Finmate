import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson, postJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

const titles = { transfer: "계좌 이체", deposit: "증권계좌 투자금 입금", withdraw: "증권계좌 투자금 출금", exchange: "증권계좌 환전", limit: "이체한도 변경" };
const descriptions = {
  transfer: "일반 계좌로 송금합니다.",
  deposit: "일반계좌의 자금을 증권계좌 예수금으로 이동합니다.",
  withdraw: "증권계좌 예수금을 본인 일반계좌로 이동합니다.",
  exchange: "증권계좌 안의 KRW와 USD 예수금을 최신 USD/KRW 환율 기준으로 환전합니다.",
  limit: "계좌별 1회 및 일일 이체한도를 확인하고 변경합니다."
};

const amountText = (amount, currency) => `${Number(amount || 0).toLocaleString("ko-KR", { maximumFractionDigits: currency === "KRW" ? 0 : 2 })} ${currency}`;

function FieldError({ error, names }) {
  const fields = Array.isArray(names) ? names : [names];
  const messages = [...new Set(fields.map((name) => error?.fieldErrors?.[name]).filter(Boolean))];
  return messages.map((message) => <p className="field-error" role="alert" key={message}>{message}</p>);
}

export default function AccountOperationPage({ type }) {
  useDocumentTitle(`${titles[type]} | FinMate`);
  const location = useLocation();
  const navigate = useNavigate();
  const query = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState("");
  const [depositInvestmentId, setDepositInvestmentId] = useState("");
  const [fromCurrency, setFromCurrency] = useState("KRW");
  const [toCurrency, setToCurrency] = useState("USD");

  useEffect(() => {
    const controller = new AbortController();
    getJson(type === "exchange" ? "/api/account-operations/exchange" : "/api/account-operations", { signal: controller.signal })
      .then((response) => {
        setData(response);
        const requestedAccount = query.get("from");
        const requestedInvestment = query.get("investmentNumber");
        const choices = type === "withdraw" || type === "exchange" ? response.investments : response.accounts;
        // 예수금 입금의 investmentNumber는 입금 대상이다. 출금 계좌는 명시한 계좌, 대표 일반계좌 순으로 선택한다.
        const requestedSource = type === "deposit" ? requestedAccount : (requestedInvestment || requestedAccount);
        const selected = choices.find((item) => item.accountNumber === requestedSource)
          || (type === "deposit" && choices.find((item) => item.primary))
          || choices[0];
        setSelectedId(selected ? String(selected.id) : "");
        if (type === "deposit") {
          const target = response.investments.find((item) => item.accountNumber === requestedInvestment)
            || response.investments.find((item) => item.primary)
            || response.investments[0];
          setDepositInvestmentId(target ? String(target.id) : "");
        }
      })
      .catch((requestError) => {
        if (requestError.name !== "AbortError") setError(requestError);
      });
    return () => controller.abort();
  }, [query, type]);

  const selectedInvestment = data?.investments?.find((item) => String(item.id) === selectedId);
  const selectedAccount = data?.accounts?.find((item) => String(item.id) === selectedId);
  const investmentDepositUrl = selectedAccount
    ? `/accounts/transfer-investment?from=${encodeURIComponent(selectedAccount.accountNumber)}&fromBankCode=${selectedAccount.companyCode}`
    : "/accounts/transfer-investment";
  const selectedBalance = selectedInvestment?.balances?.find((balance) => balance.currency === fromCurrency);
  const estimatedExchangeAmount = (amount) => {
    const rate = Number(data?.usdKrwExchangeRate);
    const numeric = Number(amount);
    if (!rate || !numeric) return "";
    return fromCurrency === "KRW" ? amountText(numeric / rate, "USD") : amountText(numeric * rate, "KRW");
  };

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    const form = new FormData(event.currentTarget);
    let url;
    let body;
    let successUrl;
    if (type === "transfer") {
      const from = data.accounts.find((item) => String(item.id) === selectedId);
      url = "/api/account-operations/transfer";
      body = { fromAccountNumber: from.accountNumber, fromBankCode: from.companyCode, toAccountNumber: form.get("toAccountNumber"), toBankCode: form.get("toBankCode"), amount: form.get("amount") };
      successUrl = `/accounts/transactions?accountNumber=${encodeURIComponent(from.accountNumber)}&bankCode=${from.companyCode}`;
    }
    if (type === "deposit") {
      const from = data.accounts.find((item) => String(item.id) === selectedId);
      const to = data.investments.find((item) => String(item.id) === form.get("to"));
      url = "/api/account-operations/deposit-investment";
      body = { fromAccountId: from.id, fromBankCode: from.companyCode, toInvestmentId: to.id, toSecuritiesCompanyCode: to.companyCode, amount: form.get("amount") };
      successUrl = `/investments/securityCashTransaction?investmentNumber=${encodeURIComponent(to.accountNumber)}&securitiesCompanyCode=${to.companyCode}`;
    }
    if (type === "withdraw") {
      const from = data.investments.find((item) => String(item.id) === selectedId);
      const to = data.accounts.find((item) => String(item.id) === form.get("to"));
      url = "/api/account-operations/withdraw-investment";
      body = { fromInvestmentId: from.id, fromSecuritiesCompanyCode: from.companyCode, toAccountId: to.id, toBankCode: to.companyCode, amount: form.get("amount") };
      successUrl = `/investments/securityCashTransaction?investmentNumber=${encodeURIComponent(from.accountNumber)}&securitiesCompanyCode=${from.companyCode}`;
    }
    if (type === "exchange") {
      const investment = data.investments.find((item) => String(item.id) === selectedId);
      url = "/api/account-operations/exchange";
      body = { investmentId: investment.id, securitiesCompanyCode: investment.companyCode, fromCurrencyCode: fromCurrency, toCurrencyCode: toCurrency, fromAmount: form.get("amount") };
      successUrl = `/investments/currency-exchange/transactions?investmentId=${investment.id}`;
    }
    try {
      await postJson(url, body);
      navigate(successUrl);
    } catch (requestError) {
      setError(requestError);
    }
  };

  return (
    <div className="page">
      <Header />
      <main className="main">
        <section className="content operation-page">
          <div className="page-heading"><h1>{titles[type]}</h1><p>{descriptions[type]} {type === "transfer" && <Link to="/accounts/transfer-investment">증권계좌 예수금은 투자금 입금 화면을 이용하세요.</Link>}</p></div>
          {error && Object.keys(error.fieldErrors || {}).length === 0 && <p className="overview-error" role="alert">{error.message}</p>}
          {data && type === "exchange" && (
            <section><h2>현재 환율</h2><div className="summary-grid"><div className="metric-card"><span className="metric-label">USD/KRW</span><span className="metric-value">{data.usdKrwExchangeRate ? `${Number(data.usdKrwExchangeRate).toLocaleString("ko-KR", { maximumFractionDigits: 2 })} KRW` : "수신 대기"}</span><span className="metric-note">1 USD 기준. 환전 처리 전 서버에서 최신 환율과 잔액을 다시 검증합니다.</span></div></div></section>
          )}
          {data && type !== "limit" && (
            <section>
              <h2>{type === "exchange" ? "환전 요청" : "계좌 및 금액 입력"}</h2>
              {((type === "withdraw" || type === "exchange") ? data.investments : data.accounts).length === 0 ? <div className="empty-state"><strong>사용할 계좌가 없습니다.</strong></div> : (
                <form className="operation-form" onSubmit={submit}>
                  <div className="table-scroll">
                    <table className="records-table">
                      <thead><tr><th>선택</th><th>{type === "withdraw" || type === "exchange" ? "증권계좌" : "출금 계좌"}</th><th>잔액</th></tr></thead>
                      <tbody>{((type === "withdraw" || type === "exchange") ? data.investments : data.accounts).map((item) => <tr key={item.id}><td><input type="radio" name="selectedAccount" value={item.id} checked={selectedId === String(item.id)} onChange={(event) => setSelectedId(event.target.value)} /></td><td><div className="account-cell"><span className="primary-line">{item.companyName}</span><span className="secondary-line">{item.accountNumber}</span></div></td><td>{item.balances ? <ul className="compact-list">{item.balances.map((balance) => <li key={balance.currency}>{amountText(balance.amount, balance.currency)}</li>)}</ul> : amountText(item.balance, item.currency)}</td></tr>)}</tbody>
                    </table>
                  </div>

                  <FieldError error={error} names={type === "transfer" ? ["fromAccountNumber", "fromBankCode"] : type === "deposit" ? ["fromAccountId", "fromBankCode"] : type === "withdraw" ? ["fromInvestmentId", "fromSecuritiesCompanyCode"] : ["investmentId", "securitiesCompanyCode"]} />

                  {type === "transfer" && <><label>입금 은행<select name="toBankCode" required>{data.banks.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select><FieldError error={error} names="toBankCode" /></label><label>입금 계좌번호<input name="toAccountNumber" required placeholder="000000-00-000000" /><FieldError error={error} names="toAccountNumber" /></label></>}
                  {type === "deposit" && <label>입금 증권계좌<select name="to" required value={depositInvestmentId} onChange={(event) => setDepositInvestmentId(event.target.value)}>{data.investments.map((item) => <option key={item.id} value={item.id}>{item.companyName} {item.accountNumber} · {item.balances.map((balance) => amountText(balance.amount, balance.currency)).join(" / ")}</option>)}</select><FieldError error={error} names={["toInvestmentId", "toSecuritiesCompanyCode"]} /></label>}
                  {type === "withdraw" && <label>입금 일반계좌<select name="to" required>{data.accounts.map((item) => <option key={item.id} value={item.id}>{item.companyName} {item.accountNumber} · {amountText(item.balance, item.currency)}</option>)}</select><FieldError error={error} names={["toAccountId", "toBankCode"]} /></label>}
                  {type === "exchange" && <div className="exchange-fields"><label>환전 전 통화<select value={fromCurrency} onChange={(event) => { const next = event.target.value; setFromCurrency(next); setToCurrency(next === "KRW" ? "USD" : "KRW"); }}>{data.currencies.map((item) => <option key={item.value} value={item.value}>{item.label} ({item.value})</option>)}</select><FieldError error={error} names="fromCurrencyCode" /></label><label>환전 후 통화<select value={toCurrency} onChange={(event) => setToCurrency(event.target.value)}>{data.currencies.filter((item) => item.value !== fromCurrency).map((item) => <option key={item.value} value={item.value}>{item.label} ({item.value})</option>)}</select><FieldError error={error} names="toCurrencyCode" /></label><div className="selected-balance-card"><span>사용 가능 잔액</span><strong>{selectedBalance ? amountText(selectedBalance.amount, selectedBalance.currency) : `0 ${fromCurrency}`}</strong></div></div>}
                  <label>{type === "exchange" ? "환전 금액" : "이체 금액"}<input name="amount" type="number" min={type === "exchange" && fromCurrency === "USD" ? "0.01" : "1"} step={type === "exchange" && fromCurrency === "USD" ? "0.01" : "1"} required placeholder={type === "exchange" ? "환전 전 통화 기준 금액" : "금액 입력"} onInput={(event) => { const output = event.currentTarget.form.querySelector("[data-estimate]"); if (output) output.textContent = estimatedExchangeAmount(event.currentTarget.value); }} /><FieldError error={error} names={type === "exchange" ? "fromAmount" : "amount"} /></label>
                  {type === "exchange" && <p className="exchange-estimate">예상 환전 금액 <strong data-estimate>-</strong></p>}
                  <button type="submit" disabled={!selectedId || (type === "deposit" && !depositInvestmentId) || (type === "exchange" && !data.usdKrwExchangeRate)}>{type === "exchange" ? "환전하기" : "이체하기"}</button>
                </form>
              )}
            </section>
          )}
          {type === "limit" && <TransferLimitForm error={error} onError={setError} />}
          <p className="action-row">
            {type === "exchange" && <Link to="/investments/currency-exchange/transactions">환전 내역</Link>}
            {type === "transfer" && <Link to={investmentDepositUrl}>투자금 입금</Link>}
            <Link to={type === "transfer" || type === "limit" ? "/accounts" : "/investments"}>홈으로</Link>
          </p>
        </section>
      </main>
    </div>
  );
}

function TransferLimitForm({ error, onError }) {
  const [data, setData] = useState(null);
  const load = (url = "/api/account-operations/transfer-limit") => getJson(url).then((value) => { setData(value); onError(null); }).catch(onError);
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const choose = (event) => { const item = data.accounts.find((account) => String(account.id) === event.target.value); if (item) load(`/api/account-operations/transfer-limit?accountNumber=${item.accountNumber}&bankCode=${item.companyCode}`); };
  const submit = async (event) => { event.preventDefault(); onError(null); const account = data.accounts.find((item) => item.id === data.selectedAccountId); const form = new FormData(event.currentTarget); try { await postJson("/api/account-operations/transfer-limit", { accountNumber: account.accountNumber, bankCode: account.companyCode, dailyTransferLimit: form.get("daily"), singleTransferLimit: form.get("single") }); load(`/api/account-operations/transfer-limit?accountNumber=${account.accountNumber}&bankCode=${account.companyCode}`); } catch (requestError) { onError(requestError); } };
  return data && <section className="limit-workspace"><h2>계좌 선택</h2><label>계좌<select onChange={choose} defaultValue=""><option value="">계좌 선택</option>{data.accounts.map((item) => <option key={item.id} value={item.id}>{item.companyName} {item.accountNumber} · {amountText(item.balance, item.currency)}</option>)}</select></label>{data.selectedAccountId && <form onSubmit={submit}><div className="limit-summary"><div><span>현재 일일 한도</span><strong>{Number(data.dailyLimit).toLocaleString("ko-KR")}</strong></div><div><span>현재 1회 한도</span><strong>{Number(data.singleLimit).toLocaleString("ko-KR")}</strong></div><div><span>오늘 사용</span><strong>{Number(data.todayUsed).toLocaleString("ko-KR")}</strong></div></div><label>변경할 일일 한도<input name="daily" type="number" min="1" required defaultValue={data.dailyLimit} /><FieldError error={error} names="dailyTransferLimit" /></label><label>변경할 1회 한도<input name="single" type="number" min="1" required defaultValue={data.singleLimit} /><FieldError error={error} names="singleTransferLimit" /></label><button>한도 변경</button></form>}</section>;
}
