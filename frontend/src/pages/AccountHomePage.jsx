import { Link } from "react-router-dom";
import Header from "../components/layout/Header.jsx";
import CurrencySwitcher from "../components/accounts/CurrencySwitcher.jsx";
import OverviewStatus from "../components/accounts/OverviewStatus.jsx";
import useAccountOverview from "../hooks/useAccountOverview.js";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import { formatMoney } from "../utils/moneyFormatting.js";
import "../styles/account-overview.css";

// 계좌 홈 하단에 표시할 업무 메뉴 정보를 배열로 관리한다.
const accountMenus = [
  { href: "/accounts/open", icon: "＋", title: "계좌 개설", description: "은행과 통화를 선택해 새 계좌를 만듭니다." },
  { href: "/accounts/list", icon: "▤", title: "보유계좌 목록", description: "잔액과 대표계좌 설정을 한곳에서 관리합니다.", reactRoute: true },
  { href: "/accounts/transfer", icon: "↗", title: "계좌 이체", description: "등록된 계좌에서 안전하게 송금합니다." },
  { href: "/accounts/transfer-investment", icon: "₩", title: "투자금 입금", description: "일반 계좌에서 증권계좌 예수금으로 자금을 옮깁니다." },
  { href: "/accounts/transactions", icon: "↕", title: "거래내역", description: "기간별 입출금과 잔액 흐름을 확인합니다." }
];

// 일반 계좌의 요약, 대표계좌와 주요 메뉴를 보여주는 페이지 컴포넌트
export default function AccountHomePage() {
  useDocumentTitle("계좌 관리 | FinMate");
  const { status, data, error, retry } = useAccountOverview();
  const primaryAccount = data?.primaryAccount;

  return (
    <div className="page">
      <Header />

      <main className="main">
        <section className="content">
          <div className="page-heading">
            <h1>계좌 관리</h1>
            <p>계좌 개설, 보유계좌 조회, 거래내역 확인을 진행할 수 있습니다.</p>
          </div>

          {/* 최초 조회가 끝나기 전에는 본문 대신 로딩 또는 오류 상태를 표시한다. */}
          <OverviewStatus status={status} error={error} onRetry={retry} />

          {status === "success" && (
            <>
              <section className="home-overview-section">
                <div className="home-section-heading">
                  <div>
                    <h2>계좌 요약</h2>
                    <p>통화를 선택하면 주 금액과 보조 금액의 위치가 바뀝니다.</p>
                  </div>
                </div>

                <div className="home-summary-grid">
                  <div className="home-balance-card">
                    <CurrencySwitcher balances={data.totalBalances} label="총 보유금액" />
                  </div>
                  <div className="home-count-card">
                    <span className="home-count-icon" aria-hidden="true">▤</span>
                    <div>
                      <span className="metric-label">보유계좌 수</span>
                      <span className="metric-value">{data.accountCount}개</span>
                      <span className="metric-note">일반 입출금 계좌 기준</span>
                    </div>
                  </div>
                </div>
              </section>

              <section className="home-primary-section">
                <div className="home-section-heading">
                  <div>
                    <h2>대표계좌</h2>
                    <p>가장 자주 사용하는 계좌와 현재 잔액입니다.</p>
                  </div>
                </div>

                {/* 대표계좌의 존재 여부에 따라 계좌 카드와 안내 화면 중 하나를 렌더링한다. */}
                {primaryAccount ? (
                  <div className="primary-account-card">
                    <div className="primary-account-identity">
                      <span className="primary-account-icon" aria-hidden="true">₩</span>
                      <small>대표 입출금 계좌</small>
                      <strong>{primaryAccount.bankName}</strong>
                      <span>{primaryAccount.accountNumber}</span>
                    </div>
                    <div className="primary-account-balance primary-account-balance-single">
                      <small>사용 가능 잔액</small>
                      <strong className="primary-account-main-amount">
                        <span>{formatMoney(primaryAccount.balance, primaryAccount.fractionDigits)}</span>
                        <span>{primaryAccount.currencyCode}</span>
                      </strong>
                    </div>
                    <div className="primary-account-actions">
                      <a href={`/accounts/transfer?from=${encodeURIComponent(primaryAccount.accountNumber)}&fromBankCode=${primaryAccount.bankCode}`}>이체</a>
                      <a href="/accounts/transactions">거래내역</a>
                    </div>
                  </div>
                ) : (
                  <div className="empty-state">
                    {data.accountCount === 0 ? (
                      <>
                        <strong>첫 계좌를 개설해 보세요</strong>
                        <p>최초 원화 계좌에는 모의 시작 자금 1억원이 입금되며 대표계좌로 자동 설정됩니다.</p>
                        <Link to="/accounts/open">계좌 개설하기</Link>
                      </>
                    ) : (
                      <>
                        <strong>대표계좌를 설정해 주세요</strong>
                        <p>자주 사용하는 계좌를 대표계좌로 지정하면 이체와 거래내역 확인이 더 빨라집니다.</p>
                        <Link to="/accounts/list">보유계좌 확인</Link>
                      </>
                    )}
                  </div>
                )}
              </section>

              <section className="home-menu-section">
                <div className="home-section-heading">
                  <div>
                    <h2>계좌 메뉴</h2>
                    <p>계좌 개설부터 이체와 거래내역까지 바로 이동합니다.</p>
                  </div>
                </div>
                <nav aria-label="계좌 관리 메뉴">
                  <ul className="app-menu-grid home-menu-grid">
                    {accountMenus.map((menu) => (
                      <li className="app-menu-card" key={menu.href}>
                        <Link to={menu.href} data-icon={menu.icon}>
                          <strong>{menu.title}</strong>
                          <span>{menu.description}</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                </nav>
              </section>
            </>
          )}
        </section>
      </main>
    </div>
  );
}
