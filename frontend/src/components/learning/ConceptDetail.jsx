const FORMULA_CONTENT_PATTERN = /[=÷×→]|(?:핵심 흐름|핵심 관계|핵심 차이|핵심 해석|시장 영향|초보자 핵심|계산 예시|계산식|산식|공식|목표금액|필요한 증거금|추가로 준비할 금액|비용 전 손익|괴리율|원화 환산가치|명목금액|손익분기)|\d[\d,.]*(?:만|천|억|조)?\s*(?:원|주|포대|달러|%|배).*(?:차이|차익|빼면|나누|곱하|더하|손익|순이익|손실|수익|가치|금액|비중|남는|됩니다|입니다)/;
const FORMULA_LABEL_PATTERN = /^(공식|계산식|계산 예시|숫자 대입|비중 계산|손익 계산|결과|핵심 흐름|핵심 관계|핵심 차이|핵심 해석|시장 영향|초보자 핵심):\s*(.+)$/;

// 투자 개념의 상세 내용을 표시한다.
function TextBlocks({ text }) {
  const paragraphs = String(text || "")
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean);

  return paragraphs.map((paragraph, index) => {
    const formula = paragraph.match(FORMULA_LABEL_PATTERN);
    const formulaContent = FORMULA_CONTENT_PATTERN.test(paragraph);

    return (
      <p
        key={`${index}-${paragraph}`}
        className={formulaContent ? "learning-detail-formula" : undefined}
      >
        {formula ? (
          <>
            <span className="learning-formula-label">{formula[1]}</span>
            <span className="learning-formula-expression">{formula[2]}</span>
          </>
        ) : paragraph}
      </p>
    );
  });
}

function DetailSection({ title, text, className = "", open = true }) {
  if (!text?.trim()) {
    return null;
  }

  return (
    <details className={`learning-detail ${className}`.trim()} open={open}>
      <summary>{title}</summary>
      <div className="learning-detail-text">
        <TextBlocks text={text} />
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
