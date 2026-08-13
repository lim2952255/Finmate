import { Component } from "react";

// 한 컴포넌트의 렌더링 오류가 전체 페이지를 하얗게 비우지 않도록 최상단에서 잡는다.
export default class AppErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <main className="route-state" role="alert">
          <div className="route-state-card">
            <strong>화면을 표시하는 중 문제가 발생했습니다.</strong>
            <p>잠시 후 다시 시도해 주세요.</p>
            <div className="route-state-actions">
              <a href="/home">홈으로</a>
              <button type="button" onClick={() => window.location.reload()}>다시 불러오기</button>
            </div>
          </div>
        </main>
      );
    }

    return this.props.children;
  }
}
