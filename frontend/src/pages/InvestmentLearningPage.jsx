import { useEffect, useMemo, useRef, useState } from "react";
import Header from "../components/layout/Header.jsx";
import LearningDrawer from "../components/learning/LearningDrawer.jsx";
import { getLearningConcept } from "../api/investmentLearning.js";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import useLearningCatalog from "../hooks/useLearningCatalog.js";
import "../styles/investment-learning.css";

// 투자학습의 전체 화면을 구성하는 jsx를 리턴하는 컴포넌트를 정의한다.

const ALL_CATEGORIES = "ALL";

// 투자학습 페이지의 간단한 문구들과 개념수를 렌더링하는 jsx를 리턴하는 컴포넌트
function LearningHero({ conceptCount }) {
  return (
    <div className="learning-hero">
      <div>
        <span className="eyebrow">FINMATE LEARNING</span>
        <h1>투자 개념을 연결해서 배우세요</h1>
        <p>
          계좌의 돈 흐름부터 포트폴리오, ETF, 시장 안전장치와 파생상품까지<br />
          헷갈리기 쉬운 개념을 그림과 예시로 차근차근 정리합니다.
        </p>
      </div>
      <div className="learning-progress" aria-label="학습 개념 수">
        {/* conceptCount가 null 또는 undefined이면 "-"을 출력하고, 아니라면 conceptCount를 출력한다.*/}
        <strong>{conceptCount ?? "–"}</strong>
        <span>개의 핵심 개념</span>
      </div>
    </div>
  );
}

// 카테고리 필터 버튼 목록을 만들고, 현재 선택된 카테고리를 강조하며, 버튼 클릭시 상태를 변경하는 이벤트를 설정한 컴포넌트
function CategoryFilters({ categories, activeCategory, onChange }) {
  // 상위 컴포넌트로부터 주입받은 props들을 객체구조분해를 통해서 각각의 변수들로 바로 받는다.
  const filters = [
    { code: ALL_CATEGORIES, displayName: "전체" },
    ...categories // spread 문법으로서 categories 배열내의 데이터를 그대로 복제하는 역할을 수행한다.
    // 따라서 filters는 categories 배열내의 데이터 복사 + ALL_CATEGORIES라는 배열항목을 추가한 새로운 배열이다.
  ];

  return (
    <div className="learning-filters" aria-label="개념 카테고리">
      {/* filters 배열내의 데이터를 순차적으로 순회하면서 활성화된 카테고리를 selected 상수에 등록한다.*/}
      {filters.map((category) => {
        const selected = category.code === activeCategory;

        return (
          // 각 filters 배열들을 순회하면서 각 카테고리 항목마다 버튼을 생성하고, 활성화되어 있는 카테고리에는 강조 표시를 한다.
          // 이후 특정 카테고리 버튼을 클릭하게 되면, onChange 이벤트가 실행되면서 activeCategory의 상태가 변경되며, react가 이를 감지햐여 컴포넌트를 재실행하게 된다.
          <button
            key={category.code}
            type="button"
            className={`learning-filter${selected ? " is-active" : ""}`}
            aria-pressed={selected}
            onClick={() => onChange(category.code)}
          >
            {category.displayName}
          </button>
        );
      })}
    </div>
  );
}

// 각각 개념 카드 UI를 정의하는 jsx를 리턴하는 컴포넌트
function ConceptCard({ concept, categoryName, onOpen }) {
  return (
    // 특정 개념에 대한 개념카드를 생성하고, 해당 개념카드 클릭 시 onOpen으로 전달받은 함수를 실행한다.
    // 부모가 onOpen으로 전달한 openConcept는 상세 정보를 Spring 서버에 요청하고 상세 패널을 여는 역할을 수행한다.
    <button
      type="button"
      className="learning-card"
      aria-haspopup="dialog"
      aria-controls="investment-learning-dialog"
      onClick={() => onOpen(concept)}
    >
      <span className="learning-card-category">{categoryName}</span>
      <strong>{concept.title}</strong>
      <span className="learning-card-summary">{concept.summary}</span>
      <span className="learning-card-action">
        개념 익히기 <span aria-hidden="true">→</span>
      </span>
    </button>
  );
}

// 각 카탈로그 조회 상태에 따라 적절한 jsx를 생성하는 컴포넌트
function CatalogStatus({ status, error, onRetry }) {
  if (status === "loading") {
    return <p className="learning-empty" role="status">투자 개념을 불러오는 중입니다.</p>;
  }

  if (status === "error") {
    return (
      <div className="learning-catalog-error" role="alert">
        <p>{error?.message || "투자 개념을 불러오지 못했습니다."}</p>
        {/* 오류 발생시 재시도 이벤트를 등록한다.*/}
        <button type="button" onClick={onRetry}>다시 불러오기</button>
      </div>
    );
  }

  return null;
}

// 투자학습 페이지의 화면을 정의하는 jsx를 리턴하는 컴포넌트
export default function InvestmentLearningPage() {
  // 페이지 제목을 설정하는 커스텀 훅
  useDocumentTitle("투자 학습 | FinMate");

  const { status, catalog, error, retry } = useLearningCatalog(); // 투자학습 페이지에서 사용할 카테고리 목록과 개념 목록을 서버에서 조회하여 관리하는 커스텀 훅이다.
  const [activeCategory, setActiveCategory] = useState(ALL_CATEGORIES); // useState를 활용하여 activeCategory 상태를 관리하며, activeCategory가 변경되면 컴포넌트를 재실행하여 DOM을 update한다.
  const [query, setQuery] = useState(""); // useState를 활용하여 query 상태를 관리하며, query가 변경되면 컴포넌트를 재실행하여 DOM을 update한다.
  const [drawer, setDrawer] = useState({ // useState를 활용하여 현재 open된 개념카드 상태를 관리하며, 특정 개념카드가 open되거나 close될때마다 컴포넌트를 재실행하여 DOM을 update한다.
    open: false,
    title: "개념",
    status: "idle",
    concept: null
  });

  // useRef는 렌더링 사이에 값을 기억해두되, 값이 바뀌어도 컴포넌트를 다시 실행하지 않는다.
  // 화면에 표시할 상태라기보다 요청 객체나 DOM처럼 렌더링과 무관한 값을 보관할 때 사용한다.
  const conceptRequestRef = useRef(null);  // 현재 진행중인 상세개념 요청의 AbortController를 저장한다.

  // 투자학습페이지에서 벗어날때, conceptRequestRef의 상태를 abort로 전환함으로서 요청을 중단하는 cleanup함수 설정
  useEffect(() => () => conceptRequestRef.current?.abort(), []);

  // useMemo는 [의존형 변수]가 변경될 경우에만 다시 계산하고, 아니면 이전 계산 결과를 재사용한다.
  // 즉 catalog의 값이 변경되는 경우에만 Map을 다시 계산해서 생성한다.
  // catalog 목록을 사용하기 편한 Map으로 변환하는 작업을 등록한다.
  const categoryNames = useMemo(() => new Map(
    (catalog?.categories || []).map((category) => [category.code, category.displayName])
  ), [catalog]);

  // 현재 화면에 실제로 보여줄 개념목록을 계산한다.
  const visibleConcepts = useMemo(() => {
    // 검색어를 정규화한다.
    const normalizedQuery = query.trim().toLocaleLowerCase("ko-KR");

    // catalog내의 개념들을 순차적으로 순회하면서 화면에 표시할 개념목록만 남기고 필터링하는 방식이다.
    // 만약 현재 activeCategory가 ALL_CATEGORIES로 되어있거나, 현재 개념의 Category가 activeCategory와 일치한다면, 해당 개념을 화면에 표시한다.
    return (catalog?.concepts || []).filter((concept) => {
      const categoryMatches = activeCategory === ALL_CATEGORIES
        || concept.category === activeCategory;
      // 개념의 제목과 요약을 합쳐서 검색 대상 문자로 변환한다. 즉 제목 + 요약안에 입력 쿼리가 포함되는지 여부로 검색을 수행한다.
      const searchableText = `${concept.title} ${concept.summary}`
        .toLocaleLowerCase("ko-KR");

      return categoryMatches
        && (!normalizedQuery || searchableText.includes(normalizedQuery));
    });
  }, [activeCategory, catalog, query]); // 개념목록을 계산하는 useMemo는 activeCategory, catalog, query중 하나라도 달라지면 다시 계산하게 된다.

  // 사용자가 개념카드를 클릭했을때 실행되는 메서드 정의
  const openConcept = (conceptSummary) => {
    conceptRequestRef.current?.abort(); // 이전 상세 요청이 진행 중이라면 취소한다.
    const controller = new AbortController();
    conceptRequestRef.current = controller;

    // drawer를 loading 상태로 변경한다. 이때 drawer의 상태가 변경되었기 때문에, react가 이를 감지하고 컴포넌트를 재실행한다.
    setDrawer({
      open: true,
      title: conceptSummary.title,
      status: "loading",
      concept: null
    });

    // Spring 서버에 상세개념 API를 호출하여 상세개념 데이터를 받는다.
    getLearningConcept(conceptSummary.conceptCode, { signal: controller.signal })
      // 데이터를 받는데 성공할 경우, Drawer의 상태를 update한다 -> 이때 react가 컴포넌트를 재실행하게 된다.
      .then((concept) => {
        if (!controller.signal.aborted) {
          setDrawer({
            open: true,
            title: concept.title,
            status: "success",
            concept
          });
        }
      })
      // 예외 처리
      .catch((requestError) => {
        if (requestError.name !== "AbortError") {
          setDrawer({
            open: true,
            title: conceptSummary.title,
            status: "error",
            concept: null
          });
        }
      });
  };

  // 개념 상세페이지에서 벗어나는 경우, AbortController의 상태를 abort로 전환하고, 개념카드의 상태를 false로 변경한다.
  const closeDrawer = () => {
    conceptRequestRef.current?.abort();
    setDrawer((current) => ({ ...current, open: false }));
  };

  return (
    <div className="page">
      {/* Header 컴포넌트 호출 */}
      <Header />
      <main className="main learning-main">
        <section className="content learning-content">
          {/* LearningHero 컴포넌트를 호출하여 간단한 문구들을 추가한다.*/}
          <LearningHero conceptCount={catalog?.concepts.length} />

          <section className="learning-explorer" aria-labelledby="learning-explorer-title">
            <div className="learning-toolbar">
              <div>
                <h2 id="learning-explorer-title">개념 둘러보기</h2>
                <p>관심 있는 주제를 선택하거나 검색한 뒤 카드를 열어보세요.</p>
              </div>
              <label className="learning-search">
                <span className="sr-only">투자 개념 검색</span>
                <span aria-hidden="true">⌕</span>
                {/* 검색창에 이벤트를 설정해서, query가 변경되도록 설정하고, 쿼리가 변경되면 react가 컴포넌트를 재실행하게 된다.*/}
                <input
                  type="search"
                  placeholder="예: 미수, ETF, 헤지"
                  autoComplete="off"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
              </label>
            </div>

            {status === "success" && (
              <>
                {/* 카테고리 필터링을 수행하는 CategoryFilters 컴포넌트를 호출하며, ActiveCategory의 상태를 변경할 수 있는 setActiveCategory를 onChange로 전달한다.*/}
                <CategoryFilters
                  categories={catalog.categories}
                  activeCategory={activeCategory}
                  onChange={setActiveCategory}
                />
                <div className="learning-card-grid">
                  {/* 현재 식별가능한 개념목록들을 순차적으로 조회하면서 개념카드를 생성하고, 각 개념카드를 클릭하면 개념상세화면이 열리는 ConceptCard 컴포넌트를 호출한다.*/}
                  {visibleConcepts.map((concept) => (
                    <ConceptCard
                      key={concept.conceptCode}
                      concept={concept}
                      categoryName={categoryNames.get(concept.category) || concept.category}
                      onOpen={openConcept}
                    />
                  ))}
                </div>
                {visibleConcepts.length === 0 && (
                  <p className="learning-empty">검색 조건에 맞는 개념이 없습니다.</p>
                )}
              </>
            )}
            {/*카탈로그 조회 상태를 화면에 표시하는 컴포넌트*/}
            {/* useLearningCatalog 커스텀훅에서 제공해준 retry 함수를 onRetry에 등록하여 카탈로그를 다시 요청하는 로직을 등록한다.*/}
            <CatalogStatus status={status} error={error} onRetry={retry} />
          </section>
        </section>
      </main>

      {/* 사용자가 특정 개념카드를 열었을 실제 상세 개념창을 그리는 컴포넌트*/}
      <LearningDrawer drawer={drawer} onRequestClose={closeDrawer} />
    </div>
  );
}
