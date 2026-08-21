import { useCallback, useEffect, useState } from "react";
import { getAccountOverview, setPrimaryAccount } from "../api/accountOverview.js";

// 계좌 데이터의 조회 상태와 대표계좌 변경 동작을 한곳에서 관리하는 Custom Hook
export default function useAccountOverview() {
  const [retryVersion, setRetryVersion] = useState(0);
  const [state, setState] = useState({
    status: "loading",
    data: null,
    error: null,
    updatingId: null
  });

  // 페이지가 처음 나타나거나 다시 시도할 때 Spring 서버에서 계좌 데이터를 조회한다.
  useEffect(() => {
    const controller = new AbortController();

    getAccountOverview({ signal: controller.signal })
      .then((data) => setState({ status: "success", data, error: null, updatingId: null }))
      .catch((error) => {
        if (error.name !== "AbortError") {
          setState({ status: "error", data: null, error, updatingId: null });
        }
      });

    return () => controller.abort();
  }, [retryVersion]);

  // 오류 화면의 다시 시도 버튼이 호출하면 Effect를 다시 실행한다.
  const retry = useCallback(() => {
    setState((current) => ({ ...current, status: "loading", error: null }));
    setRetryVersion((version) => version + 1);
  }, []);

  // 대표계좌 변경이 끝나면 서버가 돌려준 최신 데이터로 화면을 즉시 다시 그린다.
  const changePrimary = useCallback(async (accountId) => {
    setState((current) => ({ ...current, error: null, updatingId: accountId }));

    try {
      const data = await setPrimaryAccount(accountId);
      setState({ status: "success", data, error: null, updatingId: null });
    } catch (error) {
      setState((current) => ({ ...current, error, updatingId: null }));
    }
  }, []);

  return { ...state, retry, changePrimary };
}
