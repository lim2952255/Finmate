import { useState } from "react";
import { Link } from "react-router-dom";
import { postJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

export default function PasswordChangePage() {
  useDocumentTitle("비밀번호 변경 | FinMate");
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    setSuccess(false);
    setSubmitting(true);

    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    if (values.newPassword !== values.passwordConfirmation) {
      setError(new Error("새 비밀번호와 비밀번호 확인이 일치하지 않습니다."));
      setSubmitting(false);
      return;
    }

    try {
      // 서버도 동일한 확인을 수행하며, 현재 비밀번호가 맞을 때만 새 해시를 저장한다.
      await postJson("/api/users/me/password", values);
      form.reset();
      setSuccess(true);
    } catch (requestError) {
      setError(requestError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page">
      <Header />
      <main className="main">
        <section className="content password-change-page">
          <section className="password-change-card">
            <aside className="password-change-intro">
              <span className="password-change-icon" aria-hidden="true">✓</span>
              <span className="eyebrow">ACCOUNT SECURITY</span>
              <h1>계정을 안전하게<br />보호하세요.</h1>
              <p>현재 비밀번호를 확인한 뒤 새로운 비밀번호로 변경합니다.</p>
              <ul>
                <li>영문, 숫자, 특수문자 포함</li>
                <li>최소 10자 이상 사용</li>
                <li>현재 비밀번호와 다르게 설정</li>
              </ul>
            </aside>
            <div className="password-change-panel">
              <div className="page-heading">
                <span className="eyebrow">CHANGE PASSWORD</span>
                <h2>비밀번호 변경</h2>
                <p>세 항목을 모두 입력해주세요.</p>
              </div>
              {error && <p className="alert alert-error" role="alert">{error.message}</p>}
              {success && <p className="alert alert-success" role="status">비밀번호가 안전하게 변경되었습니다.</p>}
              <form className="password-change-form" onSubmit={submit}>
                <label>
                  현재 비밀번호
                  <input name="currentPassword" type="password" autoComplete="current-password" placeholder="현재 비밀번호를 입력하세요" required />
                </label>
                <label>
                  새 비밀번호
                  <input name="newPassword" type="password" autoComplete="new-password" placeholder="새 비밀번호를 입력하세요" required />
                  <small>영문, 숫자, 특수문자를 포함해 10자 이상 입력해주세요.</small>
                </label>
                <label>
                  새 비밀번호 확인
                  <input name="passwordConfirmation" type="password" autoComplete="new-password" placeholder="새 비밀번호를 한 번 더 입력하세요" required />
                </label>
                <button type="submit" disabled={submitting}>{submitting ? "변경 중..." : "비밀번호 변경"}</button>
              </form>
              <Link className="password-change-back" to="/home">홈으로 돌아가기</Link>
            </div>
          </section>
        </section>
      </main>
    </div>
  );
}
