import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import PageLoading from "../components/common/PageLoading.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import "../styles/market-data.css";

const WIDTH = 920;
const HEIGHT = 430;
const PADDING = { left: 36, right: 84, top: 30, bottom: 42 };
const number = (value) => Number(value || 0);
const direction = (value) => value > 0 ? "bullish" : value < 0 ? "bearish" : "flat";

const formatPrice = (value, digits) => new Intl.NumberFormat("ko-KR", { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(number(value));
const formatVolume = (value) => value == null ? "-" : new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 }).format(number(value));

export function MarketDataIndexPage() {
  return <div className="page"><Header /><main className="main"><section className="content"><div className="page-heading"><span className="eyebrow">MARKET DATA</span><h1>환율 / 지수 정보</h1><p>주요 환율과 국내외 주가지수의 흐름을 확인합니다.</p></div><section><div className="section-title"><h2>조회 메뉴</h2><p>KIS에서 필요한 기간의 일봉을 조회하고 저장해 표시합니다.</p></div><div className="menu"><Link className="menu-item" to="/investments/exchanges"><strong>실시간 환율</strong><span>USD/KRW 환율과 기간별 가격 흐름</span></Link><Link className="menu-item" to="/investments/indices"><strong>실시간 지수 시세</strong><span>KOSPI·KOSDAQ·NASDAQ 지수 흐름</span></Link></div><p className="action-row"><Link className="button secondary" to="/investments">투자 홈</Link></p></section></section></main></div>;
}

function MarketLineChart({ data, summary }) {
  const [hoverIndex, setHoverIndex] = useState(null);
  const points = useMemo(() => {
    if (!data.prices.length) return [];
    const closes = data.prices.map((item) => number(item.close));
    const min = Math.min(...closes);
    const max = Math.max(...closes);
    const range = max - min || Math.max(max * 0.02, 1);
    const plotWidth = WIDTH - PADDING.left - PADDING.right;
    const plotHeight = HEIGHT - PADDING.top - PADDING.bottom;
    return data.prices.map((item, index) => ({
      ...item,
      x: PADDING.left + plotWidth * index / Math.max(1, data.prices.length - 1),
      y: PADDING.top + (max - number(item.close)) / range * plotHeight
    }));
  }, [data.prices]);
  if (!points.length) return <p className="chart-empty-message">표시할 일봉 데이터가 없습니다.</p>;
  const hovered = hoverIndex == null ? null : points[hoverIndex];
  const maximum = points.reduce((left, item) => number(item.close) > number(left.close) ? item : left);
  const minimum = points.reduce((left, item) => number(item.close) < number(left.close) ? item : left);
  const ticks = [summary.max, (summary.max + summary.min) / 2, summary.min];
  const yFor = (value) => PADDING.top + (summary.max - value) / (summary.max - summary.min || 1) * (HEIGHT - PADDING.top - PADDING.bottom);
  const onMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width * WIDTH;
    const ratio = Math.max(0, Math.min(1, (x - PADDING.left) / (WIDTH - PADDING.left - PADDING.right)));
    setHoverIndex(Math.round(ratio * (points.length - 1)));
  };
  return <div className="market-chart-scroll"><svg className="market-data-line-chart" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={`${data.displayName} 종가 차트`} onPointerMove={onMove} onPointerLeave={() => setHoverIndex(null)}>{ticks.map((tick) => <g key={tick}><line className="chart-grid-line" x1={PADDING.left} x2={WIDTH - PADDING.right} y1={yFor(tick)} y2={yFor(tick)} /><text className="chart-axis-label" x={WIDTH - PADDING.right + 8} y={yFor(tick) + 4}>{formatPrice(tick, data.fractionDigits)}</text></g>)}<polyline className="market-line" points={points.map((point) => `${point.x},${point.y}`).join(" ")} /><line className={`chart-current-line ${summary.className}`} x1={PADDING.left} x2={WIDTH - PADDING.right} y1={points.at(-1).y} y2={points.at(-1).y} />{[[maximum,"maximum","최고"],[minimum,"minimum","최저"]].map(([point,className,label]) => <g key={className}><circle className={`chart-extrema-point ${className}`} cx={point.x} cy={point.y} r="5" /><text className={`chart-extrema-label ${className}`} x={point.x + 8} y={point.y - 8}>{label} {formatPrice(point.close, data.fractionDigits)} · {point.date}</text></g>)}{hovered && <g><line className="chart-crosshair-line" x1={hovered.x} x2={hovered.x} y1={PADDING.top} y2={HEIGHT - PADDING.bottom} /><line className="chart-crosshair-line" x1={PADDING.left} x2={WIDTH - PADDING.right} y1={hovered.y} y2={hovered.y} /><circle className="chart-crosshair-point" cx={hovered.x} cy={hovered.y} r="5" /></g>}<rect className="chart-hover-layer" x={PADDING.left} y={PADDING.top} width={WIDTH - PADDING.left - PADDING.right} height={HEIGHT - PADDING.top - PADDING.bottom} /></svg>{hovered && <div className="market-data-tooltip" style={{ left: `${Math.min(72, Math.max(2, hovered.x / WIDTH * 100))}%`, top: `${Math.max(2, hovered.y / HEIGHT * 100)}%` }}><strong>{hovered.date}</strong><dl><dt>시가</dt><dd>{formatPrice(hovered.open, data.fractionDigits)}</dd><dt>고가</dt><dd>{formatPrice(hovered.high, data.fractionDigits)}</dd><dt>저가</dt><dd>{formatPrice(hovered.low, data.fractionDigits)}</dd><dt>종가</dt><dd>{formatPrice(hovered.close, data.fractionDigits)}</dd><dt>거래량</dt><dd>{formatVolume(hovered.volume)}</dd></dl></div>}</div>;
}

export function MarketDataDetailPage({ type }) {
  const title = type === "EXCHANGE_RATE" ? "실시간 환율" : "실시간 지수";
  useDocumentTitle(`${title} | FinMate`);
  const location = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [realtime, setRealtime] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => { const controller = new AbortController(); const params = new URLSearchParams(location.search); params.set("type", type); getJson(`/api/investment-read/market?${params}`, { signal: controller.signal }).then((value) => { setData(value); setError(null); }).catch((value) => value.name !== "AbortError" && setError(value)); return () => controller.abort(); }, [location.search, type]);
  useEffect(() => {
    if (!data?.indicator) return undefined;
    const controller = new AbortController();
    const loadRealtime = () => getJson(`/investments/market-data/realtime?indicator=${data.indicator}`, { signal: controller.signal })
      .then(setRealtime)
      .catch((requestError) => { if (requestError.name !== "AbortError") setRealtime(null); });
    loadRealtime();
    const timer = window.setInterval(loadRealtime, 60000);
    return () => { controller.abort(); window.clearInterval(timer); };
  }, [data?.indicator]);
  const update = (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); navigate(`${location.pathname}?indicator=${form.get("indicator")}&period=${form.get("period")}`); };
  if (!data) return <div className="page"><Header /><main className="main"><section className="content">{error ? <p className="overview-error">{error.message}</p> : <PageLoading message="시장 데이터를 불러오고 있습니다." />}</section></main></div>;
  const latest = data.prices.at(-1); const previous = data.prices.at(-2); const latestValue = realtime?.currentPrice == null ? number(latest?.close) : number(realtime.currentPrice); const previousValue = number(previous?.close); const change = realtime?.change == null ? latestValue - previousValue : number(realtime.change); const rate = realtime?.changeRate == null ? (previousValue ? change / previousValue * 100 : 0) : number(realtime.changeRate); const closes = data.prices.map((item) => number(item.close)); const summary = { latest: latestValue, change, rate, className: realtime?.priceChangeClass || direction(change), min: Math.min(...closes), max: Math.max(...closes) }; const periodLabel = data.periods.find((item) => item.value === data.period)?.label || data.period;
  return <div className="page"><Header /><main className="main"><section className="content market-realtime-page"><section className="market-hero"><div className="market-hero-main"><div><div className="market-title-line"><span className="market-badge">{type === "EXCHANGE_RATE" ? "환율" : "지수"}</span><span className="market-symbol">{data.indicator}</span></div><h1>{data.displayName} <span>({data.nameKo})</span></h1><p className="market-description">{data.description}</p></div><div className="market-price-row"><strong className="market-current-price">{formatPrice(summary.latest, data.fractionDigits)}</strong><span className={`chart-price-change ${summary.className}`}><span>{change > 0 ? "+" : ""}{formatPrice(change, data.fractionDigits)}</span><span>{rate > 0 ? "+" : ""}{rate.toFixed(2)}%</span></span><span className="market-latest-date">기준 {realtime?.tradeDate || latest?.date || "-"}</span><span className="market-realtime-status">{realtime ? "실시간 시세 반영" : data.realtimeMode === "WEBSOCKET" ? "실시간 연결 준비" : "1분 캐시 준비"}</span></div></div><div className="market-hero-stats"><div className="market-stat featured"><span>현재가</span><strong>{formatPrice(summary.latest, data.fractionDigits)}</strong></div><div className="market-stat"><span>단위</span><strong>{data.unit}</strong></div><div className="market-stat"><span>조회 기간</span><strong>{periodLabel}</strong></div><div className="market-stat"><span>기간 최고</span><strong>{formatPrice(summary.max, data.fractionDigits)}</strong></div><div className="market-stat"><span>기간 최저</span><strong>{formatPrice(summary.min, data.fractionDigits)}</strong></div></div></section><section className="market-chart-section"><div className="chart-section-header"><div><span className="section-eyebrow">MARKET CHART</span><h2>일봉 차트</h2><p>선택한 기간의 종가 흐름을 한 화면에서 확인합니다.</p></div>{data.savedDailyPriceCount > 0 && <span className="saved-data-pill">새 데이터 {data.savedDailyPriceCount}건 저장</span>}</div><form className="market-control-bar" onSubmit={update}><label>대상<select name="indicator" defaultValue={data.indicator}>{data.indicators.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><label>기간<select name="period" defaultValue={data.period}>{data.periods.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><button>조회</button></form><div className="market-chart-layout"><div className="market-chart-card"><div className="market-chart-heading"><div className="chart-price"><strong>{formatPrice(summary.latest, data.fractionDigits)}</strong><span className={`chart-price-change ${summary.className}`}>{change > 0 ? "+" : ""}{formatPrice(change, data.fractionDigits)} · {rate > 0 ? "+" : ""}{rate.toFixed(2)}%</span><span>{realtime?.tradeDate || latest?.date}</span></div><div className="chart-legend"><span className="legend-item"><span className="legend-color" />종가</span></div></div><MarketLineChart data={data} summary={summary} /><p className="market-chart-caption">{data.prices[0]?.date}부터 {latest?.date}까지의 {periodLabel} 일봉입니다.</p></div><aside className="chart-side-panel"><div className="chart-side-item featured"><span>대상</span><strong>{data.displayName}</strong><small>{data.description}</small></div><div className="chart-side-item"><span>구분</span><strong>{type === "EXCHANGE_RATE" ? "환율" : "주가지수"}</strong></div><div className="chart-side-item"><span>기간</span><strong>{periodLabel}</strong></div><div className="chart-side-item"><span>최근 거래일</span><strong>{latest?.date}</strong></div><div className="chart-side-item"><span>표시 일수</span><strong>{data.prices.length}</strong></div><div className="chart-side-item"><span>기간 최고 / 최저</span><strong>{formatPrice(summary.max, data.fractionDigits)} / {formatPrice(summary.min, data.fractionDigits)}</strong></div><div className="chart-side-item"><span>이번 접근에서 저장</span><strong>{data.savedDailyPriceCount}건</strong></div></aside></div></section><section className="market-table-section"><div className="market-table-header"><div><span className="section-eyebrow">RECENT PRICES</span><h2>최근 일봉</h2></div><p>최근 저장된 일봉 데이터를 표로 확인합니다.</p></div><div className="table-scroll"><table className="records-table market-price-table"><thead><tr><th>거래일</th><th>시가</th><th>고가</th><th>저가</th><th>종가</th><th>거래량</th></tr></thead><tbody>{data.prices.slice(-30).reverse().map((item) => <tr key={item.date}><td>{item.date}</td><td>{formatPrice(item.open, data.fractionDigits)}</td><td>{formatPrice(item.high, data.fractionDigits)}</td><td>{formatPrice(item.low, data.fractionDigits)}</td><td>{formatPrice(item.close, data.fractionDigits)}</td><td>{formatVolume(item.volume)}</td></tr>)}</tbody></table></div></section><section className="market-action-section"><Link className="market-action-button primary" to="/investments/market-data">환율 / 지수 메뉴</Link><div className="market-action-links"><Link className="market-action-button" to="/investments/exchanges">실시간 환율</Link><Link className="market-action-button" to="/investments/indices">실시간 지수 시세</Link><Link className="market-action-button" to="/investments">투자 홈</Link></div></section></section></main></div>;
}
