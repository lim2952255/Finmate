import { useRef } from "react";

// 시장 주제별 탭 버튼을 만들고 클릭과 키보드 이동 이벤트를 설정하는 컴포넌트
export default function ReportTabs({ topics, activeTopicCode, onChange }) {
  // 생성된 각 button DOM을 배열에 저장하여 방향키로 이동한 탭에 focus를 설정한다.
  const tabRefs = useRef([]);

  // 사용자가 왼쪽 또는 오른쪽 방향키를 눌렀을 때 실행되는 이벤트 함수
  const moveWithKeyboard = (event, currentIndex) => {
    // 좌우 방향키가 아니라면 아무 작업도 하지 않는다.
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;

    event.preventDefault();

    // 오른쪽 방향키는 다음 탭, 왼쪽 방향키는 이전 탭으로 이동한다.
    const direction = event.key === "ArrowRight" ? 1 : -1;

    // 나머지 연산자를 사용하여 마지막 탭 다음에는 첫 탭, 첫 탭 이전에는 마지막 탭을 선택한다.
    const nextIndex = (currentIndex + direction + topics.length) % topics.length;

    // 부모로부터 props로 받은 onChange를 호출하여 선택된 주제의 상태를 변경한다.
    onChange(topics[nextIndex].code);

    // 새롭게 선택된 버튼 DOM에 키보드 focus를 이동한다.
    tabRefs.current[nextIndex]?.focus();
  };

  return (
    <div className="market-topic-tabs" role="tablist" aria-label="시장 리포트 주제">
      {/* topics 배열을 순회하며 주제별 탭 버튼을 생성한다. */}
      {topics.map((topic, index) => {
        // 현재 탭의 code가 부모가 전달한 activeTopicCode와 같으면 선택된 탭이다.
        const selected = topic.code === activeTopicCode;

        // ref 콜백으로 button DOM을 저장하고, 클릭하면 부모가 관리하는 주제 상태를 변경한다.
        return (
          <button
            key={topic.code}
            ref={(element) => { tabRefs.current[index] = element; }}
            id={`market-topic-${topic.code}`}
            className={`market-topic-tab${selected ? " is-active" : ""}`}
            type="button"
            role="tab"
            aria-controls="market-report-panel"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(topic.code)}
            onKeyDown={(event) => moveWithKeyboard(event, index)}
          >
            <span className="market-topic-symbol">{topic.symbol}</span>
            <span>
              <strong>{topic.displayName}</strong>
              <small>{topic.category}</small>
            </span>
          </button>
        );
      })}
    </div>
  );
}
