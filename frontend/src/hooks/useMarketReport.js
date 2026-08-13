import { useCallback, useEffect, useRef, useState } from "react";
import { getMarketReport } from "../api/marketReports.js";

// 선택된 시장 주제의 리포트를 Spring 서버에서 받아오고 요청 상태를 관리하는 Custom Hook
export default function useMarketReport(topicCode) {
  // 주제별 리포트를 Map에 저장하여 이미 조회한 주제를 다시 클릭할 때 재사용한다.
  // cacheRef의 값이 바뀌어도 화면을 다시 그릴 필요가 없으므로 useRef를 사용한다.
  const cacheRef = useRef(new Map());

  // 다시 시도할 때 requestVersion을 증가시켜 useEffect를 다시 실행한다.
  const [requestVersion, setRequestVersion] = useState(0);

  // 어느 주제와 요청 버전에 대한 응답인지 포함하여 API 요청 상태를 관리한다.
  const [state, setState] = useState({
    topicCode: null,
    requestVersion: -1,
    status: "loading",
    report: null,
    error: null
  });

  // topicCode 또는 requestVersion이 변경될 때 해당 주제의 리포트를 다시 확인한다.
  useEffect(() => {
    const controller = new AbortController();

    // 현재 주제의 리포트가 캐시에 저장되어 있는지 확인한다.
    const cachedReport = cacheRef.current.get(topicCode);

    // 캐시가 있으면 즉시 완료되는 Promise를 사용하고, 없으면 Spring API를 호출한다.
    const request = cachedReport
      ? Promise.resolve(cachedReport)
      : getMarketReport(topicCode, { signal: controller.signal });

    request
      .then((report) => {
        // 요청이 이미 취소되었다면 늦게 도착한 응답으로 상태를 변경하지 않는다.
        if (controller.signal.aborted) return;

        // 성공한 응답을 캐시에 저장하고 요청 상태를 success로 변경한다.
        cacheRef.current.set(topicCode, report);
        setState({
          topicCode,
          requestVersion,
          status: "success",
          report,
          error: null
        });
      })
      .catch((error) => {
        // 사용자가 페이지를 이동해 취소된 요청은 오류 화면으로 처리하지 않는다.
        if (error.name !== "AbortError") {
          setState({
            topicCode,
            requestVersion,
            status: "error",
            report: null,
            error
          });
        }
      });

    // 주제가 바뀌거나 컴포넌트가 사라지기 전에 진행 중인 요청을 취소한다.
    return () => controller.abort();
  }, [requestVersion, topicCode]);

  // 다시 시도할 때 현재 주제의 캐시를 삭제하고 requestVersion을 증가시킨다.
  const retry = useCallback(() => {
    cacheRef.current.delete(topicCode);
    setRequestVersion((version) => version + 1);
  }, [topicCode]);

  // 선택한 주제 또는 요청 버전과 저장된 응답이 다르면 이전 데이터를 보여주지 않고 loading을 반환한다.
  if (state.topicCode !== topicCode || state.requestVersion !== requestVersion) {
    return { status: "loading", report: null, error: null, retry };
  }

  // 이 Hook을 사용하는 컴포넌트에 현재 요청 상태와 재시도 함수를 반환한다.
  return { status: state.status, report: state.report, error: state.error, retry };
}
