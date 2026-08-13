import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getJson, postJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

export default function AccountOpenPage({ investment = false }) {
  useDocumentTitle(`${investment ? "증권 " : ""}계좌 개설 | FinMate`);
  const navigate = useNavigate();
  const [options, setOptions] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => { getJson("/api/account-setup").then(setOptions).catch(setError); }, []);

  const submit = async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = investment
      ? { securitiesCompanyCode: form.get("securitiesCompanyCode") }
      : { bankCode: form.get("bankCode"), currencyCode: form.get("currencyCode") };
    try {
      await postJson(investment ? "/api/account-setup/investments" : "/api/account-setup/accounts", body);
      navigate(investment ? "/investments" : "/accounts");
    } catch (requestError) { setError(requestError); }
  };

  return <div className="page"><Header /><main className="main"><section className="content">
    <div className="page-heading"><h1>{investment ? "증권 계좌 개설" : "계좌 개설"}</h1><p>선택한 기관을 기준으로 계좌번호가 자동 발급됩니다.</p></div>
    {error && <p className="overview-error">{error.message}</p>}
    {options && <form onSubmit={submit}>
      {investment ? <label>증권사<select name="securitiesCompanyCode" required defaultValue=""><option value="">증권사를 선택하세요</option>{options.securitiesCompanies.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
        : <><label>은행<select name="bankCode" required defaultValue=""><option value="">은행을 선택하세요</option>{options.banks.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><label>통화<select name="currencyCode" required defaultValue="KRW">{options.currencies.map((item) => <option key={item.value} value={item.value}>{item.label} ({item.value})</option>)}</select></label></>}
      <div className="form-fact"><strong>계좌번호</strong><span>계좌 개설 시 자동 발급</span></div>
      <button type="submit">계좌 개설</button>
    </form>}
  </section></main></div>;
}
