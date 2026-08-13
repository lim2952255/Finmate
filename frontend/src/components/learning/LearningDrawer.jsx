import { useEffect, useRef, useState } from "react";
import ConceptDetail from "./ConceptDetail.jsx";

const MINIMUM_DRAWER_WIDTH = 460;
const DRAWER_VIEWPORT_MARGIN = 48;
const KEYBOARD_RESIZE_STEP = 24;

// 선택한 개념의 상세 패널을 표시한다.
export default function LearningDrawer({ drawer, onRequestClose }) {
  const dialogRef = useRef(null);
  const resizingRef = useRef(false);
  const [width, setWidth] = useState(720);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (drawer.open && !dialog.open) {
      dialog.showModal();
    } else if (!drawer.open && dialog.open) {
      dialog.close();
    }
  }, [drawer.open]);

  useEffect(() => () => {
    document.body.classList.remove("learning-resizing");
  }, []);

  const resizeToPointer = (clientX) => {
    const maximumWidth = window.innerWidth - DRAWER_VIEWPORT_MARGIN;
    setWidth(Math.max(
      MINIMUM_DRAWER_WIDTH,
      Math.min(maximumWidth, window.innerWidth - clientX)
    ));
  };

  const startResize = (event) => {
    resizingRef.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    document.body.classList.add("learning-resizing");
    resizeToPointer(event.clientX);
  };

  const continueResize = (event) => {
    if (resizingRef.current) {
      resizeToPointer(event.clientX);
    }
  };

  const stopResize = () => {
    resizingRef.current = false;
    document.body.classList.remove("learning-resizing");
  };

  const resizeWithKeyboard = (event) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;

    event.preventDefault();
    const direction = event.key === "ArrowLeft" ? 1 : -1;
    const maximumWidth = window.innerWidth - DRAWER_VIEWPORT_MARGIN;
    setWidth((current) => Math.max(
      MINIMUM_DRAWER_WIDTH,
      Math.min(maximumWidth, current + direction * KEYBOARD_RESIZE_STEP)
    ));
  };

  return (
    <dialog
      id="investment-learning-dialog"
      ref={dialogRef}
      className="learning-drawer"
      aria-labelledby="learning-dialog-title"
      style={{ "--learning-drawer-width": `${width}px` }}
      onClose={onRequestClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onRequestClose();
      }}
    >
      <div
        className="learning-drawer-resizer"
        role="separator"
        aria-label="학습 패널 너비 조절"
        aria-orientation="vertical"
        tabIndex="0"
        onPointerDown={startResize}
        onPointerMove={continueResize}
        onPointerUp={stopResize}
        onPointerCancel={stopResize}
        onKeyDown={resizeWithKeyboard}
      />
      <div className="learning-drawer-shell">
        <header className="learning-drawer-header">
          <div>
            <span>투자 학습</span>
            <h2 id="learning-dialog-title">{drawer.title}</h2>
          </div>
          <button type="button" aria-label="닫기" onClick={onRequestClose}>×</button>
        </header>
        <div className="learning-drawer-content">
          {drawer.status === "loading" && (
            <p className="learning-dialog-status">개념을 불러오는 중입니다.</p>
          )}
          {drawer.status === "error" && (
            <p className="learning-dialog-status">
              개념을 불러오지 못했습니다. 개념 동기화 상태를 확인한 뒤 다시 시도해 주세요.
            </p>
          )}
          {drawer.status === "success" && drawer.concept && (
            <ConceptDetail concept={drawer.concept} />
          )}
        </div>
      </div>
    </dialog>
  );
}
