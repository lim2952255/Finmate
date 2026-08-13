import { Link } from "react-router-dom";
import Header from "../components/layout/Header.jsx";
import OverviewStatus from "../components/accounts/OverviewStatus.jsx";
import useAccountOverview from "../hooks/useAccountOverview.js";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import { formatMoney } from "../utils/moneyFormatting.js";
import "../styles/account-overview.css";

// 보유 중인 일반 계좌와 대표계좌 설정 기능을 보여주는 페이지 컴포넌트
export default function AccountListPage() {
  useDocumentTitle("보유계좌 목록 | FinMate");
  const { status, data, error, updatingId, retry, changePrimary } = useAccountOverview();
  const accounts = Array.isArray(data?.accounts) ? data.accounts : [];

  return (
    <div className="page">
      <Header />

      <main className="main">
        <section className="content">
          <div className="page-heading">
            <h1>보유계좌 목록</h1>
            <p>보유 중인 일반 계좌의 잔액, 대표계좌 여부, 이체와 거래내역을 확인합니다.</p>
          </div>

          <OverviewStatus status={status} error={status === "error" ? error : null} onRetry={retry} />

          {/* 대표계좌 변경 요청만 실패한 경우에는 기존 목록을 유지하면서 오류를 알린다. */}
          {status === "success" && error && <p className="overview-error" role="alert">{error.message}</p>}

          {status === "success" && accounts.length === 0 && (
            <div className="empty-state">
              <span className="empty-state-icon" aria-hidden="true">＋</span>
              <strong>첫 계좌를 만들어 보세요</strong>
              <p>입출금과 이체 내역을 관리할 일반 계좌가 아직 없습니다.</p>
              <a href="/accounts/open">계좌 개설하기</a>
            </div>
          )}

          {status === "success" && accounts.length > 0 && (
            <div className="table-scroll">
              <table className="records-table">
                <thead>
                  <tr>
                    <th>계좌</th>
                    <th>통화</th>
                    <th>잔액</th>
                    <th>대표계좌</th>
                    <th>이체</th>
                    <th>거래내역</th>
                    <th>설정</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => (
                    <tr key={account.id}>
                      <td>
                        <div className="account-cell">
                          <span className="primary-line">{account.bankName}</span>
                          <span className="secondary-line">{account.accountNumber}</span>
                        </div>
                      </td>
                      <td><span className="badge badge-neutral">{account.currencyCode}</span></td>
                      <td className="money-cell">{formatMoney(account.balance, account.fractionDigits)} {account.currencyCode}</td>
                      <td>{account.primary ? <span className="badge badge-submitted">대표계좌</span> : <span className="secondary-line">-</span>}</td>
                      <td><a href={`/accounts/transfer?from=${encodeURIComponent(account.accountNumber)}&fromBankCode=${account.bankCode}`}>이체</a></td>
                      <td><a href={`/accounts/transactions?accountNumber=${encodeURIComponent(account.accountNumber)}&bankCode=${account.bankCode}`}>거래내역</a></td>
                      <td>
                        {account.primary ? (
                          <span className="secondary-line">현재 대표계좌</span>
                        ) : (
                          <button
                            className="table-action-button"
                            type="button"
                            disabled={updatingId !== null}
                            onClick={() => changePrimary(account.id)}
                          >
                            {updatingId === account.id ? "변경 중" : "대표계좌 설정"}
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
            <a href="/accounts/open">계좌 개설</a>
            <Link to="/accounts">계좌 관리 홈</Link>
          </p>
        </section>
      </main>
    </div>
  );
}
