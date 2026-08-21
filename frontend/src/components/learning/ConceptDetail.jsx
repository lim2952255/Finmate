import ConceptTextBlocks from "./ConceptTextBlocks.jsx";

function DetailSection({ title, text, className = "", open = true }) {
  if (!text?.trim()) {
    return null;
  }

  return (
    <details className={`learning-detail ${className}`.trim()} open={open}>
      <summary>{title}</summary>
      <div className="learning-detail-text">
        <ConceptTextBlocks text={text} formulaClassName="learning-detail-formula" labelClassName="learning-formula-label" expressionClassName="learning-formula-expression" />
      </div>
    </details>
  );
}

export default function ConceptDetail({ concept }) {
  return (
    <article>
      <p className="learning-dialog-summary">{concept.summary}</p>

      {concept.visual?.assetPath && (
        <figure className="learning-dialog-visual">
          <img src={concept.visual.assetPath} alt={concept.visual.altText || ""} />
          {concept.visual.caption && <figcaption>{concept.visual.caption}</figcaption>}
        </figure>
      )}

      <DetailSection title="자세히 알아보기" text={concept.detailedExplanation} />
      <DetailSection
        title="빵집으로 쉽게 이해하기"
        text={concept.bakeryExample}
        className="learning-detail--bakery"
      />
      <DetailSection
        title="시장과 연결해서 보기"
        text={concept.marketImpact}
        className="learning-detail--impact"
      />
      <DetailSection
        title="주의해서 볼 점"
        text={concept.caution}
        className="learning-detail--caution"
        open={false}
      />
    </article>
  );
}
