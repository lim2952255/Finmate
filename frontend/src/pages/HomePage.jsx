import { Link } from "react-router-dom";
import Header from "../components/layout/Header.jsx";

// React Router가 /home 경로에 HomePage를 연결해 두었다면,
// /home으로 이동했을 때 HomePage 컴포넌트가 렌더링되고 JSX를 반환한다.
// JSX는 JS에서 UI를 HTML과 비슷한 형태로 구조화하여 작성할 수 있도록 도와주는 확장 문법이다.

// const는 변수의 재할당을 막는다. 다만 배열이나 객체 내부의 값까지 불변으로 만드는 것은 아니다.
// 메인 홈페이지에서 보여줄 메뉴 목록을 설정한다(계좌 관리 / 투자 / 포트폴리오)
const menus = [
  {
    href: "/accounts",
    className: "menu-item-account",
    kicker: "BANKING",
    title: "계좌 관리",
    description: "잔액과 거래내역, 이체 한도를 확인하고 관리하세요.",
    action: "계좌로 이동",
    reactRoute: true,
    paths: ["M3 9.5 12 4l9 5.5M5 10v8m4-8v8m6-8v8m4-8v8M3 20h18"]
  },
  {
    href: "/investments",
    className: "menu-item-investment",
    kicker: "INVESTMENT",
    title: "투자",
    description: "종목, 관심 목록, 시세와 주문 내역을 살펴보세요.",
    action: "투자로 이동",
    reactRoute: true,
    paths: ["M4 19V9m5 10V5m6 14v-7m5 7V3M3 21h18"]
  },
  {
    href: "/investments/portfolio",
    className: "menu-item-portfolio",
    kicker: "PORTFOLIO",
    title: "포트폴리오",
    description: "보유 자산의 평가금액과 수익률 변화를 확인하세요.",
    action: "포트폴리오 확인",
    reactRoute: true,
    paths: ["M4 19V5m0 14h16M7 15l4-4 3 2 5-6", "m16 7 3-.2.2 3"]
  }
];

// jsx를 리턴하는 함수를 컴포넌트라고 한다.
function MenuIcon({ paths }) {
  return (
    <span className="menu-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24">
        {paths.map((path) => <path key={path} d={path} />)}
      </svg>
    </span>
  );
}

// 계좌 / 투자 / 포트폴리오 메뉴를 보여주는 UI를 만들기 위한 컴포넌트
function QuickMenu() {
  return (
    <div className="menu home-menu">
      {/* menus 배열에서 menu를 하나씩 꺼내 각 메뉴의 UI를 생성한다. */}
      {menus.map((menu) => {
        const content = (
          <>
            <MenuIcon paths={menu.paths} />
            <span className="menu-kicker">{menu.kicker}</span>
            <strong>{menu.title}</strong>
            <span className="menu-description">{menu.description}</span>
            <span className="menu-arrow">{menu.action} <b aria-hidden="true">→</b></span>
          </>
        );

        return menu.reactRoute ? (
          <Link key={menu.href} className={`menu-item ${menu.className}`} to={menu.href}>
            {content}
          </Link>
        ) : (
          <a key={menu.href} className={`menu-item ${menu.className}`} href={menu.href}>
            {content}
          </a>
        );
      })}
    </div>
  );
}

// 홈화면의 상단에 노출할 멘트 및 이미지, 버튼등의 UI를 표현하는 jsx를 생성하는 컴포넌트
function Hero() {
  const chartHeights = [34, 45, 42, 58, 66, 62, 84];

  return (
    <div className="hero home-hero">
      <div className="home-hero-copy">
        <span className="eyebrow">MY FINANCE, CLEARLY</span>
        <h1>흩어진 금융 생활을<br /><span>한눈에 관리하세요</span></h1>
        <p>
          계좌 흐름부터 모의투자 포트폴리오까지,
          필요한 금융 정보를 하나의 대시보드에서 명확하게 확인하세요.
        </p>
        <div className="hero-actions">
          <a className="button-link button-link-primary" href="/investments/portfolio">포트폴리오 확인</a>
          <a className="button-link button-link-secondary" href="/investments/stocks/search">종목 둘러보기</a>
        </div>
      </div>

      <div className="hero-dashboard" aria-hidden="true">
        <div className="hero-dashboard-header">
          <span>자산 현황</span>
          <span className="status-dot">업데이트됨</span>
        </div>
        <strong>₩ 24,680,000</strong>
        <span className="hero-return">+ 8.42% 이번 달</span>
        <div className="hero-chart">
          {chartHeights.map((height) => (
            <span key={height} style={{ height: `${height}%` }} />
          ))}
        </div>
        <div className="hero-dashboard-footer">
          <span><i className="legend-dot account-dot" />계좌</span>
          <span><i className="legend-dot investment-dot" />투자</span>
        </div>
      </div>
    </div>
  );
}

// 실제 홈페이지 화면의 HTML구조를 나타내는 메인 컴포넌트
// 컴포넌트 안에서도 Header나 Hero 같은 하위 컴포넌트를 조합하여 JSX를 만들 수 있다.
export default function HomePage() {
  return (
    <div className="page">
      {/* Header 컴포넌트를 호출하여 페이지의 header에 공통적인 JSX를 적용한다. */}
      <Header />
      <main className="main">
        <section className="content home-content">
          {/* Hero 컴포넌트를 호출하여 홈 페이지의 상단에 FinMate를 설명하는 문구와 이미지, 페이지 이동 버튼을 표시하는 JSX를 적용한다. */}
          <Hero />
          <div className="home-section-heading">
            <div>
              <span className="eyebrow">QUICK ACCESS</span>
              <h2>무엇을 확인할까요?</h2>
            </div>
            <p>자주 사용하는 금융 기능으로 바로 이동하세요.</p>
          </div>
          {/* QuickMenu 컴포넌트를 호출하여 계좌관리 / 투자 / 포트폴리오 메뉴를 표시하는 JSX를 적용한다. */}
          <QuickMenu />
        </section>
      </main>
    </div>
  );
}
