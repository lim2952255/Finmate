import { useEffect } from "react";

// 페이지의 제목을 설정하는 커스텀 훅
export default function useDocumentTitle(title) {
  // useEffect(실행할 함수, [의존성 변수])는 의존성 변수의 값이 변할때마다 실행할 함수를 실행하는 구조로서, title값이 변경되면 실행할 함수를 실행하여 문서의 제목을 update한다.
  useEffect(() => {
    document.title = title;
  }, [title]);
}
