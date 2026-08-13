import { Link } from "react-router-dom";
import Header from "../components/layout/Header.jsx";
import OverviewStatus from "../components/accounts/OverviewStatus.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import useInvestmentOverview from "../hooks/useInvestmentOverview.js";
import { formatMoney } from "../utils/moneyFormatting.js";
import "../styles/account-overview.css";

// 사용 가능한 통화별 예수금을 목록으로 표시하는 작은 컴포넌트
function CashBalanceList({ cashBalances }) {
  return (
    <ul className="compact-list">
      {cashBalances.map((balance) => (
        <li key={balance.currencyCode}>
          <span>{formatMoney(balance.availableBalance, balance.fractionDigits)}</span>
          <span>{balance.currencyCode}</span>
        </li>
      ))}
    </ul>
  );
}

// 보유 중인 증권계좌와 대표 증권계좌 설정 기능을 보여주는 페이지 컴포넌트
export default function InvestmentListPage() {
  useDocumentTitle("내 증권 계좌 | FinMate");
  const { status, data, error, updatingId, retry, changePrimary } = useInvestmentOverview();
  const investments = Array.isArray(data?.investments) ? data.investments : [];

  return (
    <div className="page">
      <Header />

      <main className="main">
        <section className="content">
          <div className="page-heading">
            <h1>내 증권 계좌 / 예수금</h1>
            <p>증권계좌별 예수금과 대표계좌 여부, 투자금 이체와 포트폴리오 진입을 확인합니다.</p>
          </div>

          <OverviewStatus status={status} error={status === "error" ? error : null} onRetry={retry} />

          {/* 대표계좌 변경 요청만 실패한 경우에는 기존 목록을 유지하면서 오류를 알린다. */}
          {status === "success" && error && <p className="overview-error" role="alert">{error.message}</p>}

          {status === "success" && investments.length === 0 && (
            <div className="empty-state">
              <span className="empty-state-icon" aria-hidden="true">＋</span>
              <strong>첫 증권 계좌를 만들어 보세요</strong>
              <p>예수금과 포트폴리오를 관리할 증권 계좌가 아직 없습니다.</p>
              <a href="/investments/open">증권 계좌 개설하기</a>
            </div>
          )}

          {status === "success" && investments.length > 0 && (
            <div className="table-scroll">
              <table className="records-table">
                <thead>
                  <tr>
                    <th>증권계좌</th>
                    <th>예수금</th>
                    <th>대표계좌</th>
                    <th>투자금 이체</th>
                    <th>환전</th>
                    <th>포트폴리오</th>
                    <th>설정</th>
                  </tr>
                </thead>
                <tbody>
                  {investments.map((investment) => (
                    <tr key={investment.id}>
                      <td>
                        <div className="account-cell">
                          <span className="primary-line">{investment.securitiesCompanyName}</span>
                          <span className="secondary-line">{investment.accountNumber}</span>
                        </div>
                      </td>
                      <td><CashBalanceList cashBalances={investment.cashBalances} /></td>
                      <td>{investment.primary ? <span className="badge badge-submitted">대표계좌</span> : <span className="secondary-line">-</span>}</td>
                      <td><a href={`/investments/transfer?from=${encodeURIComponent(investment.accountNumber)}&fromSecuritiesCompanyCode=${investment.securitiesCompanyCode}`}>이체</a></td>
                      <td><a href={`/investments/currency-exchange?investmentNumber=${encodeURIComponent(investment.accountNumber)}&securitiesCompanyCode=${investment.securitiesCompanyCode}`}>환전</a></td>
                      <td><a href={`/investments/portfolio?investmentId=${investment.id}`}>포트폴리오</a></td>
                      <td>
                        {investment.primary ? (
                          <span className="secondary-line">현재 대표계좌</span>
                        ) : (
                          <button
                            className="table-action-button"
                            type="button"
                            disabled={updatingId !== null}
                            onClick={() => changePrimary(investment.id)}
                          >
                            {updatingId === investment.id ? "변경 중" : "대표계좌 설정"}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="page-links">
            <a href="/investments/open">증권 계좌 개설</a>
            <a href="/investments/currency-exchange">증권계좌 환전</a>
            <Link to="/investments">투자 홈</Link>
          </p>
        </section>
      </main>
    </div>
  );
}
