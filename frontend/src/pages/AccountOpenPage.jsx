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
    {/* 계좌 종류별 개설 한도와 시작 자금 정책을 안내한다. 실제 개설 제한과 지급 판단은 서버에서 수행한다. */}
    <p>{investment
      ? "증권계좌는 사용자당 최대 3개까지 개설할 수 있으며, 초기 예수금은 0원입니다. 일반계좌에서 투자금을 입금해 주세요."
      : "일반계좌는 통화를 합산해 사용자당 최대 3개까지 개설할 수 있습니다. 모의 시작 자금은 사용자당 최초 1회, 첫 원화 계좌에 1억원이 지급됩니다. 기존에 시작 자금을 받은 사용자의 추가 계좌는 0원으로 개설됩니다."}</p>
    {error && <p className="overview-error">{error.message}</p>}
    {options && <form onSubmit={submit}>
      {investment ? <label>증권사<select name="securitiesCompanyCode" required defaultValue=""><option value="">증권사를 선택하세요</option>{options.securitiesCompanies.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
        : <><label>은행<select name="bankCode" required defaultValue=""><option value="">은행을 선택하세요</option>{options.banks.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><label>통화<select name="currencyCode" required defaultValue="KRW">{options.currencies.map((item) => <option key={item.value} value={item.value}>{item.label} ({item.value})</option>)}</select></label></>}
      <div className="form-fact"><strong>계좌번호</strong><span>계좌 개설 시 자동 발급</span></div>
      <button type="submit">계좌 개설</button>
    </form>}
  </section></main></div>;
}
