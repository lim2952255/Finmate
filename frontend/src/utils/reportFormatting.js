// 서버에서 받은 날짜 값을 한국어 날짜와 시간 형식의 문자열로 변환한다.
export function formatReportDateTime(value) {
  // 날짜 값이 없으면 화면에 대신 표시할 "-"를 반환한다.
  if (!value) return "-";

  const date = new Date(value);
  // JavaScript가 날짜로 변환할 수 없는 값이라면 원래 값을 그대로 반환한다.
  if (Number.isNaN(date.getTime())) return value;

  // 브라우저의 Intl API를 이용하여 한국어 형식으로 날짜와 시간을 표시한다.
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}
