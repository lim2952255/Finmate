import { Link, useLocation } from "react-router-dom";
import useSession from "../../hooks/useSession.js";

// Header는 FinMate 페이지의 공통적인 Header부분을 렌더링하는 jsx를 리턴하는 컴포넌트이다. 이를 여러 컴포넌트에서 재활용하며 페이지의 상단 부분을 공통적이고 일관되게 표현할 수 있다.

// 화면 상단에 노출할 메뉴 목록을 상수로 저장한다.
const navigation = [
  { href: "/accounts", label: "계좌" },
  { href: "/investments", label: "투자" },
  { href: "/investments/portfolio", label: "포트폴리오" },
  { href: "/investment-learning", label: "투자 학습" }
];

// 화면 상단에 노출할 메뉴 목록별로 적절한 Link를 생성하는 jsx를 리턴하는 컴포넌트
function Navigation() {
  const { pathname } = useLocation();

  return (
    <nav className="header-nav" aria-label="주요 메뉴">
      {/* 각 메뉴를 순회하며 객체 구조 분해로 각 속성을 변수에 할당한다. */}
      {navigation.map(({ href, label }) => {
        // pathname이 현재 메뉴의 URL과 연결되어 있으면 active를 true로 설정한다.
        // active인 메뉴에는 CSS를 적용해 현재 위치를 강조한다.
        const active = pathname === href || pathname.startsWith(`${href}/`);

        return <Link key={href} to={href} aria-current={active ? "page" : undefined}>{label}</Link>;
      })}
    </nav>
  );
}

// FinMate 로고와, 로고클릭시 메인 홈페이지로 이동하는 링크를 생성하는 jsx를 리턴하는 컴포넌트
function Logo() {
  return (
    <Link className="logo" to="/home" aria-label="FinMate 홈">
      <span className="logo-mark" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span>FinMate</span>
    </Link>
  );
}

// 로그인된 사용자 화면에 보여줄 헤더 UI를 정의하는 jsx를 리턴하는 컴포넌트
function AuthenticatedActions({ session }) {
  // 로그인 사용자 정보가 담긴 session을 props로 입력받는다.
  // displayName을 문자 배열로 만든 뒤 첫 글자를 사용자 아바타에 사용한다.
  const initial = Array.from(session.displayName || "F")[0];

  return (
    <div className="header-account">
      <Navigation />
      <p className="header-welcome">
        {/* 로그인한 사용자 정보를 화면 상단에 표시한다. */}
        <span className="user-avatar" aria-hidden="true">{initial}</span>
        <span><strong>{session.displayName}</strong>님</span>
      </p>
      <form action="/logout" method="post">
        {/* 로그아웃 요청에 CSRF 토큰을 함께 전달한다. */}
        <input
          type="hidden"
          name={session.csrf.parameterName}
          value={session.csrf.token}
        />
        <button className="button-quiet" type="submit">로그아웃</button>
      </form>
      {session.passwordChangeAvailable && (
        <Link className="button-quiet" to="/settings/password">비밀번호 변경</Link>
      )}
      <Link className="header-cta" to="/accounts">마이페이지</Link>
    </div>
  );
}

// 로그인하지 않은 사용자 화면에 보여줄 헤더 UI를 지정하는 jsx를 리턴하는 컴포넌트
function AnonymousActions() {
  return (
    <div className="header-account">
      <Link className="header-link" to="/signup">회원가입</Link>
      <Link className="header-cta" to="/login">로그인</Link>
    </div>
  );
}

// Finmate의 화면의 상단에 노출할 공통 헤더의 UI를 지정하는 jsx를 리턴하는 컴포넌트
export default function Header() {
  // useSession을 활용하여 Spring 서버로부터 현재 사용자의 로그인 상태를 가져온다.
  // 이때 useSession에서 status를 useState()로 관리하기 때문에, status가 달라지면 react에서 status가 변경되었음을 감지하고, 컴포넌트를 다시 실행하게 된다.
  const { status, session } = useSession();

  return (
    <header className="header">
      <Logo />
      {/* useSession()을 통해 Spring 서버로부터 데이터를 수신했는지 여부를 기반으로 적절한 jsx를 생성한다.*/}
      {/* useSession()은 React의 Hook들을 이용해서 재사용 가능한 상태/로직을 묶은 Custom Hook이다. */}
      {status === "loading" && (
        <div className="header-session-loading" role="status">로그인 상태 확인 중</div>
      )}
      {/* 만약 사용자가 로그인한 상태라면 AuthenticatedActions 컴포넌트를 호출한다.*/}
      {status === "success" && session.authenticated && (
        <AuthenticatedActions session={session} />
      )}
      {/* 만약 사용자가 로그인하지 않은 상태라면 AnonoymousActions 컴포넌트를 호출한다.*/}
      {status !== "loading" && (!session || !session.authenticated) && (
        <AnonymousActions />
      )}
    </header>
  );
}
