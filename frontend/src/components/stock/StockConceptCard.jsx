function DetailSection({ title, children, tone = "default", open = false }) {
  if (!children) return null;

  return (
    <details className={`stock-concept-detail ${tone}`} open={open}>
      <summary>{title}</summary>
      <div>{children}</div>
    </details>
  );
}

function StockAnalysis({ analysis }) {
  if (!analysis) return null;

  return (
    <section className="stock-analysis-card">
      <span className="stock-analysis-eyebrow">이 종목의 실제 데이터로 보기</span>
      <h3>{analysis.heading}</h3>
      {analysis.available ? (
        <>
          <div className="stock-analysis-metrics">
            {analysis.metrics.map((metric) => (
              <div key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong></div>
            ))}
          </div>
          {analysis.formula && <p className="stock-analysis-formula"><b>계산식</b>{analysis.formula}</p>}
          {analysis.interpretation && <p className="stock-analysis-interpretation">{analysis.interpretation}</p>}
          <small>{[analysis.reference, analysis.updatedAt, analysis.source].filter(Boolean).join(" · ")}</small>
        </>
      ) : (
        <p className="stock-analysis-unavailable">{analysis.unavailableReason}</p>
      )}
    </section>
  );
}

// 개념 API가 제공하는 그림, 실제 종목 수치, 상세 설명과 주의사항을 빠짐없이 카드로 구성한다.
export default function StockConceptCard({ concept }) {
  return (
    <article className="stock-concept-card">
      <header>
        <span>INVESTMENT CONCEPT</span>
        <h2>{concept.title}</h2>
        <p>{concept.summary}</p>
      </header>

      {concept.visual && (
        <figure className="stock-concept-visual">
          <img src={concept.visual.assetPath} alt={concept.visual.altText || ""} />
          {concept.visual.caption && <figcaption>{concept.visual.caption}</figcaption>}
        </figure>
      )}

      <StockAnalysis analysis={concept.stockAnalysis} />

      <div className="stock-concept-details">
        <DetailSection title="개념 자세히 이해하기" open><p>{concept.detailedExplanation}</p></DetailSection>
        <DetailSection title="빵집 예시로 쉽게 보기" tone="example"><p>{concept.bakeryExample}</p></DetailSection>
        <DetailSection title="시장과 주가에는 어떤 영향을 주나요?" tone="impact"><p>{concept.marketImpact}</p></DetailSection>
        <DetailSection title="투자할 때 주의할 점" tone="caution"><p>{concept.caution}</p></DetailSection>
      </div>
    </article>
  );
}
