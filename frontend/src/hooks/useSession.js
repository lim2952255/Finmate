import { useEffect, useState } from "react";
import { getSession } from "../api/session.js";

// React에서 제공하는 Hook들을 활용하여 재사용가능한 메서드를 구축한 Custom Hook
export default function useSession() {
  // useState는 react에서 관리할 상태정보와, 상태를 변경하는 메서드를 제공한다. 이때 초기 상태로 {status: "loading", session: null}로 설정한다.
  // 이후 setState를 통해서 state가 변경되면, react가 state가 변경되었음을 감지하고, 컴포넌트를 재실행하며, DOM을 update함으로서 화면을 렌더링한다.
  const [state, setState] = useState({ status: "loading", session: null });

  // useEffect는 컴포넌트가 렌더링된 뒤 실행해야 하는 부수작업(side effect)을 등록하는 React Hook이다.
  // useEffect 내의 코드는 컴포넌트 렌더링 이후에 실행된다.

  // useEffect(실행할 함수, [의존하는 값들]) 구조이며, [의존하는 값들]의 값들이 변경될때 실행할 함수가 실행되는 구조이다.
  // 만약 뒤의 [의존하는 값들]을 생략해버리면 컴포넌트가 렌더링 될때마다 실행된다.
  useEffect(() => {
    // AbortController는 진행중인 요청을 나중에 취소할 수 있게 해주는 브라우저 기능이다.
    // 만약 지금 세션정보를 서버에서 받아오는 중인데, 사용자가 다른페이지로 이동한다면, 이때 요청을 취소하기 위해 사용된다.
    const controller = new AbortController();

    // getSession()을 호출하여 Spring 서버에 세션정보를 요청한다. 이때 파라미터로 controller.signal을 전달하여, 나중에 작업요청을 취소할 수 있도록 한다.
    getSession({ signal: controller.signal })
      // 이때 getSession()은 내부적으로 비동기적으로 서버에 데이터를 요청하기 때문에, 서버의 응답결과가 즉시 돌아오지 않을 수 있다.
      // 따라서 서버의 응답결과가 돌아올때까지 대기하다가, 서버의 응답결과가 돌아오면 그때 then절이 실행된다.
      // then 절에서는 서버의 응답결과로 받은 세션 정보를 기반으로 setState를 통해 status를 update한다.
      // 이렇게 status가 update되면 react는 status가 업데이트되었음을 식별하고, 컴포넌트를 다시 실행하며 DOM을 업데이트하게 된다.
      .then((session) => setState({ status: "success", session }))
      .catch((error) => {
        // 에러가 발생했는데, 에러가 브라우저에서 요청을 중단한 경우가 아니라면 status를 error로 update한다.
        if (error.name !== "AbortError") {
          setState({ status: "error", session: null });
        }
      });

    // useEffect내에서 반환되는 함수는 cleanup 함수(정리 함수)로서 React가 해당 Effect를 정리해야 할때 호출된다.
    // cleanup은 보통 두가지 경우 실행된다.
    // 1. 컴포넌트가 화면에서 사라질 때
    // 2. 의존성이 바뀌어서 useEffect가 다시 실행될때
    return () => controller.abort();
  }, []); // useEffect의 맨 뒤의 []은 의존성 배열로서, 빈 배열인 경우에 이 Effect는 기본적으로 컴포넌트가 처음 마운트될때 실행하는 effect임을 나타낸다.

  return state;
}
