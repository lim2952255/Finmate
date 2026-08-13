// API 응답을 기다리는 동안 빈 화면 대신 현재 진행 상태를 보여주는 공통 컴포넌트다.
export default function PageLoading({ message = "화면 정보를 불러오고 있습니다." }) {
  return (
    <div className="overview-state page-loading" role="status">
      <span className="route-spinner" aria-hidden="true" />
      <p>{message}</p>
    </div>
  );
}
