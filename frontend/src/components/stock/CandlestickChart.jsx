import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const DEFAULT_VISIBLE_CANDLES = 63;
const MINIMUM_VISIBLE_CANDLES = 20;
const CHART_HEIGHT = 540;
const PADDING = { top: 28, right: 78, bottom: 86, left: 18 };

const valueOf = (value) => Number(String(value ?? "0").replaceAll(",", ""));

function formatPrice(value, currency) {
  const digits = currency === "KRW" ? 0 : 2;
  const symbol = currency === "KRW" ? "₩" : currency === "USD" ? "$" : "";
  return `${symbol}${new Intl.NumberFormat("ko-KR", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  }).format(value)}`;
}

function formatNumber(value) {
  return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 }).format(value);
}

function movingAverage(candles, endIndex, days) {
  if (endIndex < days - 1) return null;
  let total = 0;
  for (let index = endIndex - days + 1; index <= endIndex; index += 1) {
    total += candles[index].close;
  }
  return total / days;
}

function directionClass(value) {
  if (value > 0) return "bullish";
  if (value < 0) return "bearish";
  return "flat";
}

export default function CandlestickChart({ candles = [], currency = "KRW", periodLabel, detail, orderbook }) {
  const canvasRef = useRef(null);
  const dragRef = useRef(null);
  const rangeDragRef = useRef(null);
  const wheelPanRemainderRef = useRef(0);
  const normalized = useMemo(() => candles.map((candle) => ({
    ...candle,
    open: valueOf(candle.openPrice),
    high: valueOf(candle.highPrice),
    low: valueOf(candle.lowPrice),
    close: valueOf(candle.closePrice),
    volume: valueOf(candle.accumulatedVolume),
    amount: valueOf(candle.accumulatedTradeAmount)
  })).filter((candle) => candle.open > 0 && candle.high > 0 && candle.low > 0 && candle.close > 0), [candles]);
  const initialCount = Math.min(DEFAULT_VISIBLE_CANDLES, Math.max(normalized.length, 1));
  const [visibleCount, setVisibleCount] = useState(initialCount);
  const [startIndex, setStartIndex] = useState(Math.max(0, normalized.length - initialCount));
  const [hoverIndex, setHoverIndex] = useState(null);
  const [hoverPoint, setHoverPoint] = useState({ x: 14, y: 14 });
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    const count = Math.min(DEFAULT_VISIBLE_CANDLES, Math.max(normalized.length, 1));
    const timer = window.setTimeout(() => {
      setVisibleCount(count);
      setStartIndex(Math.max(0, normalized.length - count));
      setHoverIndex(null);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [normalized.length]);

  const clampStart = useCallback((value, count = visibleCount) => (
    Math.max(0, Math.min(Math.max(0, normalized.length - count), value))
  ), [normalized.length, visibleCount]);
  const viewport = useMemo(
    () => normalized.slice(startIndex, startIndex + visibleCount),
    [normalized, startIndex, visibleCount]
  );
  const changeAmount = valueOf(detail?.latestChangeAmount);
  const changeRate = valueOf(detail?.latestChangeRate);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !viewport.length) return undefined;
    const resize = () => {
      const width = canvas.clientWidth;
      const ratio = window.devicePixelRatio || 1;
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(CHART_HEIGHT * ratio);
      const context = canvas.getContext("2d");
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      context.clearRect(0, 0, width, CHART_HEIGHT);

      const plotWidth = width - PADDING.left - PADDING.right;
      const priceHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom;
      const volumeTop = CHART_HEIGHT - 68;
      const volumeHeight = 38;
      const highest = Math.max(...viewport.map((item) => item.high));
      const lowest = Math.min(...viewport.map((item) => item.low));
      const priceMargin = Math.max((highest - lowest) * 0.09, highest * 0.004);
      const maxPrice = highest + priceMargin;
      const minPrice = lowest - priceMargin;
      const maxVolume = Math.max(...viewport.map((item) => item.volume), 1);
      const slot = plotWidth / viewport.length;
      const x = (index) => PADDING.left + slot * (index + 0.5);
      const y = (price) => PADDING.top + (maxPrice - price) / (maxPrice - minPrice) * priceHeight;

      context.font = "12px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
      context.textAlign = "left";
      context.strokeStyle = "#e5e7eb";
      context.fillStyle = "#64748b";
      context.lineWidth = 1;
      for (let line = 0; line <= 5; line += 1) {
        const lineY = PADDING.top + priceHeight * line / 5;
        const price = maxPrice - (maxPrice - minPrice) * line / 5;
        context.beginPath();
        context.moveTo(PADDING.left, lineY);
        context.lineTo(width - PADDING.right, lineY);
        context.stroke();
        context.fillText(formatPrice(price, currency), width - PADDING.right + 9, lineY + 4);
      }

      [5, 20, 60].forEach((days, averageIndex) => {
        context.beginPath();
        context.strokeStyle = ["#f59e0b", "#10b981", "#7c3aed"][averageIndex];
        context.lineWidth = 1.45;
        let started = false;
        viewport.forEach((_, localIndex) => {
          const average = movingAverage(normalized, startIndex + localIndex, days);
          if (average === null) return;
          if (!started) {
            context.moveTo(x(localIndex), y(average));
            started = true;
          } else {
            context.lineTo(x(localIndex), y(average));
          }
        });
        context.stroke();
      });

      viewport.forEach((candle, index) => {
        const rising = candle.close >= candle.open;
        const color = rising ? "#dc2626" : "#2563eb";
        const center = x(index);
        const bodyWidth = Math.max(2, Math.min(slot * 0.62, 11));
        context.strokeStyle = color;
        context.fillStyle = color;
        context.lineWidth = 1.2;
        context.beginPath();
        context.moveTo(center, y(candle.high));
        context.lineTo(center, y(candle.low));
        context.stroke();
        const bodyTop = y(Math.max(candle.open, candle.close));
        const bodyBottom = y(Math.min(candle.open, candle.close));
        context.fillRect(center - bodyWidth / 2, bodyTop, bodyWidth, Math.max(1.5, bodyBottom - bodyTop));
        context.globalAlpha = 0.42;
        context.fillRect(
          center - bodyWidth / 2,
          volumeTop + volumeHeight * (1 - candle.volume / maxVolume),
          bodyWidth,
          volumeHeight * candle.volume / maxVolume
        );
        context.globalAlpha = 1;
      });

      const latestPrice = valueOf(detail?.latestClosePrice) || viewport.at(-1)?.close;
      if (latestPrice >= minPrice && latestPrice <= maxPrice) {
        const latestY = y(latestPrice);
        const currentDirection = directionClass(changeAmount);
        const currentColor = currentDirection === "bullish" ? "#dc2626" : currentDirection === "bearish" ? "#2563eb" : "#64748b";
        context.save();
        context.setLineDash([5, 5]);
        context.strokeStyle = currentColor;
        context.beginPath();
        context.moveTo(PADDING.left, latestY);
        context.lineTo(width - PADDING.right, latestY);
        context.stroke();
        context.restore();
        const badgeText = formatPrice(latestPrice, currency);
        const badgeWidth = Math.max(66, context.measureText(badgeText).width + 14);
        context.fillStyle = currentColor;
        context.fillRect(width - PADDING.right + 4, latestY - 10, badgeWidth, 20);
        context.fillStyle = "#ffffff";
        context.textAlign = "center";
        context.font = "700 11px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
        context.fillText(badgeText, width - PADDING.right + 4 + badgeWidth / 2, latestY + 4);
      }

      const highestIndex = viewport.findIndex((item) => item.high === highest);
      const lowestIndex = viewport.findIndex((item) => item.low === lowest);
      const latestClose = normalized.at(-1)?.close;
      const drawExtremeMarker = (index, price, highestPoint) => {
        const candle = viewport[index];
        const center = x(index);
        const pointY = y(price);
        const color = highestPoint ? "#dc2626" : "#2563eb";
        const change = price ? (latestClose - price) / price * 100 : 0;
        const label = `${highestPoint ? "최고" : "최저"} ${formatPrice(price, currency)} (${change > 0 ? "+" : ""}${change.toFixed(2)}%) · ${candle.tradeDate}`;

        context.save();
        context.fillStyle = color;
        context.beginPath();
        context.arc(center, pointY, 2.7, 0, Math.PI * 2);
        context.fill();
        context.font = "700 10px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
        const measured = context.measureText(label).width;
        const labelX = Math.max(PADDING.left + measured / 2, Math.min(width - PADDING.right - measured / 2, center));
        const labelY = highestPoint
          ? Math.max(13, pointY - 10)
          : Math.min(volumeTop - 5, pointY + 18);
        context.textAlign = "center";
        context.fillText(label, labelX, labelY);
        context.restore();
      };
      drawExtremeMarker(highestIndex, highest, true);
      drawExtremeMarker(lowestIndex, lowest, false);

      const dateStep = Math.max(1, Math.ceil(viewport.length / 7));
      context.textAlign = "center";
      context.fillStyle = "#64748b";
      viewport.forEach((candle, index) => {
        if (index % dateStep === 0 || index === viewport.length - 1) {
          context.fillText(candle.tradeDate.slice(5), x(index), CHART_HEIGHT - 8);
        }
      });

      if (hoverIndex !== null && hoverIndex >= 0 && hoverIndex < viewport.length) {
        const candle = viewport[hoverIndex];
        const center = x(hoverIndex);
        context.save();
        context.setLineDash([4, 4]);
        context.strokeStyle = "#334155";
        context.globalAlpha = 0.74;
        context.beginPath();
        context.moveTo(center, PADDING.top);
        context.lineTo(center, volumeTop + volumeHeight);
        context.stroke();
        context.beginPath();
        context.moveTo(PADDING.left, y(candle.close));
        context.lineTo(width - PADDING.right, y(candle.close));
        context.stroke();
        context.restore();
      }
    };

    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [changeAmount, currency, detail?.latestClosePrice, hoverIndex, normalized, startIndex, viewport]);

  if (!normalized.length) return <p>표시할 일봉 데이터가 없습니다.</p>;

  const pointerIndex = (event) => {
    const rect = canvasRef.current.getBoundingClientRect();
    const plotWidth = rect.width - PADDING.left - PADDING.right;
    const relativeX = Math.max(0, Math.min(plotWidth - 1, event.clientX - rect.left - PADDING.left));
    return Math.max(0, Math.min(viewport.length - 1, Math.floor(relativeX / plotWidth * viewport.length)));
  };
  const hovered = hoverIndex === null ? null : viewport[hoverIndex];
  const previous = hoverIndex === null ? null : normalized[startIndex + hoverIndex - 1];
  const rate = (value, comparison) => comparison ? (value - comparison) / comparison * 100 : null;

  const resetViewport = () => {
    const count = Math.min(DEFAULT_VISIBLE_CANDLES, normalized.length);
    setVisibleCount(count);
    setStartIndex(normalized.length - count);
  };

  const zoom = (event) => {
    event.preventDefault();
    if (Math.abs(event.deltaX) > Math.abs(event.deltaY)) {
      const plotWidth = event.currentTarget.clientWidth - PADDING.left - PADDING.right;
      const slotWidth = Math.max(1, plotWidth / visibleCount);
      const accumulated = wheelPanRemainderRef.current + event.deltaX;
      const shift = Math.trunc(accumulated / slotWidth);
      wheelPanRemainderRef.current = accumulated - shift * slotWidth;
      if (shift !== 0) {
        setStartIndex((current) => clampStart(current + shift));
        setHoverIndex(null);
      }
      return;
    }
    wheelPanRemainderRef.current = 0;
    const anchor = pointerIndex(event);
    const nextCount = Math.max(
      Math.min(MINIMUM_VISIBLE_CANDLES, normalized.length),
      Math.min(normalized.length, visibleCount + (event.deltaY > 0 ? 8 : -8))
    );
    const globalAnchor = startIndex + anchor;
    const nextStart = clampStart(Math.round(globalAnchor - anchor / visibleCount * nextCount), nextCount);
    setVisibleCount(nextCount);
    setStartIndex(nextStart);
  };

  const rangeWidth = Math.max(8, visibleCount / normalized.length * 100);
  const rangeLeft = normalized.length === visibleCount
    ? 0
    : startIndex / (normalized.length - visibleCount) * (100 - rangeWidth);
  const totalAskQuantity = orderbook?.levels?.reduce((total, level) => total + valueOf(level.askQuantity), 0) || 0;
  const totalBidQuantity = orderbook?.levels?.reduce((total, level) => total + valueOf(level.bidQuantity), 0) || 0;

  return (
    <div className="chart-panel">
      <div>
        <div className="chart-main-header">
          <div className="chart-price">
            <strong>{formatPrice(valueOf(detail?.latestClosePrice), currency)}</strong>
            <span className={`chart-price-change ${directionClass(changeAmount)}`}>
              <span>{changeAmount > 0 ? "+" : changeAmount < 0 ? "-" : ""}{formatPrice(Math.abs(changeAmount), currency)}</span>
              <span>{changeRate > 0 ? "+" : ""}{changeRate.toFixed(2)}%</span>
            </span>
            <span className="chart-price-time">{detail?.latestTradeDate || "-"}</span>
            <span className="market-session-badge">세션 확인 대기</span>
          </div>
          <div className="chart-legend">
            <span className="legend-item"><span className="legend-color ma5" />MA5</span>
            <span className="legend-item"><span className="legend-color ma20" />MA20</span>
            <span className="legend-item"><span className="legend-color ma60" />MA60</span>
          </div>
        </div>

        <div
          className={`chart-scroll${dragging ? " dragging" : ""}`}
          aria-label="마우스 휠로 확대·축소하고 드래그로 이동할 수 있는 일봉 캔들 차트"
          onDoubleClick={resetViewport}
        >
          <canvas
            ref={canvasRef}
            className="stock-chart-canvas"
            role="img"
            aria-label="일봉 가격, 거래량 및 이동평균선 차트"
            onWheel={zoom}
            onPointerMove={(event) => {
              if (dragRef.current) {
                event.preventDefault();
                const slotWidth = Math.max(4, (event.currentTarget.clientWidth - PADDING.left - PADDING.right) / visibleCount);
                const movedSlots = Math.round((event.clientX - dragRef.current.clientX) / slotWidth);
                setStartIndex(clampStart(dragRef.current.startIndex - movedSlots));
              }
              setHoverIndex(pointerIndex(event));
              const rect = event.currentTarget.getBoundingClientRect();
              const tooltipWidth = 300;
              const tooltipHeight = 246;
              const localX = event.clientX - rect.left;
              const localY = event.clientY - rect.top;
              setHoverPoint({
                x: localX + tooltipWidth + 32 > rect.width ? Math.max(14, localX - tooltipWidth - 16) : localX + 16,
                y: localY + tooltipHeight + 32 > rect.height ? Math.max(14, localY - tooltipHeight - 16) : localY + 16
              });
            }}
            onPointerLeave={() => {
              if (!dragRef.current) setHoverIndex(null);
            }}
            onPointerDown={(event) => {
              if (event.button !== 0) return;
              dragRef.current = { clientX: event.clientX, startIndex };
              setDragging(true);
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerUp={(event) => {
              dragRef.current = null;
              setDragging(false);
              if (event.currentTarget.hasPointerCapture(event.pointerId)) {
                event.currentTarget.releasePointerCapture(event.pointerId);
              }
            }}
            onPointerCancel={() => {
              dragRef.current = null;
              setDragging(false);
            }}
          />
          {hovered && (
            <div className="chart-tooltip visible" aria-hidden="false" style={{ left: hoverPoint.x, top: hoverPoint.y }}>
              <div className="chart-tooltip-title">{hovered.tradeDate}</div>
              {[["시가", hovered.open, previous?.close], ["고가", hovered.high, previous?.close], ["저가", hovered.low, previous?.close], ["종가", hovered.close, previous?.close]].map(([label, value, comparison]) => {
                const change = rate(value, comparison);
                return <div className="chart-tooltip-row" key={label}><span className="chart-tooltip-label">{label}</span><span className={`chart-tooltip-value ${directionClass(change || 0)}`}>{formatPrice(value, currency)}{change === null ? "" : ` (${change > 0 ? "+" : ""}${change.toFixed(2)}%)`}</span></div>;
              })}
              <div className="chart-tooltip-row"><span className="chart-tooltip-label">거래량</span><span className="chart-tooltip-value">{formatNumber(hovered.volume)}주</span></div>
              <div className="chart-tooltip-row"><span className="chart-tooltip-label">거래대금</span><span className="chart-tooltip-value">{formatNumber(hovered.amount)}</span></div>
            </div>
          )}
          <div className="chart-interaction-guide">기본 약 3개월 · 전체 기간까지 축소 · 두 손가락 또는 드래그로 좌우 이동</div>
        </div>

        <div
          className="chart-range-scrollbar"
          aria-label="전체 조회 기간에서 차트 표시 구간 이동"
          onPointerMove={(event) => {
            if (!rangeDragRef.current) return;
            const rect = event.currentTarget.getBoundingClientRect();
            const movable = rect.width * (1 - rangeWidth / 100);
            if (movable <= 0) return;
            const delta = event.clientX - rangeDragRef.current.clientX;
            setStartIndex(clampStart(Math.round(rangeDragRef.current.startIndex + delta / movable * (normalized.length - visibleCount))));
          }}
          onPointerUp={(event) => {
            rangeDragRef.current = null;
            if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
          }}
        >
          <div className="chart-range-track">
            <button
              className="chart-range-thumb"
              type="button"
              aria-label="차트 표시 기간 이동"
              style={{ left: `${rangeLeft}%`, width: `${rangeWidth}%` }}
              onPointerDown={(event) => {
                event.stopPropagation();
                rangeDragRef.current = { clientX: event.clientX, startIndex };
                event.currentTarget.parentElement.parentElement.setPointerCapture(event.pointerId);
              }}
            />
          </div>
        </div>
        <p className="chart-caption">{normalized[0]?.tradeDate}부터 {normalized.at(-1)?.tradeDate}까지의 수정주가 기준 {periodLabel} 일봉입니다.</p>
      </div>

      <aside className="chart-side-panel">
        <div className="chart-side-item"><span>종목</span><strong>{detail?.symbol || "-"}</strong></div>
        <div className="chart-side-item"><span>시장</span><strong>{detail?.market || "-"}</strong></div>
        <div className="chart-side-item"><span>기간</span><strong>{periodLabel}</strong></div>
        <div className="chart-side-item"><span>최근 거래일</span><strong>{detail?.latestTradeDate || "-"}</strong></div>
        <div className="chart-side-item"><span>조회 일수</span><strong>{normalized.length}</strong></div>
        {detail?.chartPriceSummary && <><div className="chart-side-item"><span>기간 최고</span><strong>{formatPrice(valueOf(detail.chartPriceSummary.highestPrice), currency)}</strong><small>{detail.chartPriceSummary.highestTradeDate}</small></div><div className="chart-side-item"><span>기간 최저</span><strong>{formatPrice(valueOf(detail.chartPriceSummary.lowestPrice), currency)}</strong><small>{detail.chartPriceSummary.lowestTradeDate}</small></div></>}
        <div className="orderbook-section">
          <div className="orderbook-header"><strong>실시간 호가</strong><small>{orderbook?.quoteTime || "수신 대기"}</small></div>
          <table className="orderbook-table"><thead><tr><th>매도가</th><th>잔량</th><th>매수가</th><th>잔량</th></tr></thead><tbody>{orderbook?.levels?.length ? orderbook.levels.map((level, index) => <tr key={index}><td className="orderbook-price ask">{formatPrice(valueOf(level.askPrice), currency)}</td><td>{formatNumber(valueOf(level.askQuantity))}</td><td className="orderbook-price bid">{formatPrice(valueOf(level.bidPrice), currency)}</td><td>{formatNumber(valueOf(level.bidQuantity))}</td></tr>) : <tr><td className="orderbook-empty" colSpan="4">수신 대기 중</td></tr>}</tbody>{orderbook?.levels?.length > 0 && <tfoot><tr><th>매도합</th><td>{formatNumber(totalAskQuantity)}</td><th>매수합</th><td>{formatNumber(totalBidQuantity)}</td></tr></tfoot>}</table>
        </div>
      </aside>
    </div>
  );
}
