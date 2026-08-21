import ConceptTextBlocks from "../learning/ConceptTextBlocks.jsx";

function DetailSection({ title, children, tone = "default", open = false }) {
  if (!children) return null;

  return (
    <details className={`concept-card-section${tone === "example" ? " concept-card-section--bakery" : tone === "caution" ? " concept-card-section--caution" : ""}`} open={open}>
      <summary>{title}</summary>
      <div className="concept-card-text">{children}</div>
    </details>
  );
}

function StockAnalysis({ analysis }) {
  if (!analysis) return null;

  return (
    <section className="concept-stock-analysis">
      <header className="concept-stock-analysis-header"><div><span className="concept-stock-analysis-eyebrow">이 종목에 적용해 보기</span><h3>{analysis.heading}</h3></div><p className="concept-stock-analysis-reference">{analysis.reference}</p></header>
      {analysis.available ? (
        <>
          <div className="concept-stock-analysis-metrics">
            {analysis.metrics.map((metric) => (
              <div className="concept-stock-analysis-metric" key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong></div>
            ))}
          </div>
          {analysis.formula && <p className="concept-stock-analysis-formula">{analysis.formula}</p>}
          {analysis.interpretation && <p className="concept-stock-analysis-interpretation">{analysis.interpretation}</p>}
          <p className="concept-stock-analysis-meta">{[analysis.updatedAt, analysis.source].filter(Boolean).join(" · ")}</p>
        </>
      ) : (
        <p className="concept-stock-analysis-unavailable">{analysis.unavailableReason}</p>
      )}
    </section>
  );
}

// 개념 API가 제공하는 그림, 실제 종목 수치, 상세 설명과 주의사항을 빠짐없이 카드로 구성한다.
export default function StockConceptCard({ concept }) {
  return (
    <article className="concept-card-body">
      <p className="concept-card-summary">{concept.summary}</p>

      {concept.visual && (
        <figure className="concept-card-visual">
          <img src={concept.visual.assetPath} alt={concept.visual.altText || ""} />
          {concept.visual.caption && <figcaption>{concept.visual.caption}</figcaption>}
        </figure>
      )}

      <StockAnalysis analysis={concept.stockAnalysis} />

      <DetailSection title="자세히 알아보기" open><ConceptTextBlocks text={concept.detailedExplanation} formulaClassName="concept-card-formula" labelClassName="concept-formula-label" expressionClassName="concept-formula-expression" /></DetailSection>
      <DetailSection title="빵집으로 쉽게 이해하기" tone="example"><ConceptTextBlocks text={concept.bakeryExample} formulaClassName="concept-card-formula" labelClassName="concept-formula-label" expressionClassName="concept-formula-expression" /></DetailSection>
      <DetailSection title="시장과 주가에는 어떤 영향을 주나요?"><ConceptTextBlocks text={concept.marketImpact} formulaClassName="concept-card-formula" labelClassName="concept-formula-label" expressionClassName="concept-formula-expression" /></DetailSection>
      <DetailSection title="주의해서 볼 점" tone="caution"><ConceptTextBlocks text={concept.caution} formulaClassName="concept-card-formula" labelClassName="concept-formula-label" expressionClassName="concept-formula-expression" /></DetailSection>
    </article>
  );
}
