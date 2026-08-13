import { useEffect, useRef, useState } from "react";
import StockConceptCard from "./StockConceptCard.jsx";

const STORAGE_KEY = "finmate.stockConceptDrawerWidth";
const DEFAULT_WIDTH = 540;
const MINIMUM_WIDTH = 460;

export default function StockConceptDrawer({ state, onClose }) {
  const dialogRef = useRef(null);
  const resizing = useRef(false);
  const [width, setWidth] = useState(() => Number(localStorage.getItem(STORAGE_KEY)) || DEFAULT_WIDTH);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (state.open && !dialog.open) dialog.showModal();
    if (!state.open && dialog.open) dialog.close();
    document.body.classList.toggle("concept-dialog-open", state.open);
    return () => document.body.classList.remove("concept-dialog-open");
  }, [state.open]);

  const resize = (clientX) => {
    const next = Math.max(MINIMUM_WIDTH, Math.min(window.innerWidth - 48, window.innerWidth - clientX));
    setWidth(next);
    localStorage.setItem(STORAGE_KEY, String(next));
  };

  return (
    <dialog ref={dialogRef} className="concept-drawer" style={{ "--concept-drawer-width": `${width}px` }} onClose={onClose} onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div className="concept-drawer-resizer" role="separator" tabIndex="0" aria-label="개념 패널 너비 조절" onDoubleClick={() => { setWidth(DEFAULT_WIDTH); localStorage.setItem(STORAGE_KEY, String(DEFAULT_WIDTH)); }} onPointerDown={(event) => { resizing.current = true; event.currentTarget.setPointerCapture(event.pointerId); resize(event.clientX); }} onPointerMove={(event) => resizing.current && resize(event.clientX)} onPointerUp={() => { resizing.current = false; }} onPointerCancel={() => { resizing.current = false; }} />
      <div className="concept-drawer-shell">
        <header className="concept-drawer-header"><div><span>주식 개념</span><h2>{state.title || "투자 개념"}</h2></div><button type="button" aria-label="닫기" onClick={onClose}>×</button></header>
        <div className="concept-drawer-content">
          {state.loading && <p className="learning-dialog-status">개념을 불러오는 중입니다.</p>}
          {state.error && <p className="overview-error">{state.error.message}</p>}
          {state.concept && <StockConceptCard concept={state.concept} />}
        </div>
      </div>
    </dialog>
  );
}
