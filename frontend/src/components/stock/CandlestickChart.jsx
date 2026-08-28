import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  createStockPriceLine,
  deleteAllStockPriceLines,
  deleteStockPriceLine,
  getStockPriceLines
} from "../../api/stockPriceLines.js";

const DEFAULT_VISIBLE_CANDLES = 63;
const MINIMUM_VISIBLE_CANDLES = 20;
const CHART_HEIGHT = 620;
// 우측 여백은 가격 축 숫자와 현재가·사용자선 배지가 차트 밖에서 잘리지 않도록 넉넉하게 확보한다.
const PADDING = { top: 28, right: 148, bottom: 30, left: 18 };
const VOLUME_PANEL_HEIGHT = 118;
const VOLUME_PANEL_GAP = 26;
const KOREAN_WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

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

function formatVolumeAxis(value) {
  const absolute = Math.abs(value);
  const formatUnit = (divisor, unit) => {
    const scaled = value / divisor;
    const digits = Math.abs(scaled) >= 10 || Number.isInteger(scaled) ? 0 : 1;
    return `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: digits }).format(scaled)}${unit}`;
  };
  if (absolute >= 100_000_000) return formatUnit(100_000_000, "억");
  if (absolute >= 10_000) return formatUnit(10_000, "만");
  if (absolute >= 1_000) return formatUnit(1_000, "천");
  return formatNumber(value);
}

function niceVolumeMaximum(value) {
  if (!Number.isFinite(value) || value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const ceiling = normalized <= 2 ? 2 : normalized <= 3 ? 3 : normalized <= 5 ? 5 : 10;
  return ceiling * magnitude;
}

function formatChartDate(value, includeWeekday = false, minuteInterval = false) {
  const [datePart, timePart] = String(value ?? "").split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  if (!year || !month || !day) return value || "-";
  const date = new Date(Date.UTC(year, month - 1, day));
  const formatted = `${year}.${String(month).padStart(2, "0")}.${String(day).padStart(2, "0")}`;
  const dateLabel = includeWeekday ? `${formatted}(${KOREAN_WEEKDAYS[date.getUTCDay()]})` : formatted;
  if (!minuteInterval || !timePart) return dateLabel;
  const timeLabel = timePart.slice(0, 5);
  return includeWeekday ? `${dateLabel} ${timeLabel}` : `${String(month).padStart(2, "0")}.${String(day).padStart(2, "0")} ${timeLabel}`;
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

export default function CandlestickChart({
  candles = [],
  currency = "KRW",
  interval,
  intervalLabel,
  detail,
  orderbook,
  stockId
}) {
  const canvasRef = useRef(null);
  const dragRef = useRef(null);
  const rangeDragRef = useRef(null);
  const wheelPanRemainderRef = useRef(0);
  const wheelHandlerRef = useRef(null);
  const chartGeometryRef = useRef(null);
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
  const [priceLines, setPriceLines] = useState([]);
  const [lineMode, setLineMode] = useState(false);
  const [lineSaving, setLineSaving] = useState(false);
  const [lineError, setLineError] = useState(null);
  const [lineContextMenu, setLineContextMenu] = useState(null);
  const minuteInterval = interval?.startsWith("MINUTE_");

  useEffect(() => {
    if (!stockId) return undefined;
    const controller = new AbortController();
    getStockPriceLines(stockId, { signal: controller.signal })
      .then((lines) => {
        setPriceLines(lines || []);
        setLineError(null);
      })
      .catch((error) => {
        if (error.name !== "AbortError") setLineError(error.message);
      });
    return () => controller.abort();
  }, [stockId]);

  useEffect(() => {
    const closeMenu = () => setLineContextMenu(null);
    window.addEventListener("pointerdown", closeMenu);
    return () => window.removeEventListener("pointerdown", closeMenu);
  }, []);

  useEffect(() => {
    const count = Math.min(DEFAULT_VISIBLE_CANDLES, Math.max(normalized.length, 1));
    const timer = window.setTimeout(() => {
      setVisibleCount(count);
      setStartIndex(Math.max(0, normalized.length - count));
      setHoverIndex(null);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [normalized.length]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const handleWheel = (event) => wheelHandlerRef.current?.(event);
    canvas.addEventListener("wheel", handleWheel, { passive: false });
    return () => canvas.removeEventListener("wheel", handleWheel);
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
      const volumeHeight = VOLUME_PANEL_HEIGHT;
      const volumeTop = CHART_HEIGHT - PADDING.bottom - volumeHeight;
      const priceBottom = volumeTop - VOLUME_PANEL_GAP;
      const priceHeight = priceBottom - PADDING.top;
      const highest = Math.max(...viewport.map((item) => item.high));
      const lowest = Math.min(...viewport.map((item) => item.low));
      const priceMargin = Math.max((highest - lowest) * 0.09, highest * 0.004);
      const maxPrice = highest + priceMargin;
      const minPrice = lowest - priceMargin;
      const maxVolume = niceVolumeMaximum(Math.max(...viewport.map((item) => item.volume), 1));
      const slot = plotWidth / viewport.length;
      const x = (index) => PADDING.left + slot * (index + 0.5);
      const y = (price) => PADDING.top + (maxPrice - price) / (maxPrice - minPrice) * priceHeight;
      chartGeometryRef.current = { width, maxPrice, minPrice, priceHeight, priceBottom };

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

      // 거래량을 가격 영역과 분리하고, 우측 축에 만·억 단위를 표시한다.
      context.fillStyle = "#f8fafc";
      context.fillRect(PADDING.left, volumeTop, plotWidth, volumeHeight);
      context.strokeStyle = "#cbd5e1";
      context.beginPath();
      context.moveTo(PADDING.left, volumeTop);
      context.lineTo(width - PADDING.right, volumeTop);
      context.stroke();
      context.fillStyle = "#475569";
      context.font = "700 11px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
      context.textAlign = "left";
      context.fillText("거래량", PADDING.left + 4, volumeTop - 8);
      for (let line = 0; line <= 3; line += 1) {
        const lineY = volumeTop + volumeHeight * line / 3;
        const volume = maxVolume * (1 - line / 3);
        context.strokeStyle = line === 3 ? "#cbd5e1" : "#e2e8f0";
        context.lineWidth = 1;
        context.beginPath();
        context.moveTo(PADDING.left, lineY);
        context.lineTo(width - PADDING.right, lineY);
        context.stroke();
        if (line < 3) {
          context.fillStyle = "#64748b";
          context.font = "700 11px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
          context.fillText(formatVolumeAxis(volume), width - PADDING.right + 9, lineY + 4);
        }
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

      if (minuteInterval) {
        viewport.forEach((candle, index) => {
          if (index === 0 || String(candle.tradeDate).slice(0, 10) === String(viewport[index - 1].tradeDate).slice(0, 10)) return;
          const boundaryX = x(index) - slot / 2;
          context.save();
          context.setLineDash([5, 4]);
          context.strokeStyle = "#94a3b8";
          context.beginPath();
          context.moveTo(boundaryX, PADDING.top);
          context.lineTo(boundaryX, volumeTop + volumeHeight);
          context.stroke();
          context.restore();
          context.fillStyle = "#475569";
          context.font = "700 10px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
          context.textAlign = "left";
          context.fillText(formatChartDate(candle.tradeDate), boundaryX + 5, PADDING.top + 12);
        });
      }

      viewport.forEach((candle, index) => {
        const rising = candle.close >= candle.open;
        const color = rising ? "#dc2626" : "#2563eb";
        const center = x(index);
        const bodyWidth = Math.max(2, Math.min(slot * 0.62, 11));
        const forming = candle.completed === false;
        context.strokeStyle = color;
        context.fillStyle = color;
        context.lineWidth = 1.2;
        if (forming) {
          context.save();
          context.setLineDash([3, 2]);
          context.globalAlpha = 0.72;
        }
        context.beginPath();
        context.moveTo(center, y(candle.high));
        context.lineTo(center, y(candle.low));
        context.stroke();
        const bodyTop = y(Math.max(candle.open, candle.close));
        const bodyBottom = y(Math.min(candle.open, candle.close));
        if (forming) {
          context.strokeRect(center - bodyWidth / 2, bodyTop, bodyWidth, Math.max(2, bodyBottom - bodyTop));
          context.restore();
        } else {
          context.fillRect(center - bodyWidth / 2, bodyTop, bodyWidth, Math.max(1.5, bodyBottom - bodyTop));
        }
        const volumeBarHeight = Math.max(candle.volume > 0 ? 1.5 : 0, volumeHeight * candle.volume / maxVolume);
        context.globalAlpha = 0.72;
        context.fillRect(
          center - bodyWidth / 2,
          volumeTop + volumeHeight - volumeBarHeight,
          bodyWidth,
          volumeBarHeight
        );
        context.globalAlpha = 1;
      });

      const latestPrice = valueOf(detail?.latestClosePrice) || viewport.at(-1)?.close;
      const latestPriceY = latestPrice >= minPrice && latestPrice <= maxPrice ? y(latestPrice) : null;

      // 사용자가 캔들의 종가를 기준으로 저장한 지지선·저항선을 가격 영역에 표시한다.
      priceLines.forEach((line) => {
        const price = valueOf(line.price);
        if (price < minPrice || price > maxPrice) return;
        const lineY = y(price);

        // 넓고 투명한 바탕선을 먼저 그려 캔들과 이동평균선 위에서도 사용자선이 묻히지 않게 한다.
        context.save();
        context.strokeStyle = "rgba(109, 40, 217, 0.18)";
        context.lineWidth = 7;
        context.beginPath();
        context.moveTo(PADDING.left, lineY);
        context.lineTo(width - PADDING.right, lineY);
        context.stroke();
        context.restore();

        // 실제 사용자선은 점선이 아닌 굵은 실선으로 표시해 현재가선·격자선과 명확히 구분한다.
        context.save();
        context.strokeStyle = "#6d28d9";
        context.lineWidth = 2.6;
        context.shadowColor = "rgba(109, 40, 217, 0.34)";
        context.shadowBlur = 4;
        context.beginPath();
        context.moveTo(PADDING.left, lineY);
        context.lineTo(width - PADDING.right, lineY);
        context.stroke();
        context.restore();

        context.fillStyle = "#6d28d9";
        context.beginPath();
        context.arc(PADDING.left + 4, lineY, 4, 0, Math.PI * 2);
        context.fill();

        context.font = "800 11px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
        const label = `사용자선 ${formatPrice(price, currency)}`;
        const labelWidth = Math.max(94, context.measureText(label).width + 16);
        // 현재가 가격표와 동일하게 가격 영역 오른쪽 축 공간에 배치한다.
        const labelX = width - PADDING.right + 4;
        // 사용자선과 현재가가 거의 같으면 두 배지가 겹치지 않도록 사용자선 배지만 위·아래로 이동한다.
        const labelY = latestPriceY !== null && Math.abs(latestPriceY - lineY) < 24
          ? (lineY > PADDING.top + 26 ? lineY - 24 : lineY + 24)
          : lineY;
        context.fillStyle = "#6d28d9";
        context.fillRect(labelX, labelY - 11, labelWidth, 22);
        context.fillStyle = "#ffffff";
        context.textAlign = "center";
        context.fillText(label, labelX + labelWidth / 2, labelY + 4);
      });

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
          context.fillText(formatChartDate(candle.tradeDate, false, minuteInterval), x(index), CHART_HEIGHT - 8);
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
  }, [changeAmount, currency, detail?.latestClosePrice, hoverIndex, minuteInterval, normalized, priceLines, startIndex, viewport]);

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
  useEffect(() => {
    wheelHandlerRef.current = zoom;
  });

  const createLineFromCandle = async (candle) => {
    if (!stockId || !candle || lineSaving) return;
    setLineSaving(true);
    setLineError(null);
    try {
      const created = await createStockPriceLine(stockId, candle.closePrice ?? candle.close);
      setPriceLines((current) => current.some((line) => Number(line.id) === Number(created.id))
        ? current
        : [...current, created]);
      setLineMode(false);
    } catch (error) {
      setLineError(error.message);
    } finally {
      setLineSaving(false);
    }
  };

  const removePriceLine = async (line) => {
    if (!stockId || !line || lineSaving) return;
    setLineSaving(true);
    setLineError(null);
    try {
      await deleteStockPriceLine(stockId, line.id);
      setPriceLines((current) => current.filter((item) => Number(item.id) !== Number(line.id)));
      setLineContextMenu(null);
    } catch (error) {
      setLineError(error.message);
    } finally {
      setLineSaving(false);
    }
  };

  const resetPriceLines = async () => {
    if (!stockId || !priceLines.length || lineSaving) return;
    if (!window.confirm("이 종목에 만든 가로선을 모두 삭제할까요?")) return;
    setLineSaving(true);
    setLineError(null);
    try {
      await deleteAllStockPriceLines(stockId);
      setPriceLines([]);
      setLineContextMenu(null);
    } catch (error) {
      setLineError(error.message);
    } finally {
      setLineSaving(false);
    }
  };

  const openPriceLineContextMenu = (event) => {
    event.preventDefault();
    const geometry = chartGeometryRef.current;
    const canvas = canvasRef.current;
    if (!geometry || !canvas || !priceLines.length) {
      setLineContextMenu(null);
      return;
    }
    const rect = canvas.getBoundingClientRect();
    const localY = event.clientY - rect.top;
    const visibleLines = priceLines
      .map((line) => {
        const price = valueOf(line.price);
        const y = PADDING.top + (geometry.maxPrice - price)
          / (geometry.maxPrice - geometry.minPrice) * geometry.priceHeight;
        return { line, y, distance: Math.abs(y - localY) };
      })
      .filter((item) => item.y >= PADDING.top && item.y <= geometry.priceBottom)
      .sort((first, second) => first.distance - second.distance);
    if (!visibleLines.length || visibleLines[0].distance > 10) {
      setLineContextMenu(null);
      return;
    }
    setLineContextMenu({
      line: visibleLines[0].line,
      x: Math.min(event.clientX - rect.left, rect.width - 156),
      y: Math.min(event.clientY - rect.top, rect.height - 52)
    });
  };

  if (!normalized.length) return <p>표시할 {intervalLabel || "차트"} 데이터가 없습니다.</p>;

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
            <span className="chart-price-time">{minuteInterval ? formatChartDate(detail?.latestCandleAt, false, true) : detail?.latestTradeDate || "-"}</span>
            <span className="market-session-badges" aria-label="시장별 거래 상태">
              {(detail?.marketSessions || []).map((session) => (
                <span className={`market-session-badge ${session.open ? "open" : "closed"}`} key={session.market}>
                  {session.market} {session.status}
                </span>
              ))}
            </span>
          </div>
          <div className="chart-header-tools">
            <div className="chart-legend">
              <span className="legend-item"><span className="legend-color ma5" />MA5</span>
              <span className="legend-item"><span className="legend-color ma20" />MA20</span>
              <span className="legend-item"><span className="legend-color ma60" />MA60</span>
              {priceLines.length > 0 && <span className="legend-item"><span className="legend-color user-price-line" />사용자선</span>}
              {normalized.at(-1)?.completed === false && <span className="legend-item"><span className="legend-color forming" />형성 중</span>}
            </div>
            <div className="price-line-toolbar" aria-label="차트 가로선 도구">
              <button
                className={`price-line-button${lineMode ? " active" : ""}`}
                type="button"
                disabled={lineSaving}
                aria-pressed={lineMode}
                onClick={() => {
                  setLineMode((current) => !current);
                  setLineContextMenu(null);
                  setLineError(null);
                }}
              >
                {lineMode ? "선 만들기 취소" : "＋ 선 만들기"}
              </button>
              <button
                className="price-line-reset-button"
                type="button"
                disabled={lineSaving || priceLines.length === 0}
                onClick={resetPriceLines}
              >
                전체 초기화{priceLines.length ? ` (${priceLines.length})` : ""}
              </button>
            </div>
          </div>
        </div>

        {lineMode && <div className="price-line-mode-hint" role="status">가로선을 만들 캔들을 클릭하세요. 해당 캔들의 종가에 선이 저장됩니다.</div>}
        {lineError && <div className="price-line-error" role="alert">{lineError}</div>}

        <div
          className={`chart-scroll${dragging ? " dragging" : ""}${lineMode ? " line-mode" : ""}`}
          aria-label={`마우스 휠로 확대·축소하고 드래그로 이동할 수 있는 ${intervalLabel || "캔들"} 차트`}
          onDoubleClick={resetViewport}
        >
          <canvas
            ref={canvasRef}
            className="stock-chart-canvas"
            role="img"
            aria-label={`${intervalLabel || "캔들"} 가격, 거래량 및 이동평균선 차트`}
            onContextMenu={openPriceLineContextMenu}
            onPointerMove={(event) => {
              if (dragRef.current) {
                event.preventDefault();
                if (Math.abs(event.clientX - dragRef.current.clientX) > 4) dragRef.current.moved = true;
                if (!dragRef.current.lineSelection) {
                  const slotWidth = Math.max(4, (event.currentTarget.clientWidth - PADDING.left - PADDING.right) / visibleCount);
                  const movedSlots = Math.round((event.clientX - dragRef.current.clientX) / slotWidth);
                  setStartIndex(clampStart(dragRef.current.startIndex - movedSlots));
                }
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
              event.preventDefault();
              dragRef.current = { clientX: event.clientX, startIndex, moved: false, lineSelection: lineMode };
              setDragging(!lineMode);
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerUp={(event) => {
              const pointerState = dragRef.current;
              if (pointerState?.lineSelection && !pointerState.moved) {
                void createLineFromCandle(viewport[pointerIndex(event)]);
              }
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
          {lineContextMenu && (
            <div
              className="price-line-context-menu"
              style={{ left: lineContextMenu.x, top: lineContextMenu.y }}
              onPointerDown={(event) => event.stopPropagation()}
            >
              <strong>{formatPrice(valueOf(lineContextMenu.line.price), currency)}</strong>
              <button type="button" disabled={lineSaving} onClick={() => removePriceLine(lineContextMenu.line)}>가로선 삭제</button>
            </div>
          )}
          {hovered && (
            <div className="chart-tooltip visible" aria-hidden="false" style={{ left: hoverPoint.x, top: hoverPoint.y }}>
              <div className="chart-tooltip-title">{formatChartDate(hovered.tradeDate, true, minuteInterval)}{hovered.completed === false ? " · 형성 중" : ""}</div>
              {[["시가", hovered.open, previous?.close], ["고가", hovered.high, previous?.close], ["저가", hovered.low, previous?.close], ["종가", hovered.close, previous?.close]].map(([label, value, comparison]) => {
                const change = rate(value, comparison);
                return <div className="chart-tooltip-row" key={label}><span className="chart-tooltip-label">{label}</span><span className={`chart-tooltip-value ${directionClass(change || 0)}`}>{formatPrice(value, currency)}{change === null ? "" : ` (${change > 0 ? "+" : ""}${change.toFixed(2)}%)`}</span></div>;
              })}
              <div className="chart-tooltip-row"><span className="chart-tooltip-label">거래량</span><span className="chart-tooltip-value">{formatNumber(hovered.volume)}주</span></div>
              <div className="chart-tooltip-row"><span className="chart-tooltip-label">거래대금</span><span className="chart-tooltip-value">{formatNumber(hovered.amount)}</span></div>
            </div>
          )}
          <div className="chart-interaction-guide">최근 63개 봉부터 표시 · 전체 기간까지 축소 · 두 손가락 또는 드래그로 좌우 이동</div>
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
        <p className="chart-caption">{minuteInterval ? formatChartDate(normalized[0]?.tradeDate, false, true) : normalized[0]?.tradeDate}부터 {minuteInterval ? formatChartDate(normalized.at(-1)?.tradeDate, false, true) : normalized.at(-1)?.tradeDate}까지의 {minuteInterval ? `${intervalLabel} 봉` : `수정주가 기준 ${intervalLabel}`}입니다.</p>
      </div>

      <aside className="chart-side-panel">
        <div className="chart-side-item"><span>종목</span><strong>{detail?.symbol || "-"}</strong></div>
        <div className="chart-side-item"><span>시장</span><strong>{detail?.market || "-"}</strong></div>
        <div className="chart-side-item"><span>봉 주기</span><strong>{intervalLabel}</strong></div>
        <div className="chart-side-item"><span>{minuteInterval ? "최근 거래시각" : "최근 거래일"}</span><strong>{minuteInterval ? formatChartDate(detail?.latestCandleAt, false, true) : detail?.latestTradeDate || "-"}</strong></div>
        <div className="chart-side-item"><span>봉 수</span><strong>{normalized.length}</strong></div>
        {detail?.chartPriceSummary && <><div className="chart-side-item"><span>기간 최고</span><strong>{formatPrice(valueOf(detail.chartPriceSummary.highestPrice), currency)}</strong><small>{detail.chartPriceSummary.highestTradeDate}</small></div><div className="chart-side-item"><span>기간 최저</span><strong>{formatPrice(valueOf(detail.chartPriceSummary.lowestPrice), currency)}</strong><small>{detail.chartPriceSummary.lowestTradeDate}</small></div></>}
        <div className="orderbook-section">
          <div className="orderbook-header"><strong>실시간 호가</strong><small>{orderbook?.quoteTime || "수신 대기"}</small></div>
          <table className="orderbook-table"><thead><tr><th>매도가</th><th>잔량</th><th>매수가</th><th>잔량</th></tr></thead><tbody>{orderbook?.levels?.length ? orderbook.levels.map((level, index) => <tr key={index}><td className="orderbook-price ask">{formatPrice(valueOf(level.askPrice), currency)}</td><td>{formatNumber(valueOf(level.askQuantity))}</td><td className="orderbook-price bid">{formatPrice(valueOf(level.bidPrice), currency)}</td><td>{formatNumber(valueOf(level.bidQuantity))}</td></tr>) : <tr><td className="orderbook-empty" colSpan="4">수신 대기 중</td></tr>}</tbody>{orderbook?.levels?.length > 0 && <tfoot><tr><th>매도합</th><td>{formatNumber(totalAskQuantity)}</td><th>매수합</th><td>{formatNumber(totalBidQuantity)}</td></tr></tfoot>}</table>
        </div>
      </aside>
    </div>
  );
}
