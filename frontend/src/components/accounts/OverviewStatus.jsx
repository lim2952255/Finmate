// 계좌 관련 API의 최초 로딩과 오류 상태를 공통된 모양으로 표시한다.
export default function OverviewStatus({ status, error, onRetry }) {
  if (status === "loading") {
    return <p className="overview-state" role="status">계좌 정보를 불러오는 중입니다.</p>;
  }

  if (status === "error") {
    return (
      <div className="overview-state" role="alert">
        <p>{error?.message || "계좌 정보를 불러오지 못했습니다."}</p>
        <button type="button" onClick={onRetry}>다시 불러오기</button>
      </div>
    );
  }

  return null;
}
