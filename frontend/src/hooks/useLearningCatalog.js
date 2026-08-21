import { useCallback, useEffect, useState } from "react";
import { getLearningCatalog } from "../api/investmentLearning.js";

// 투자학습 카탈로그 데이터 Spring 서버로부터 받아오는 커스텀훅
export default function useLearningCatalog() {
  const [requestVersion, setRequestVersion] = useState(0);
  const [state, setState] = useState({
    status: "loading",
    catalog: null,
    error: null
  }); // 요청 상태정보를 state로 관리한다.

  // useEffect의 의존성 배열에 requestVersion을 담는다. 즉 requestVersion값이 변경될때마다 해당 함수를 다시 실행한다.
  useEffect(() => {
    const controller = new AbortController();

    // 투자학습의 카탈로그 데이터를 요청한다.
    getLearningCatalog({ signal: controller.signal })
      // 스프링서버로부터 카탈로그 데이터를 받으면 status를 변경한다 -> react가 이를 식별하고 컴포넌트를 재실행한다. 이떄 useLearningCatalog 자체는 컴포넌트가 아니라 커스텀 훅이며, 이 커스텀 훅을 불러서 사용하고 있는 컴포넌트가 재실행된다.
      .then((catalog) => setState({ status: "success", catalog, error: null }))
      .catch((error) => {
        if (error.name !== "AbortError") {
          setState({ status: "error", catalog: null, error });
        }
      });
    // 컴포넌트가 사라지거나 requestVersion이 바뀌어 Effect가 다시 실행되기 전에 기존 요청을 취소한다.
    return () => controller.abort();
  }, [requestVersion]);

  // 사용자가 [다시 불러오기] 버튼을 눌렀을 때 실행되는 onclick 함수 설정
  const retry = useCallback(() => {
    // 다시불러올때에는 version을 1만큼 증가시킨다. 이렇게 state와 RequestVersion이 변경되면 컴포넌트가 재실행된다.
    setState((current) => ({ ...current, status: "loading", error: null }));
    setRequestVersion((version) => version + 1);
  }, []);

  return { ...state, retry };
}
