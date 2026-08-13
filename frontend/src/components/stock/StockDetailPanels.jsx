import { useMemo, useState } from "react";

const number = (value, digits = 0) => value === null || value === undefined
  ? "-"
  : new Intl.NumberFormat("ko-KR", { maximumFractionDigits: digits }).format(Number(value));
const signed = (value, digits = 2) => value === null || value === undefined
  ? "-"
  : `${Number(value) > 0 ? "+" : ""}${number(value, digits)}`;
const quarter = (date) => date ? `${date.slice(0, 4)}년 ${Math.ceil(Number(date.slice(5, 7)) / 3)}분기` : "-";
const quarterLabel = (date) => date ? `${date.slice(2, 4)}.${Math.ceil(Number(date.slice(5, 7)) / 3)}Q` : "-";
const direction = (value) => Number(value) > 0 ? "positive" : Number(value) < 0 ? "negative" : "neutral";

function ConceptButton({ code, title, onConcept, compact = false }) {
  return <button className={`concept-help-button${compact ? " concept-help-button--compact" : ""}`} type="button" onClick={() => onConcept(code, title)} aria-label={`${title} 개념 보기`}>개념 익히기</button>;
}

export function QuotePanel({ detail, onConcept }) {
  const quote = detail.domesticDetailInfo?.quote;
  const metadata = detail.metadataDisplayInfo;
  const metrics = quote ? [
    { label: "현재가 (원)", value: number(quote.currentPrice), note: `전일 대비 ${signed(quote.changeRate)}%`, className: direction(quote.changeRate) },
    { label: "시가 / 고가 / 저가 (원)", value: `${number(quote.openPrice)} / ${number(quote.highPrice)} / ${number(quote.lowPrice)}` },
    { label: "누적 거래량", value: number(quote.accumulatedVolume) },
    { label: "누적 거래대금 (백만원)", value: number(quote.accumulatedTradeAmount) },
    { label: "PER (배)", value: number(quote.per, 2), code: "PER", title: "PER" },
    { label: "PBR (배)", value: number(quote.pbr, 2), code: "PBR", title: "PBR" },
    { label: "EPS (원)", value: number(quote.eps, 2), code: "EPS", title: "EPS" },
    { label: "BPS (원)", value: number(quote.bps, 2), code: "BPS", title: "BPS" },
    { label: "시가총액 (억원)", value: number(quote.marketCap), code: "MARKET_CAP", title: "시가총액" },
    { label: "상장주식 수", value: number(quote.listedShares), code: "LISTED_SHARES", title: "상장주식 수" },
    { label: "52주 최고가 (원)", value: number(quote.w52HighPrice), note: quote.w52HighDate },
    { label: "52주 최저가 (원)", value: number(quote.w52LowPrice), note: quote.w52LowDate },
    { label: "외국인 보유수량", value: number(quote.foreignHoldingQuantity), code: "FOREIGN_HOLDING_QUANTITY", title: "외국인 보유수량" },
    { label: "외국인 소진율", value: `${number(quote.foreignExhaustionRate, 2)}%`, note: "보유 비율이 아닌 KIS 소진율 지표", code: "FOREIGN_EXHAUSTION_RATE", title: "외국인 소진율" }
  ] : [];

  return <>
    <section>
      <h2>종목 정보</h2>
      <div className="stock-summary">
        {[["종목코드", detail.symbol, "code-chip"], ["시장", detail.market, "market-chip"], ["상품유형", detail.securityType, "security-chip"], ["통화", detail.currency, "currency-chip"], ["최근 거래일", detail.latestTradeDate], ["최근 종가", `${detail.currencySymbol}${number(detail.latestClosePrice, detail.priceDecimalDigits)}`]].map(([label, value, className]) => <div className="summary-item" key={label}><strong>{label}</strong><span className={className || ""}>{value || "데이터 없음"}</span></div>)}
      </div>
      {metrics.length > 0 && <div>
        <div className="detail-section-header" style={{ marginTop: 22 }}>
          <div><div className="detail-heading-with-concept"><h3>KIS 주요 시세 지표</h3><ConceptButton code="VALUATION_AND_PROFITABILITY" title="수익성과 기업가치의 관계" onConcept={onConcept} /></div><p>한국투자증권 현재가 조회 기준 지표입니다.</p></div>
          <span className="detail-updated-at">갱신 {detail.domesticDetailInfo.quoteUpdatedAt?.replace("T", " ").slice(0, 16) || "-"}</span>
        </div>
        <div className="kis-metric-grid">{metrics.map((metric) => <div className="kis-metric-card" key={metric.label}><div className="kis-metric-heading"><span className="kis-metric-label">{metric.label}</span>{metric.code && <ConceptButton code={metric.code} title={metric.title} onConcept={onConcept} compact />}</div><strong>{metric.value}</strong>{metric.note && <small className={metric.className || ""}>{metric.note}</small>}</div>)}</div>
      </div>}
      {metadata && <details className="metadata-card">
        <summary className="metadata-header metadata-summary">
          <div><span className="metadata-eyebrow">{metadata.sourceLabel}</span><h3 className="metadata-title">{metadata.title}</h3><p className="metadata-description">{metadata.description}</p></div>
          <div className="metadata-summary-actions"><div className="metadata-badges">{metadata.badges?.map((badge) => <span className={`metadata-badge ${badge.type || ""}`} key={badge.label}>{badge.label}</span>)}</div><span className="metadata-toggle"><span className="metadata-toggle-closed">펼쳐보기</span><span className="metadata-toggle-open">접기</span><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 7.5 5 5 5-5" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" /></svg></span></div>
        </summary>
        <div className="metadata-content"><div className="metadata-section-grid">{metadata.sections?.map((section) => <article className="metadata-section" key={section.title}><h3>{section.title}</h3><p>{section.description}</p><div className="metadata-list">{section.items?.map((item) => <div className="metadata-row" key={item.label}><span className="metadata-label">{item.label}</span><strong className={`metadata-value${item.label.includes("업종") ? " industry-chip" : ""}`}>{item.value}</strong></div>)}</div></article>)}</div></div>
      </details>}
    </section>
  </>;
}

function FinancialMetricCard({ title, metric, onConcept, code, latestPeriod }) {
  if (!metric) return null;
  return <article className="financial-analysis-card"><h3 className="concept-label"><span>{title} 분석</span><ConceptButton code={code} title={title} onConcept={onConcept} compact /></h3><p>{quarter(latestPeriod)} 기준 · 억원</p><strong className="financial-analysis-value">{number(metric.latestQuarter)}</strong><dl className="financial-analysis-list"><div><dt>YoY</dt><dd className={direction(metric.yoyRate)}>{signed(metric.yoyRate)}%</dd></div><div><dt>QoQ</dt><dd className={direction(metric.qoqRate)}>{signed(metric.qoqRate)}%</dd></div><div><dt>TTM</dt><dd>{number(metric.ttm)}</dd></div><div><dt>런레이트</dt><dd>{number(metric.runRate)}</dd></div></dl></article>;
}

const finite = (value) => {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const chronological = (rows, key) => (rows || []).slice().sort((left, right) =>
  String(left[key] || "").localeCompare(String(right[key] || "")));

const chartDomain = (values, includeZero = true) => {
  const valid = values.map(finite).filter((value) => value !== null);
  if (valid.length === 0) return null;
  let minimum = Math.min(...valid);
  let maximum = Math.max(...valid);
  if (includeZero) {
    minimum = Math.min(0, minimum);
    maximum = Math.max(0, maximum);
  }
  const gap = maximum - minimum;
  const padding = gap === 0 ? Math.max(Math.abs(maximum) * 0.15, 1) : gap * 0.12;
  return { minimum: minimum - (includeZero && minimum === 0 ? 0 : padding), maximum: maximum + (includeZero && maximum === 0 ? 0 : padding) };
};

const scaleValue = (value, sourceMinimum, sourceMaximum, targetMinimum, targetMaximum) =>
  targetMinimum + ((value - sourceMinimum) / (sourceMaximum - sourceMinimum || 1)) * (targetMaximum - targetMinimum);

const compact = (value) => {
  const absolute = Math.abs(Number(value));
  if (absolute >= 100000000) return `${number(Number(value) / 100000000, 1)}억`;
  if (absolute >= 10000) return `${number(Number(value) / 10000, 1)}만`;
  return number(value, 1);
};

const shortDate = (value) => {
  const parts = String(value || "").split("-");
  return parts.length >= 3 ? `${parts[1]}.${parts[2]}` : value;
};

const tooltipPosition = (event, rect, tooltipWidth = 230, tooltipHeight = 150) => ({
  left: Math.max(10, Math.min(event.clientX - rect.left + 16, rect.width - tooltipWidth - 10)),
  top: Math.max(10, Math.min(event.clientY - rect.top + 16, rect.height - tooltipHeight - 10))
});

function IndependentBarPanels({ rows, series }) {
  if (!rows?.length) return null;
  const width = 960;
  const height = 340;
  const panelWidth = 296;
  const panelGap = 20;
  const panelStart = 16;
  const plotTop = 48;
  const plotBottom = 282;
  return <svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="분기 실적 흐름">
    {series.map((item, panelIndex) => {
      const panelX = panelStart + panelIndex * (panelWidth + panelGap);
      const domain = chartDomain(rows.map((row) => row[item.key]));
      if (!domain) return <text className="financial-svg-value" key={item.key} x={panelX + panelWidth / 2} y="160" textAnchor="middle">데이터 없음</text>;
      const y = (value) => scaleValue(value, domain.maximum, domain.minimum, plotTop, plotBottom);
      const zeroY = y(0);
      const slotWidth = panelWidth / rows.length;
      const barWidth = Math.min(42, slotWidth * 0.52);
      return <g key={item.key}>
        <text className="financial-svg-title" x={panelX} y="22">{item.label}</text>
        <line className="financial-svg-grid" x1={panelX} x2={panelX + panelWidth} y1={plotTop} y2={plotTop} />
        <line className="financial-svg-grid" x1={panelX} x2={panelX + panelWidth} y1={plotBottom} y2={plotBottom} />
        <line className="financial-svg-zero" x1={panelX} x2={panelX + panelWidth} y1={zeroY} y2={zeroY} />
        {rows.map((row, index) => {
          const value = finite(row[item.key]);
          const centerX = panelX + slotWidth * index + slotWidth / 2;
          if (value === null) return <text className="financial-svg-value" key={row.label} x={centerX} y={zeroY - 8} textAnchor="middle">-</text>;
          const valueY = y(value);
          return <g key={row.label}>
            <rect className={`financial-svg-series--${item.className}`} x={centerX - barWidth / 2} y={Math.min(valueY, zeroY)} width={barWidth} height={Math.max(1, Math.abs(zeroY - valueY))} rx="4"><title>{`${row.label} ${item.label} ${number(value)}억원`}</title></rect>
            <text className="financial-svg-value" x={centerX} y={value >= 0 ? Math.max(plotTop + 10, valueY - 7) : Math.min(plotBottom - 3, valueY + 14)} textAnchor="middle">{compact(value)}</text>
          </g>;
        })}
        {rows.map((row, index) => <text key={row.label} x={panelX + slotWidth * index + slotWidth / 2} y="313" textAnchor="middle">{row.label}</text>)}
      </g>;
    })}
  </svg>;
}

function GroupedBarChart({ rows, series, labelKey = "label", height = 300 }) {
  if (!rows?.length) return null;
  const width = 460;
  const plotLeft = 36;
  const plotRight = 444;
  const plotTop = 24;
  const plotBottom = height - 58;
  const domain = chartDomain(rows.flatMap((row) => series.map((item) => row[item.key])));
  if (!domain) return null;
  const y = (value) => scaleValue(value, domain.maximum, domain.minimum, plotTop, plotBottom);
  const zeroY = y(0);
  const groupWidth = (plotRight - plotLeft) / rows.length;
  const barWidth = 34;
  return <svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img">
    <line className="financial-svg-grid" x1={plotLeft} x2={plotRight} y1={plotTop} y2={plotTop} />
    <line className="financial-svg-grid" x1={plotLeft} x2={plotRight} y1={plotBottom} y2={plotBottom} />
    <line className="financial-svg-zero" x1={plotLeft} x2={plotRight} y1={zeroY} y2={zeroY} />
    <text x={plotLeft} y={plotTop + 10}>{number(domain.maximum, 1)}</text><text x={plotLeft} y={plotBottom - 4}>{number(domain.minimum, 1)}</text>
    {rows.map((row, rowIndex) => {
      const centerX = plotLeft + groupWidth * rowIndex + groupWidth / 2;
      return <g key={row[labelKey]}>{series.map((item, seriesIndex) => {
        const value = finite(row[item.key]);
        if (value === null) return null;
        const x = centerX + (seriesIndex - series.length / 2) * (barWidth + 6) + 3;
        const valueY = y(value);
        return <g key={item.key}><rect className={`financial-svg-series--${item.className}`} x={x} y={Math.min(valueY, zeroY)} width={barWidth} height={Math.max(1, Math.abs(valueY - zeroY))} rx="4"><title>{`${row[labelKey]} · ${item.label} ${number(value, 2)}`}</title></rect><text className="financial-svg-value" x={x + barWidth / 2} y={value >= 0 ? Math.max(plotTop + 10, valueY - 7) : Math.min(plotBottom - 3, valueY + 14)} textAnchor="middle">{number(value, 1)}</text></g>;
      })}<text className="financial-svg-title" x={centerX} y={height - 28} textAnchor="middle">{row[labelKey]}</text></g>;
    })}
  </svg>;
}

function AnnualizedChart({ rows }) {
  if (!rows?.length) return null;
  const width = 460;
  const height = 300;
  const plotLeft = 92;
  const plotRight = 438;
  return <svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img">
    {rows.map((row, index) => {
      const rowTop = 28 + index * 86;
      const domain = chartDomain([row.ttm, row.runRate]);
      if (!domain) return null;
      const x = (value) => scaleValue(value, domain.minimum, domain.maximum, plotLeft, plotRight);
      const zeroX = x(0);
      return <g key={row.label}><text className="financial-svg-title" x="12" y={rowTop + 32}>{row.label}</text><line className="financial-svg-grid" x1={plotLeft} x2={plotRight} y1={rowTop + 70} y2={rowTop + 70} /><line className="financial-svg-zero" x1={zeroX} x2={zeroX} y1={rowTop} y2={rowTop + 62} />
        {[{ key: "ttm", label: "TTM", y: rowTop + 7, className: "ttm" }, { key: "runRate", label: "런레이트", y: rowTop + 38, className: "run-rate" }].map((item) => { const value = finite(row[item.key]); if (value === null) return null; const valueX = x(value); return <g key={item.key}><rect className={`financial-svg-series--${item.className}`} x={Math.min(zeroX, valueX)} y={item.y} width={Math.max(1, Math.abs(valueX - zeroX))} height="20" rx="3"><title>{`${row.label} ${item.label} ${number(value)}억원`}</title></rect><text className="financial-svg-value" x={value >= 0 ? Math.min(plotRight - 2, valueX + 5) : Math.max(plotLeft + 2, valueX - 5)} y={item.y + 15} textAnchor={value >= 0 ? "start" : "end"}>{compact(value)}</text></g>; })}
      </g>;
    })}
  </svg>;
}

function IndependentLinePanels({ rows, series }) {
  if (!rows?.length) return null;
  const width = 960;
  const height = 330;
  const panelWidth = 296;
  const panelGap = 20;
  const panelStart = 16;
  const plotTop = 54;
  const plotBottom = 248;
  return <svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img">
    {series.map((item, panelIndex) => {
      const panelX = panelStart + panelIndex * (panelWidth + panelGap);
      const domain = chartDomain(rows.map((row) => row[item.key]), false);
      if (!domain) return null;
      const x = (index) => rows.length === 1 ? panelX + panelWidth / 2 : panelX + panelWidth * index / (rows.length - 1);
      const y = (value) => scaleValue(value, domain.maximum, domain.minimum, plotTop, plotBottom);
      const valid = rows.map((row, index) => ({ value: finite(row[item.key]), index })).filter((point) => point.value !== null);
      return <g key={item.key}><text className="financial-svg-title" x={panelX} y="23">{item.label} ({item.unit})</text><line className="financial-svg-grid" x1={panelX} x2={panelX + panelWidth} y1={plotTop} y2={plotTop} /><line className="financial-svg-grid" x1={panelX} x2={panelX + panelWidth} y1={plotBottom} y2={plotBottom} /><polyline className={`financial-svg-line financial-svg-series--${item.className}`} points={valid.map((point) => `${x(point.index)},${y(point.value)}`).join(" ")} />
        {rows.map((row, index) => <g key={row.label}><text x={x(index)} y="282" textAnchor="middle">{row.label}</text>{finite(row[item.key]) !== null && <><circle className={`financial-svg-series--${item.className}`} cx={x(index)} cy={y(finite(row[item.key]))} r="4.5"><title>{`${row.label} ${item.label} ${number(row[item.key], 2)}${item.unit}`}</title></circle><text className="financial-svg-value" x={x(index)} y={Math.max(plotTop + 9, y(finite(row[item.key])) - 10)} textAnchor="middle">{compact(row[item.key])}</text></>}</g>)}
      </g>;
    })}
  </svg>;
}

function InvestorFlowChart({ rows, series }) {
  const [hover, setHover] = useState(null);
  const ordered = chronological(rows, "tradeDate");
  if (!ordered.length) return null;
  const width = 960;
  const height = 360;
  const plotLeft = 76;
  const plotRight = 940;
  const plotTop = 28;
  const plotBottom = 294;
  const domain = chartDomain(ordered.flatMap((row) => series.map((item) => row[item.key])));
  if (!domain) return null;
  const x = (index) => ordered.length === 1 ? (plotLeft + plotRight) / 2 : plotLeft + (plotRight - plotLeft) * index / (ordered.length - 1);
  const y = (value) => scaleValue(value, domain.maximum, domain.minimum, plotTop, plotBottom);
  const candidates = ordered.flatMap((row, pointIndex) => series.map((item) => ({ row, pointIndex, item, value: finite(row[item.key]) })).filter((item) => item.value !== null));
  const maximum = candidates.reduce((current, candidate) => !current || candidate.value > current.value ? candidate : current, null);
  const minimum = candidates.reduce((current, candidate) => !current || candidate.value < current.value ? candidate : current, null);
  const labelInterval = Math.max(1, Math.ceil(ordered.length / 8));
  const pointerMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const localX = (event.clientX - rect.left) * width / rect.width;
    const localY = (event.clientY - rect.top) * height / rect.height;
    if (localX < plotLeft || localX > plotRight || localY < plotTop || localY > plotBottom) return setHover(null);
    const index = Math.max(0, Math.min(ordered.length - 1, Math.round((localX - plotLeft) / (plotRight - plotLeft) * (ordered.length - 1))));
    setHover({ index, ...tooltipPosition(event, rect) });
  };
  const extrema = (candidate, css, label) => {
    if (!candidate) return null;
    const pointX = x(candidate.pointIndex);
    const pointY = y(candidate.value);
    const anchor = pointX > (plotLeft + plotRight) / 2 ? "end" : "start";
    return <g key={css}><circle className={`financial-svg-extrema-point ${css}`} cx={pointX} cy={pointY} r="5.5" /><text className={`financial-svg-extrema-label ${css}`} x={pointX + (anchor === "end" ? -9 : 9)} y={css === "maximum" ? Math.max(plotTop + 12, pointY - 10) : Math.min(plotBottom - 4, pointY + 17)} textAnchor={anchor}>{`${label} ${candidate.item.label} ${candidate.value > 0 ? "+" : ""}${compact(candidate.value)}주 · ${shortDate(candidate.row.tradeDate)}`}</text></g>;
  };
  return <><svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img" onPointerMove={pointerMove} onPointerLeave={() => setHover(null)}>
    {[0, 1, 2, 3, 4].map((index) => { const value = domain.maximum - (domain.maximum - domain.minimum) * index / 4; const gridY = y(value); return <g key={index}><line className={Math.abs(value) < (domain.maximum - domain.minimum) / 100 ? "financial-svg-zero" : "financial-svg-grid"} x1={plotLeft} x2={plotRight} y1={gridY} y2={gridY} /><text x={plotLeft - 10} y={gridY + 4} textAnchor="end">{compact(value)}</text></g>; })}
    {ordered.map((row, index) => (index % labelInterval === 0 || index === ordered.length - 1) && <text key={row.tradeDate} x={x(index)} y="326" textAnchor="middle">{shortDate(row.tradeDate)}</text>)}
    {series.map((item) => <g key={item.key}><polyline className={`financial-svg-line financial-svg-series--${item.className}`} points={ordered.map((row, index) => ({ index, value: finite(row[item.key]) })).filter((point) => point.value !== null).map((point) => `${x(point.index)},${y(point.value)}`).join(" ")} />{ordered.map((row, index) => finite(row[item.key]) !== null && <circle className={`financial-svg-series--${item.className}`} key={row.tradeDate} cx={x(index)} cy={y(finite(row[item.key]))} r="3.4"><title>{`${row.tradeDate} ${item.label} 순매수 ${signed(row[item.key], 0)}주`}</title></circle>)}</g>)}
    {extrema(maximum, "maximum", "최고")}{minimum !== maximum && extrema(minimum, "minimum", "최저")}
    {hover && <g aria-hidden="true"><line className="financial-svg-hover-line" x1={x(hover.index)} x2={x(hover.index)} y1={plotTop} y2={plotBottom} />{series.map((item) => finite(ordered[hover.index][item.key]) !== null && <circle className={`financial-svg-series--${item.className} financial-svg-focus-point`} key={item.key} cx={x(hover.index)} cy={y(finite(ordered[hover.index][item.key]))} r="6" />)}</g>}
    <rect className="financial-svg-hover-target" x={plotLeft} y={plotTop} width={plotRight - plotLeft} height={plotBottom - plotTop} />
  </svg>{hover && <div className="chart-tooltip financial-chart-tooltip visible" style={{ left: hover.left, top: hover.top }}><div className="chart-tooltip-title">{ordered[hover.index].tradeDate}</div>{series.map((item) => <div className="chart-tooltip-row" key={item.key}><span className="chart-tooltip-label">{item.label} 순매수</span><span className={`chart-tooltip-value ${item.className}`}>{signed(ordered[hover.index][item.key], 0)}주</span></div>)}</div>}</>;
}

function TrendChart({ rows, series, includeZero = false, height = 280 }) {
  const [hover, setHover] = useState(null);
  const ordered = chronological(rows, "tradeDate");
  if (!ordered.length) return null;
  const width = 460;
  const plotLeft = 58;
  const plotRight = 442;
  const plotTop = 25;
  const plotBottom = 218;
  const domain = chartDomain(ordered.flatMap((row) => series.map((item) => row[item.key])), includeZero);
  if (!domain) return null;
  const x = (index) => ordered.length === 1 ? (plotLeft + plotRight) / 2 : plotLeft + (plotRight - plotLeft) * index / (ordered.length - 1);
  const y = (value) => scaleValue(value, domain.maximum, domain.minimum, plotTop, plotBottom);
  const candidates = ordered.flatMap((row, pointIndex) => series.map((item) => ({ row, pointIndex, item, value: finite(row[item.key]) })).filter((item) => item.value !== null));
  const maximum = candidates.reduce((current, candidate) => !current || candidate.value > current.value ? candidate : current, null);
  const minimum = candidates.reduce((current, candidate) => !current || candidate.value < current.value ? candidate : current, null);
  const labelInterval = Math.max(1, Math.ceil(ordered.length / 5));
  const pointerMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const localX = (event.clientX - rect.left) * width / rect.width;
    const localY = (event.clientY - rect.top) * height / rect.height;
    if (localX < plotLeft || localX > plotRight || localY < plotTop || localY > plotBottom) return setHover(null);
    const index = Math.max(0, Math.min(ordered.length - 1, Math.round((localX - plotLeft) / (plotRight - plotLeft) * (ordered.length - 1))));
    setHover({ index, ...tooltipPosition(event, rect, 220, series.length > 1 ? 130 : 105) });
  };
  const extrema = (candidate, css, label) => candidate && <g key={css}><circle className={`financial-svg-extrema-point ${css}`} cx={x(candidate.pointIndex)} cy={y(candidate.value)} r="5.5" /><text className={`financial-svg-extrema-label ${css}`} x={x(candidate.pointIndex) + (x(candidate.pointIndex) > 250 ? -8 : 8)} y={css === "maximum" ? Math.max(plotTop + 11, y(candidate.value) - 10) : Math.min(plotBottom - 2, y(candidate.value) + 17)} textAnchor={x(candidate.pointIndex) > 250 ? "end" : "start"}>{`${label} ${series.length > 1 ? `${candidate.item.label} ` : ""}${compact(candidate.value)}${candidate.item.suffix || ""}`}</text></g>;
  return <><svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img" onPointerMove={pointerMove} onPointerLeave={() => setHover(null)}>
    {[0, 0.5, 1].map((ratio) => { const value = domain.maximum - (domain.maximum - domain.minimum) * ratio; return <g key={ratio}><line className="financial-svg-grid" x1={plotLeft} x2={plotRight} y1={y(value)} y2={y(value)} /><text x={plotLeft - 8} y={y(value) + 4} textAnchor="end">{compact(value)}</text></g>; })}
    {ordered.map((row, index) => (index % labelInterval === 0 || index === ordered.length - 1) && <text key={row.tradeDate} x={x(index)} y="249" textAnchor="middle">{shortDate(row.tradeDate)}</text>)}
    {series.map((item) => <g key={item.key}><polyline className={`financial-svg-line financial-svg-series--${item.className}`} points={ordered.filter((row) => finite(row[item.key]) !== null).map((row) => `${x(ordered.indexOf(row))},${y(finite(row[item.key]))}`).join(" ")} />{ordered.map((row, index) => finite(row[item.key]) !== null && <circle className={`financial-svg-series--${item.className}`} key={row.tradeDate} cx={x(index)} cy={y(finite(row[item.key]))} r="3.5" />)}</g>)}
    {extrema(maximum, "maximum", "최고")}{minimum !== maximum && extrema(minimum, "minimum", "최저")}
    {hover && <g><line className="financial-svg-hover-line" x1={x(hover.index)} x2={x(hover.index)} y1={plotTop} y2={plotBottom} />{series.map((item) => finite(ordered[hover.index][item.key]) !== null && <circle className={`financial-svg-series--${item.className} financial-svg-focus-point`} key={item.key} cx={x(hover.index)} cy={y(finite(ordered[hover.index][item.key]))} r="6" />)}</g>}
    <rect className="financial-svg-hover-target" x={plotLeft} y={plotTop} width={plotRight - plotLeft} height={plotBottom - plotTop} />
  </svg>{hover && <div className="chart-tooltip financial-chart-tooltip visible" style={{ left: hover.left, top: hover.top }}><div className="chart-tooltip-title">{ordered[hover.index].tradeDate}</div>{series.map((item) => <div className="chart-tooltip-row" key={item.key}><span className="chart-tooltip-label">{item.label}</span><span className={`chart-tooltip-value ${item.className}`}>{number(ordered[hover.index][item.key], item.digits || 0)}{item.suffix || ""}</span></div>)}</div>}</>;
}

function BalanceChart({ rows }) {
  if (!rows?.length) return null;
  const width = 960;
  const height = 300;
  const plotLeft = 50;
  const plotRight = 940;
  const plotTop = 25;
  const plotBottom = 240;
  const series = [{ key: "totalLiabilities", label: "총부채", className: "liabilities" }, { key: "totalEquity", label: "자기자본", className: "equity" }];
  const domain = chartDomain(rows.flatMap((row) => series.map((item) => row[item.key])));
  if (!domain) return null;
  const y = (value) => scaleValue(value, domain.maximum, domain.minimum, plotTop, plotBottom);
  const zeroY = y(0);
  const groupWidth = (plotRight - plotLeft) / rows.length;
  const barWidth = Math.min(64, groupWidth * 0.28);
  return <svg className="financial-svg-chart" viewBox={`0 0 ${width} ${height}`} role="img">
    <line className="financial-svg-grid" x1={plotLeft} x2={plotRight} y1={plotTop} y2={plotTop} /><line className="financial-svg-grid" x1={plotLeft} x2={plotRight} y1={plotBottom} y2={plotBottom} /><line className="financial-svg-zero" x1={plotLeft} x2={plotRight} y1={zeroY} y2={zeroY} />
    {rows.map((row, index) => { const centerX = plotLeft + groupWidth * index + groupWidth / 2; return <g key={row.label}>{series.map((item, seriesIndex) => { const value = finite(row[item.key]); if (value === null) return null; const valueY = y(value); const barX = centerX + (seriesIndex === 0 ? -barWidth - 4 : 4); return <g key={item.key}><rect className={`financial-svg-series--${item.className}`} x={barX} y={Math.min(valueY, zeroY)} width={barWidth} height={Math.max(1, Math.abs(valueY - zeroY))} rx="4"><title>{`${row.label} ${item.label} ${number(value)}억원`}</title></rect><text className="financial-svg-value" x={barX + barWidth / 2} y={value >= 0 ? Math.max(plotTop + 10, valueY - 7) : Math.min(plotBottom - 3, valueY + 14)} textAnchor="middle">{compact(value)}</text></g>; })}<text className="financial-svg-title" x={centerX} y="273" textAnchor="middle">{row.label}</text></g>; })}
  </svg>;
}

const filterRecentDays = (rows, days) => {
  const ordered = chronological(rows, "tradeDate");
  if (!ordered.length) return [];
  const latest = new Date(`${ordered[ordered.length - 1].tradeDate}T00:00:00`);
  const cutoff = new Date(latest);
  cutoff.setDate(cutoff.getDate() - days + 1);
  return ordered.filter((row) => new Date(`${row.tradeDate}T00:00:00`) >= cutoff).reverse();
};

function ChartCard({ title, description, unit, children, series = [] }) {
  return <article className="financial-chart-card"><div className="financial-chart-heading"><div><h3>{title}</h3><p>{description}</p></div><span className="financial-chart-unit">{unit}</span></div>{children}<div className="financial-chart-legend">{series.map((item) => <span key={item.label}><i className={item.className} />{item.label}</span>)}</div></article>;
}

function DataTable({ title, rows, columns, periodKey = "fiscalPeriod", onConcept, conceptCode, collapsible = false }) {
  if (!rows?.length) return null;
  const table = <div className="table-scroll"><table className="detail-data-table"><thead><tr><th>기간</th>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>{rows.map((row) => <tr key={row[periodKey]}><td>{periodKey === "fiscalPeriod" ? quarter(row[periodKey]) : row[periodKey]}</td>{columns.map((column) => <td className={column.signed ? direction(row[column.key]) : ""} key={column.key}>{column.signed ? signed(row[column.key], column.digits ?? 0) : number(row[column.key], column.digits ?? 0)}{column.suffix || ""}</td>)}</tr>)}</tbody></table></div>;
  if (collapsible) return <details className="financial-card investor-table-card"><summary><span>{title}</span><span className="investor-table-summary-meta">최근 {rows.length}거래일</span></summary>{table}</details>;
  return <section className="financial-card"><div className="detail-heading-with-concept"><h3>{title}</h3>{conceptCode && <ConceptButton code={conceptCode} title={title} onConcept={onConcept} compact />}</div>{table}</section>;
}

export function FinancialPanel({ detail, onConcept }) {
  const data = detail.domesticDetailInfo;
  if (!data?.supported) return <p className="detail-empty-state">국내 종목 재무정보를 제공하지 않습니다.</p>;
  const analysis = data.financialAnalysis;
  const incomeRows = chronological(data.incomeStatements, "fiscalPeriod").map((row) => ({ ...row, label: quarterLabel(row.fiscalPeriod) }));
  const ratioRows = chronological(data.financialRatios, "fiscalPeriod").map((row) => ({ ...row, label: row.fiscalPeriod.slice(2, 7) }));
  const balanceRows = chronological(data.balanceSheets, "fiscalPeriod").map((row) => ({ ...row, label: row.fiscalPeriod.slice(2, 7) }));
  const growthRows = analysis ? [{ label: "매출", yoy: analysis.revenue.yoyRate, qoq: analysis.revenue.qoqRate }, { label: "영업이익", yoy: analysis.operatingProfit.yoyRate, qoq: analysis.operatingProfit.qoqRate }, { label: "순이익", yoy: analysis.netIncome.yoyRate, qoq: analysis.netIncome.qoqRate }] : [];
  const annualizedRows = analysis ? [{ label: "매출", ttm: analysis.revenue.ttm, runRate: analysis.revenue.runRate }, { label: "영업이익", ttm: analysis.operatingProfit.ttm, runRate: analysis.operatingProfit.runRate }, { label: "순이익", ttm: analysis.netIncome.ttm, runRate: analysis.netIncome.runRate }] : [];
  const incomeSeries = [{ key: "revenue", label: "매출액", className: "revenue" }, { key: "operatingProfit", label: "영업이익", className: "operating" }, { key: "netIncome", label: "당기순이익", className: "net-income" }];
  return <div><div className="detail-section-header"><div><h2>재무 정보</h2><p>최근 4개 분기의 재무비율과 주요 재무제표를 비교합니다.</p></div><span className="detail-updated-at">갱신 {data.financialUpdatedAt?.replace("T", " ").slice(0, 16) || "-"}</span></div><div className="financial-card-stack">
    {analysis && <><div className="financial-analysis-notice"><div className="financial-analysis-concepts"><strong>재무 분석 기준</strong><ConceptButton code="FINANCIAL_ANALYSIS_METRICS" title="재무 분석 기준" onConcept={onConcept} compact /><span className="financial-analysis-concept-list">YoY · QoQ · TTM · 런레이트</span></div><p className="financial-analysis-description">YoY는 전년 같은 분기, QoQ는 직전 분기와 비교한 변화율입니다. TTM은 최근 실제 4개 분기의 합계이며, 런레이트는 최신 분기 실적이 유지된다고 가정한 단순 연환산 값입니다.</p></div><div className="financial-analysis-grid"><FinancialMetricCard title="매출액" metric={analysis.revenue} code="REVENUE" onConcept={onConcept} latestPeriod={analysis.latestPeriod} /><FinancialMetricCard title="영업이익" metric={analysis.operatingProfit} code="OPERATING_PROFIT" onConcept={onConcept} latestPeriod={analysis.latestPeriod} /><FinancialMetricCard title="당기순이익" metric={analysis.netIncome} code="NET_INCOME" onConcept={onConcept} latestPeriod={analysis.latestPeriod} /></div></>}
    <section className="financial-chart-dashboard" aria-label="최근 4개 분기 재무 추이 차트">
      <article className="financial-chart-card financial-chart-card--wide"><div className="financial-chart-heading"><div><h3>분기 실적 흐름</h3><p>각 지표는 독립된 축으로 표시해 매출 규모에 가려지지 않고 이익의 방향도 함께 볼 수 있습니다.</p></div><span className="financial-chart-unit">억원</span></div><IndependentBarPanels rows={incomeRows} series={incomeSeries} /><div className="financial-chart-legend">{incomeSeries.map((item) => <span key={item.key}><i className={item.className} />{item.label}</span>)}</div></article>
      <ChartCard title="성장 속도 비교" description="최신 분기의 전년 동기 대비와 직전 분기 대비 변화율입니다." unit="%" series={[{ label: "YoY", className: "yoy" }, { label: "QoQ", className: "qoq" }]}><GroupedBarChart rows={growthRows} series={[{ key: "yoy", label: "YoY", className: "yoy" }, { key: "qoq", label: "QoQ", className: "qoq" }]} /></ChartCard>
      <ChartCard title="TTM과 런레이트" description="각 지표 안에서 최근 12개월 실제 합계와 최신 분기 단순 연환산을 비교합니다." unit="억원" series={[{ label: "TTM", className: "ttm" }, { label: "런레이트", className: "run-rate" }]}><AnnualizedChart rows={annualizedRows} /></ChartCard>
      <article className="financial-chart-card financial-chart-card--wide"><div className="financial-chart-heading"><div><h3>수익성과 주당 지표</h3><p>ROE, EPS, BPS를 각각 독립된 축으로 표시합니다.</p></div><span className="financial-chart-unit">지표</span></div><IndependentLinePanels rows={ratioRows} series={[{ key: "roe", label: "ROE", unit: "%", className: "roe" }, { key: "eps", label: "EPS", unit: "원", className: "eps" }, { key: "bps", label: "BPS", unit: "원", className: "bps" }]} /><div className="financial-chart-legend"><span><i className="qoq" />ROE</span><span><i className="revenue" />EPS</span><span><i className="net-income" />BPS</span></div></article>
      <article className="financial-chart-card financial-chart-card--wide"><div className="financial-chart-heading"><div><h3>부채와 자기자본 구성</h3><p>분기별 총부채와 자기자본을 영점 기준 막대로 나란히 비교합니다.</p></div><span className="financial-chart-unit">억원</span></div><BalanceChart rows={balanceRows} /><div className="financial-chart-legend"><span><i className="liabilities" />총부채</span><span><i className="equity" />자기자본</span></div></article>
    </section>
    <DataTable title="재무비율" rows={data.financialRatios} conceptCode="FINANCIAL_RATIOS" onConcept={onConcept} columns={[{ key: "salesGrowthRate", label: "매출 성장률", suffix: "%", digits: 2 }, { key: "operatingProfitGrowthRate", label: "영업이익 성장률", suffix: "%", digits: 2 }, { key: "netIncomeGrowthRate", label: "순이익 성장률", suffix: "%", digits: 2 }, { key: "roe", label: "ROE", suffix: "%", digits: 2 }, { key: "eps", label: "EPS" }, { key: "bps", label: "BPS" }, { key: "reserveRate", label: "유보율", suffix: "%", digits: 2 }, { key: "debtRate", label: "부채비율", suffix: "%", digits: 2 }]} />
    <DataTable title="손익계산서 (억원)" rows={data.incomeStatements} conceptCode="INCOME_STATEMENT" onConcept={onConcept} columns={[{ key: "revenue", label: "매출액" }, { key: "operatingProfit", label: "영업이익" }, { key: "ordinaryProfit", label: "경상이익" }, { key: "netIncome", label: "당기순이익" }]} />
    <DataTable title="대차대조표 (억원)" rows={data.balanceSheets} conceptCode="BALANCE_SHEET" onConcept={onConcept} columns={[{ key: "currentAssets", label: "유동자산" }, { key: "totalAssets", label: "총자산" }, { key: "currentLiabilities", label: "유동부채" }, { key: "totalLiabilities", label: "총부채" }, { key: "capital", label: "자본금" }, { key: "retainedEarnings", label: "이익잉여금" }, { key: "totalEquity", label: "자기자본" }]} />
  </div></div>;
}

export function InvestorPanel({ detail, onConcept }) {
  const data = detail.domesticDetailInfo;
  const [period, setPeriod] = useState(31);
  const shortSales = useMemo(() => filterRecentDays(data?.shortSales, period), [data, period]);
  const loans = useMemo(() => filterRecentDays(data?.loanTransactions, period), [data, period]);
  const investorSeries = [{ key: "foreignNetQuantity", label: "외국인", className: "foreign" }, { key: "personalNetQuantity", label: "개인", className: "personal" }, { key: "institutionNetQuantity", label: "기관", className: "institution" }];
  if (!data?.supported) return <p className="detail-empty-state">국내 주식에 한해 KIS 투자자별 매매 수급을 제공합니다.</p>;
  return <div><div className="detail-section-header"><div><div className="detail-heading-with-concept"><h2>투자자별 매매 수급</h2><ConceptButton code="INVESTOR_TRADING_FLOW" title="투자자별 매매동향" onConcept={onConcept} compact /></div><p>외국인·개인·기관의 순매수 흐름을 최근 거래일 기준으로 비교합니다.</p></div><span className="detail-updated-at">갱신 {data.investorUpdatedAt?.replace("T", " ").slice(0, 16) || "-"}</span></div>
    <p className="investor-notice">순매수는 매수 수량에서 매도 수량을 뺀 값입니다. 양수는 순매수, 음수는 순매도를 뜻하며 국내 시장 관례에 맞춰 상승·양수는 빨간색, 하락·음수는 파란색으로 표시합니다.</p>
    <ChartCard title="투자자별 순매수 추이" description="최근 거래일의 외국인·개인·기관 순매수 수량입니다." unit="주" series={investorSeries}><InvestorFlowChart rows={data.investorTrades} series={investorSeries} /></ChartCard>
    <DataTable title="투자자별 일자 상세" rows={data.investorTrades} periodKey="tradeDate" collapsible columns={[{ key: "closePrice", label: "종가" }, { key: "foreignBuyQuantity", label: "외국인 매수" }, { key: "foreignSellQuantity", label: "외국인 매도" }, { key: "foreignNetQuantity", label: "외국인 순매수", signed: true }, { key: "personalBuyQuantity", label: "개인 매수" }, { key: "personalSellQuantity", label: "개인 매도" }, { key: "personalNetQuantity", label: "개인 순매수", signed: true }, { key: "institutionBuyQuantity", label: "기관 매수" }, { key: "institutionSellQuantity", label: "기관 매도" }, { key: "institutionNetQuantity", label: "기관 순매수", signed: true }]} />
    <section className="short-loan-section"><div className="short-loan-toolbar"><div><div className="detail-heading-with-concept"><h2>공매도·대차 현황</h2><ConceptButton code="SHORT_SELLING_AND_SECURITIES_LENDING" title="공매도·대차 현황" onConcept={onConcept} compact /></div><p>최근 3개월을 저장하고 기본 1개월 추이를 표시합니다.</p></div><div className="short-loan-periods" role="group" aria-label="공매도 대차 조회 기간"><button className={`short-loan-period-button${period === 31 ? " active" : ""}`} type="button" onClick={() => setPeriod(31)}>1개월</button><button className={`short-loan-period-button${period === 93 ? " active" : ""}`} type="button" onClick={() => setPeriod(93)}>3개월</button></div></div>
      <p className="investor-notice">공매도 거래비중은 해당 거래일의 실제 공매도 체결 흐름이고, 대차잔고는 빌린 뒤 아직 반환되지 않은 누적 수량입니다.</p>
      <div className="short-loan-chart-grid"><ChartCard title="공매도 거래비중" description="전체 거래량 중 공매도 수량이 차지한 비율입니다." unit="%"><TrendChart rows={shortSales} series={[{ key: "shortSaleVolumeRatio", label: "공매도 거래비중", className: "short-sale", suffix: "%", digits: 2 }]} /></ChartCard><ChartCard title="대차잔고" description="빌린 뒤 아직 반환되지 않은 주식 수량입니다." unit="주"><TrendChart rows={loans} series={[{ key: "loanBalanceQuantity", label: "대차잔고", className: "loan-balance", suffix: "주" }]} /></ChartCard></div>
      <div className="short-loan-chart-grid"><ChartCard title="공매도 거래수량" description="해당 거래일에 체결된 공매도 수량입니다." unit="주"><TrendChart rows={shortSales} series={[{ key: "shortSaleQuantity", label: "공매도 거래수량", className: "short-sale", suffix: "주" }]} /></ChartCard><ChartCard title="대차 체결·상환 수량" description="새로 빌린 수량과 반환한 수량을 비교합니다." unit="주" series={[{ label: "신규 대차", className: "loan-new" }, { label: "상환", className: "loan-redeemed" }]}><TrendChart rows={loans} includeZero series={[{ key: "newLoanQuantity", label: "신규 대차", className: "loan-new", suffix: "주" }, { key: "redeemedLoanQuantity", label: "상환", className: "loan-redeemed", suffix: "주" }]} /></ChartCard></div>
      <DataTable title="공매도 일자 상세" rows={shortSales} periodKey="tradeDate" collapsible columns={[{ key: "closePrice", label: "종가" }, { key: "shortSaleQuantity", label: "공매도 수량" }, { key: "shortSaleVolumeRatio", label: "거래량 비중", suffix: "%", digits: 2 }, { key: "shortSaleTradeAmount", label: "공매도 거래대금" }]} />
      <DataTable title="대차거래 일자 상세" rows={loans} periodKey="tradeDate" collapsible columns={[{ key: "closePrice", label: "종가" }, { key: "newLoanQuantity", label: "신규 대차" }, { key: "redeemedLoanQuantity", label: "상환" }, { key: "loanBalanceQuantity", label: "대차잔고" }]} />
    </section>
  </div>;
}
