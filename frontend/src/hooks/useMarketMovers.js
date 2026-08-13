import { useCallback, useEffect, useState } from "react";
import { getMarketMovers } from "../api/marketMovers.js";

// 한 번 조회가 끝난 뒤 다음 조회를 시작하기까지 기다릴 시간을 5초로 설정한다.
const REFRESH_INTERVAL_MILLIS = 5000;

// 시장 움직임 데이터를 최초 조회하고 일정 시간마다 다시 조회하는 polling Custom Hook
export default function useMarketMovers() {
  // retryVersion이 변경되면 useEffect가 다시 실행되어 즉시 재요청한다.
  const [retryVersion, setRetryVersion] = useState(0);

  // API의 요청 상태, 시장 움직임 데이터, 오류 정보를 하나의 state로 관리한다.
  const [state, setState] = useState({
    status: "loading",
    pageInfo: null,
    error: null
  });

  // retryVersion이 바뀌거나 이 Hook을 사용하는 페이지가 처음 렌더링된 뒤 실행된다.
  useEffect(() => {
    const controller = new AbortController();
    let refreshTimerId;

    // 시장 움직임 데이터를 한 번 요청하는 비동기 함수를 정의한다.
    const loadMarketMovers = async () => {
      try {
        const pageInfo = await getMarketMovers({ signal: controller.signal });

        // 페이지를 벗어나 요청이 취소된 경우에는 도착한 응답으로 state를 변경하지 않는다.
        if (controller.signal.aborted) return;

        // 새로운 응답을 저장하면 이 Hook을 사용하는 컴포넌트가 다시 실행되어 표가 갱신된다.
        setState({ status: "success", pageInfo, error: null });
      } catch (error) {
        // AbortError는 페이지 이동이나 Effect 정리 과정에서 의도적으로 취소한 요청이다.
        if (error.name !== "AbortError") {
          setState((current) => ({
            ...current,
            status: current.pageInfo ? "success" : "error",
            error
          }));
        }
      } finally {
        // 현재 페이지에 머물러 있다면 5초 뒤 같은 함수를 다시 실행하도록 예약한다.
        // setInterval 대신 요청 완료 후 setTimeout을 등록하여 이전 요청과 다음 요청이 겹치지 않게 한다.
        if (!controller.signal.aborted) {
          refreshTimerId = window.setTimeout(loadMarketMovers, REFRESH_INTERVAL_MILLIS);
        }
      }
    };

    loadMarketMovers();

    // 페이지를 벗어나거나 retryVersion이 바뀌면 HTTP 요청과 다음 polling 예약을 모두 정리한다.
    return () => {
      controller.abort();
      window.clearTimeout(refreshTimerId);
    };
  }, [retryVersion]);

  // 오류 화면의 다시 시도 버튼이 호출하면 loading 상태로 바꾸고 Effect를 다시 실행한다.
  const retry = useCallback(() => {
    setState((current) => ({ ...current, status: "loading", error: null }));
    setRetryVersion((version) => version + 1);
  }, []);

  return { ...state, retry };
}
