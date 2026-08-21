import { Link } from "react-router-dom";
import Header from "../components/layout/Header.jsx";
import CurrencySwitcher from "../components/accounts/CurrencySwitcher.jsx";
import OverviewStatus from "../components/accounts/OverviewStatus.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import useInvestmentOverview from "../hooks/useInvestmentOverview.js";
import "../styles/account-overview.css";

// 투자 홈 하단에 표시할 업무 메뉴 정보를 배열로 관리한다.
const investmentMenus = [
  { href: "/investments/open", icon: "＋", title: "증권 계좌 개설", description: "모의투자를 시작할 새 계좌를 만듭니다." },
  { href: "/investments/list", icon: "₩", title: "내 증권 계좌", description: "계좌별 예수금과 대표계좌를 확인합니다.", reactRoute: true },
  { href: "/investments/transfer", icon: "↗", title: "투자금 이체", description: "증권계좌의 예수금을 출금합니다." },
  { href: "/investments/currency-exchange", icon: "⇄", title: "증권계좌 환전", description: "원화와 외화를 간편하게 교환합니다." },
  { href: "/investments/currency-exchange/transactions", icon: "FX", title: "환전 내역", description: "통화별 환전 기록을 기간별로 봅니다." },
  { href: "/investments/securityCashTransaction", icon: "↕", title: "예수금 입출금", description: "증권계좌 현금 흐름을 추적합니다." },
  { href: "/investments/portfolio", icon: "◫", title: "포트폴리오", description: "보유 종목과 손익, 업종 비중을 봅니다." },
  { href: "/investments/orders", icon: "✓", title: "주문·체결 내역", description: "주문 상태와 체결 기록을 확인합니다." },
  { href: "/investments/stocks/watchlist", icon: "☆", title: "관심 종목", description: "눈여겨보는 종목을 모아 봅니다." },
  { href: "/investments/stocks/search", icon: "⌕", title: "종목 검색", description: "시장과 업종별로 투자 대상을 찾습니다." },
  { href: "/investments/stocks/market-movers", icon: "↗", title: "시장 움직임", description: "거래대금 상위와 급등락 종목을 봅니다.", reactRoute: true },
  { href: "/investments/market-data", icon: "⌁", title: "환율 / 지수", description: "시장 지표와 기간별 차트를 확인합니다." },
  { href: "/investments/reports", icon: "N", title: "뉴스 / 시장 리포트", description: "주요 지수와 금리·환율 뉴스를 살펴봅니다.", reactRoute: true }
];

// 증권계좌 요약, 대표계좌와 투자 메뉴를 보여주는 페이지 컴포넌트
export default function InvestmentHomePage() {
  useDocumentTitle("투자 | FinMate");
  const { status, data, error, retry } = useInvestmentOverview();
  const investments = Array.isArray(data?.investments) ? data.investments : [];
  const primaryInvestment = data?.primaryInvestment;
  const primaryBalances = primaryInvestment?.cashBalances.map((balance) => ({
    ...balance,
    amount: balance.availableBalance
  }));

  return (
    <div className="page">
      <Header />

      <main className="main">
        <section className="content">
          <div className="page-heading">
            <h1>투자</h1>
            <p>증권 계좌, 예수금, 포트폴리오, 주문 내역과 투자 정보를 관리합니다.</p>
          </div>

          <OverviewStatus status={status} error={error} onRetry={retry} />

          {status === "success" && (
            <>
              <section className="home-overview-section">
                <div className="home-section-heading">
                  <div>
                    <h2>내 증권 계좌 요약</h2>
                    <p>KRW를 기본으로 표시하고 보조 통화를 선택하면 위치를 바꿉니다.</p>
                  </div>
                </div>

                {investments.length === 0 ? (
                  <div className="empty-state">
                    <strong>첫 증권 계좌를 만들어 보세요</strong>
                    <p>모의 예수금으로 주문, 포트폴리오와 환전 기능을 체험할 수 있습니다.</p>
                    <a href="/investments/open">증권 계좌 개설하기</a>
                  </div>
                ) : (
                  <div className="home-summary-grid">
                    <div className="home-balance-card">
                      <CurrencySwitcher balances={data.totalBalances} label="총 예수금" />
                    </div>
                    <div className="home-count-card">
                      <span className="home-count-icon" aria-hidden="true">◫</span>
                      <div>
                        <span className="metric-label">증권 계좌 수</span>
                        <span className="metric-value">{data.investmentAccountCount}개</span>
                        <span className="metric-note">모든 증권계좌 기준</span>
                      </div>
                    </div>
                  </div>
                )}
              </section>

              {investments.length > 0 && (
                <section className="home-primary-section">
                  <div className="home-section-heading">
                    <div>
                      <h2>대표 증권계좌</h2>
                      <p>주문과 이체에 자주 사용하는 계좌입니다.</p>
                    </div>
                  </div>

                  {/* 대표 증권계좌의 존재 여부에 따라 계좌 카드와 안내 화면 중 하나를 렌더링한다. */}
                  {primaryInvestment ? (
                    <div className="primary-account-card">
                      <div className="primary-account-identity">
                        <span className="primary-account-icon" aria-hidden="true">₩</span>
                        <small>대표 증권계좌</small>
                        <strong>{primaryInvestment.securitiesCompanyName}</strong>
                        <span>{primaryInvestment.accountNumber}</span>
                      </div>
                      <div className="primary-account-balance">
                        <CurrencySwitcher balances={primaryBalances} label="사용 가능 예수금" compact />
                      </div>
                      <div className="primary-account-actions">
                        <a href={`/investments/transfer?from=${encodeURIComponent(primaryInvestment.accountNumber)}&fromSecuritiesCompanyCode=${primaryInvestment.securitiesCompanyCode}`}>투자금 이체</a>
                        <a href={`/investments/currency-exchange?investmentNumber=${encodeURIComponent(primaryInvestment.accountNumber)}&securitiesCompanyCode=${primaryInvestment.securitiesCompanyCode}`}>환전</a>
                        <a href="/investments/portfolio">포트폴리오 보기</a>
                      </div>
                    </div>
                  ) : (
                    <div className="empty-state">
                      <strong>대표 증권계좌를 설정해 주세요</strong>
                      <p>자주 사용하는 증권계좌를 지정하면 주문과 이체를 더 빠르게 시작할 수 있습니다.</p>
                      <Link to="/investments/list">증권계좌 관리</Link>
                    </div>
                  )}
                </section>
              )}

              <section className="home-menu-section">
                <div className="home-section-heading">
                  <div>
                    <h2>투자 메뉴</h2>
                    <p>자주 확인하는 계좌, 포트폴리오, 주문·거래내역, 종목 정보를 바로 이동합니다.</p>
                  </div>
                </div>
                <nav aria-label="투자 메뉴">
                  <ul className="app-menu-grid home-menu-grid">
                    {investmentMenus.map((menu) => (
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
