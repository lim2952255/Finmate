import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson, postJson } from "../api/forms.js";
import { getSession } from "../api/session.js";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

// 로그인 성공 후 돌아갈 주소는 FinMate 내부 절대 경로만 허용한다.
function getRedirect(search) {
  const redirect = new URLSearchParams(search).get("redirect");
  return redirect?.startsWith("/") && !redirect.startsWith("//") ? redirect : "/home";
}

function LogoMark() {
  return <span className="logo-mark" aria-hidden="true"><span /><span /><span /></span>;
}

function formatTelephoneInput(value) {
  const digits = value.replace(/\D/g, "").slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

function formatTelephone(event) {
  event.currentTarget.value = formatTelephoneInput(event.currentTarget.value);
}

// 로그인과 회원가입 왼쪽 영역은 문구만 바꾸고 동일한 브랜드 레이아웃을 재사용한다.
function AuthBrand({ signup = false }) {
  const highlights = signup
    ? ["은행·증권 계좌 통합 관리", "주문과 포트폴리오 한눈에 확인", "실제 종목 데이터로 금융 개념 학습"]
    : ["계좌와 투자 자산 통합 조회", "실시간 시세 기반 포트폴리오", "안전한 세션 기반 로그인"];

  return (
    <div className="auth-intro">
      <Link className="auth-brand" to="/home"><LogoMark />FinMate</Link>
      <span className="eyebrow">{signup ? "START FINMATE" : "WELCOME BACK"}</span>
      <h1>{signup ? <>금융 생활의 흐름을<br />한곳에서 시작하세요.</> : <>오늘의 금융 현황을<br />확인해볼까요?</>}</h1>
      <p>{signup ? "계좌부터 투자 자산과 시장 정보까지, 내 금융 데이터를 이어서 관리할 수 있습니다." : "계좌와 투자 자산의 흐름을 안전하고 편리하게 이어서 관리하세요."}</p>
      <div className="auth-highlight-list" aria-label="FinMate 주요 기능">
        {highlights.map((highlight) => <span key={highlight}><i aria-hidden="true">✓</i>{highlight}</span>)}
      </div>
    </div>
  );
}

function SocialLoginList({ options, redirect }) {
  const enabled = options && (options.google || options.kakao || options.naver);
  if (!enabled) return null;

  // 로컬에서 Google 버튼을 누르면 브라우저는 8080이 아니라 현재 React origin으로 요청한다.
  // 예: http://localhost:5173/api/auth/oauth2/google?redirect=/investments/portfolio
  // Vite/Nginx가 /api 요청만 내부적으로 Spring 8080에 전달하므로 브라우저는 backend 주소를 알 필요가 없다.
  // redirect는 React가 기억한 원래 화면이며, OAuth 성공 후 그 화면으로 돌아가기 위해 Spring에 명시적으로 전달한다.
  const oauthUrl = (provider) => `/api/auth/oauth2/${provider}?redirect=${encodeURIComponent(redirect)}`;

  return (
    <>
      <div className="auth-divider"><span>또는 간편 로그인</span></div>
      <div className="social-login-list">
        {options.google && <a className="social-login social-google" href={oauthUrl("google")}><span className="social-mark social-mark-google" aria-hidden="true">G</span><span>Google로 계속하기</span></a>}
        {options.kakao && <a className="social-login social-kakao" href={oauthUrl("kakao")}><span className="social-mark social-mark-kakao" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 4C6.8 4 3 7.1 3 10.8c0 2.4 1.6 4.5 4.1 5.7L6.4 20l4.1-2.4c.5.1 1 .1 1.5.1 5.2 0 9-3.1 9-6.9S17.2 4 12 4Z" /></svg></span><span>Kakao로 계속하기</span></a>}
        {options.naver && <a className="social-login social-naver" href={oauthUrl("naver")}><span className="social-mark social-mark-naver" aria-hidden="true">N</span><span>Naver로 계속하기</span></a>}
      </div>
    </>
  );
}

export function LoginPage() {
  useDocumentTitle("로그인 | FinMate");
  const location = useLocation();
  const navigate = useNavigate();
  const redirect = getRedirect(location.search);
  const [options, setOptions] = useState(null);
  const [csrf, setCsrf] = useState(null);
  const [loadError, setLoadError] = useState(null);

  // 공개 API 두 개를 함께 조회하여 소셜 공급자와 로그인 POST용 CSRF 토큰을 준비한다.
  useEffect(() => {
    const controller = new AbortController();

    Promise.all([
      getJson("/api/auth/options", { signal: controller.signal }),
      getSession({ signal: controller.signal })
    ])
      .then(([authOptions, session]) => {
        if (session.authenticated) {
          navigate(redirect, { replace: true });
          return;
        }
        setOptions(authOptions);
        setCsrf(session.csrf);
      })
      .catch((error) => {
        if (error.name !== "AbortError") setLoadError(error);
      });

    return () => controller.abort();
  }, [navigate, redirect]);

  const params = new URLSearchParams(location.search);

  return (
    <div className="page auth-page">
      <main className="main auth-main">
        <section className="auth-card">
          <AuthBrand />
          <div className="auth-form-panel">
            <div className="auth-heading"><span className="eyebrow">SIGN IN</span><h2>로그인</h2><p>FinMate 계정으로 금융 대시보드에 접속하세요.</p></div>
            {params.has("error") && <p className="alert alert-error" role="alert">아이디 또는 비밀번호가 올바르지 않습니다.</p>}
            {params.has("oauth2Error") && <p className="alert alert-error" role="alert">소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.</p>}
            {params.has("logout") && <p className="alert alert-success" role="status">안전하게 로그아웃되었습니다.</p>}
            {loadError && <p className="alert alert-error" role="alert">로그인 정보를 준비하지 못했습니다. 서버 연결을 확인해 주세요.</p>}
            <form className="auth-form" action="/login" method="post">
              {csrf && <input type="hidden" name={csrf.parameterName} value={csrf.token} />}
              <input type="hidden" name="redirect" value={redirect} />
              <div className="field-group"><label htmlFor="login-user-id">아이디</label><input id="login-user-id" name="userId" autoComplete="username" placeholder="아이디를 입력하세요" required /></div>
              <div className="field-group"><label htmlFor="login-password">비밀번호</label><input id="login-password" name="password" type="password" autoComplete="current-password" placeholder="비밀번호를 입력하세요" required /></div>
              <button className="auth-submit" type="submit" disabled={!csrf}>로그인</button>
            </form>
            <p className="auth-signup"><Link to="/find-id">아이디 찾기</Link></p>
            <SocialLoginList options={options} redirect={redirect} />
            <p className="auth-signup">아직 계정이 없나요? <Link to="/signup">회원가입</Link></p>
          </div>
        </section>
      </main>
    </div>
  );
}

export function SignupPage() {
  useDocumentTitle("회원가입 | FinMate");
  const navigate = useNavigate();
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    const form = Object.fromEntries(new FormData(event.currentTarget));
    if (form.password !== form.passwordConfirmation) {
      setError(new Error("비밀번호와 비밀번호 확인이 일치하지 않습니다."));
      return;
    }

    setSubmitting(true);
    try {
      await postJson("/api/auth/signup", form);
      navigate("/login", { replace: true });
    } catch (requestError) {
      setError(requestError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page auth-page">
      <main className="main auth-main">
        <section className="auth-card">
          <AuthBrand signup />
          <div className="auth-form-panel">
            <div className="auth-heading"><span className="eyebrow">CREATE ACCOUNT</span><h2>회원가입</h2><p>기본 정보를 입력하고 FinMate를 시작하세요.</p></div>
            {error && <p className="alert alert-error" role="alert">{error.message}</p>}
            <form className="auth-form" onSubmit={submit}>
              <div className="field-group"><label htmlFor="signup-name">이름</label><input id="signup-name" name="username" autoComplete="name" placeholder="이름을 입력하세요" required /></div>
              <div className="field-group"><label htmlFor="signup-telephone">전화번호</label><input id="signup-telephone" name="telephone" type="tel" inputMode="numeric" autoComplete="tel" placeholder="010-0000-0000" maxLength={13} onInput={formatTelephone} required /></div>
              <div className="field-group"><label htmlFor="signup-email">이메일</label><input id="signup-email" name="email" type="email" autoComplete="email" placeholder="example@finmate.com" required /></div>
              <div className="field-group"><label htmlFor="signup-user-id">아이디</label><input id="signup-user-id" name="userId" autoComplete="username" placeholder="로그인 아이디를 입력하세요" required /></div>
              <div className="field-group"><label htmlFor="signup-password">비밀번호</label><input id="signup-password" name="password" type="password" autoComplete="new-password" placeholder="영문, 숫자, 특수문자 포함 10자 이상" required /></div>
              <div className="field-group"><label htmlFor="signup-password-confirmation">비밀번호 확인</label><input id="signup-password-confirmation" name="passwordConfirmation" type="password" autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력하세요" required /></div>
              <button className="auth-submit" type="submit" disabled={submitting}>{submitting ? "가입 중..." : "가입하기"}</button>
            </form>
            <p className="auth-signup">이미 계정이 있나요? <Link to="/login">로그인</Link></p>
          </div>
        </section>
      </main>
    </div>
  );
}

export function FindIdPage() {
  useDocumentTitle("아이디 찾기 | FinMate");
  const [error, setError] = useState(null);
  const [userId, setUserId] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    setUserId(null);
    setSubmitting(true);

    try {
      const form = Object.fromEntries(new FormData(event.currentTarget));
      const response = await postJson("/api/auth/find-id", form);
      // 서버에서 세 가입 정보가 모두 일치할 때만 반환한 로컬 로그인 아이디를 표시한다.
      setUserId(response.userId);
    } catch (requestError) {
      setError(requestError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page auth-page">
      <main className="main auth-main">
        <section className="auth-card">
          <AuthBrand />
          <div className="auth-form-panel">
            <div className="auth-heading"><span className="eyebrow">FIND ACCOUNT</span><h2>아이디 찾기</h2><p>일반 회원가입 시 입력한 정보를 확인해 주세요.</p></div>
            {error && <p className="alert alert-error" role="alert">{error.message}</p>}
            {userId && <p className="alert alert-success" role="status">회원님의 아이디는 <strong>{userId}</strong>입니다.</p>}
            <form className="auth-form" onSubmit={submit}>
              <div className="field-group"><label htmlFor="find-id-name">이름</label><input id="find-id-name" name="username" autoComplete="name" placeholder="이름을 입력하세요" required /></div>
              <div className="field-group"><label htmlFor="find-id-telephone">전화번호</label><input id="find-id-telephone" name="telephone" type="tel" inputMode="numeric" autoComplete="tel" placeholder="010-0000-0000" maxLength={13} onInput={formatTelephone} required /></div>
              <div className="field-group"><label htmlFor="find-id-email">이메일</label><input id="find-id-email" name="email" type="email" autoComplete="email" placeholder="example@finmate.com" required /></div>
              <button className="auth-submit" type="submit" disabled={submitting}>{submitting ? "확인 중..." : "아이디 찾기"}</button>
            </form>
            <p className="auth-signup"><Link to="/login">로그인으로 돌아가기</Link></p>
          </div>
        </section>
      </main>
    </div>
  );
}
