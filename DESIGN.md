# Design

## Source of truth

- Status: Active
- Last refreshed: 2026-08-10
- Primary product surfaces: 계좌, 투자, 포트폴리오, 투자 학습, 종목 상세, 금융 개념 카드, 거래·환전 내역
- Evidence reviewed: `src/main/resources/static/css/common.css`, Thymeleaf 화면 템플릿, 사용자가 제공한 데스크톱 화면 캡처

## Brand

- Personality: 신뢰할 수 있고 차분하지만 초보자에게 친절한 금융 학습·투자 서비스
- Trust signals: 명확한 기준시각, 데이터 출처, 정확한 단위, 일관된 손익 색상, 충분한 주의 문구
- Avoid: 빈 공간을 채우기 위한 과도한 높이, 장식적인 대형 패널, 셀마다 강한 테두리, 설명 없는 금융 수치

## Product goals

- Goals: 실제 종목 데이터를 이해하고 금융 개념을 함께 학습하며 계좌·주문·자산 상태를 빠르게 확인하게 한다.
- Non-goals: 전문 HTS 수준의 초고밀도 트레이딩 UI, 현재 단계의 별도 모바일 디자인
- Success signals: 주요 값과 다음 행동을 빠르게 찾고, 개념 카드를 통해 지표의 의미와 관계를 이해한다.

## Personas and jobs

- Primary personas: 주식과 금융 도메인을 학습하는 초보 투자자, 자신의 금융 데이터를 관리하려는 사용자
- User jobs: 종목 분석, 개념 학습, 계좌·예수금 확인, 주문, 이체, 환전, 포트폴리오 손익 확인
- Key contexts of use: 데스크톱 웹에서 차트와 표를 함께 비교하거나 개념 카드를 펼쳐 학습하는 상황

## Information architecture

- Primary navigation: 계좌, 투자, 포트폴리오, 투자 학습, 마이페이지
- Core routes/screens: 계좌 홈·목록, 투자 홈, 종목 검색·상세, 주문, 포트폴리오, 투자 학습, 이체·환전·거래내역
- Content hierarchy: 현재 맥락과 핵심 수치 → 주요 행동 → 차트·레코드 → 개념 학습 → 원천·보조 데이터

## Design principles

- Principle 1: 핵심 사용 목적을 먼저 보여주고 원천 데이터와 긴 설명은 점진적으로 펼친다.
- Principle 2: 여백은 의미를 구분하는 데 사용하며 데이터가 적다고 화면을 억지로 채우지 않는다.
- Principle 3: 금액과 상태는 한눈에 비교할 수 있도록 관련 값을 묶고 숫자를 정렬한다.
- Tradeoffs: 금융 데이터의 정확성과 추적 가능성을 유지하면서 원천 필드가 학습 콘텐츠보다 앞서지 않도록 한다.

## Visual language

- Color: FinMate 블루를 주요 행동에, 청록을 보조 강조에 사용한다. 손익 색상은 기존 양수·음수 규칙을 유지한다.
- Typography: 기존 글꼴 스택을 사용한다. 제목은 강하게, 본문은 13–15px와 1.45–1.65 줄높이를 사용한다.
- Spacing/layout rhythm: 기본 8px 계열 리듬, 카드 간 12–20px, 표 셀 세로 10–14px를 권장한다.
- Shape/radius/elevation: 중간 크기 라운드와 얕은 그림자를 사용한다. pill은 상태·시장·통화·필터에 제한한다.
- Motion: 상태 이해를 돕는 짧은 전환만 사용하고 reduced-motion을 존중한다.
- Imagery/iconography: 장식보다 의미 전달용 아이콘과 교육용 SVG를 우선한다.

## Components

- Existing components to reuse: `page-heading`, `eyebrow`, `records-table`, `table-scroll`, `badge`, `status-chip`, `action-row`, `empty-state`
- New/changed components: 제한 높이 채팅, 금액·잔고 flow 셀, 접을 수 있는 보조 정보 패널, 주·보조 통화를 전환하는 잔액 요약, 카테고리형 학습 카드 그리드, 크기 조절 가능한 학습 사이드패널
- Variants and states: 기본·hover·focus·selected·disabled·empty·error·loading
- Token/component ownership: 공통 토큰과 범용 컴포넌트는 `common.css`, 화면 전용 레이아웃은 각 Thymeleaf 템플릿

## Accessibility

- Target standard: WCAG 2.1 AA를 지향한다.
- Keyboard/focus behavior: 모든 링크·버튼·요약 패널은 키보드로 접근하고 명확한 focus 표시를 제공한다.
- Contrast/readability: 옅은 배경 위 텍스트 대비를 유지하고 수치에 tabular numerals를 적용한다.
- Screen-reader semantics: 의미에 맞는 heading, table, form, label, button, details/summary를 사용한다.
- Reduced motion and sensory considerations: 색상만으로 손익이나 상태를 전달하지 않고 모션 축소 설정을 존중한다.

## Responsive behavior

- Supported breakpoints/devices: 현재 제품 목표는 데스크톱이며 기존 900px·720px 규칙은 유지한다.
- Layout adaptations: 좁은 화면에서는 다열 그리드를 단일 열로 바꾸고 표는 가로 스크롤을 허용한다.
- Touch/hover differences: 핵심 정보는 hover 없이도 읽을 수 있어야 하며 hover는 보조 피드백으로만 사용한다.

## Interaction states

- Loading: 레이아웃을 유지하는 짧은 상태 문구나 skeleton을 사용한다.
- Empty: 과도한 높이 없이 이유와 다음 행동을 함께 제공한다.
- Error: 발생 위치 가까이에 원인과 복구 행동을 표시한다.
- Success: 처리 결과와 변경된 금액·상태를 명확하게 확인시킨다.
- Disabled: 비활성 이유를 문구나 title로 제공한다.
- Offline/slow network, if applicable: 마지막 정상 데이터와 기준시각을 유지하고 수신 상태를 표시한다.

## Content voice

- Tone: 친절하고 직접적이며 과장하지 않는다.
- Terminology: 금융 용어는 정확하게 사용하되 `요약 → 핵심 흐름·수식 → 쉬운 원리 → 주의점` 순서로 설명한다. 종목 상세 개념은 실제 종목 적용 예시를, 독립형 투자 학습 개념은 일관된 빵집 숫자 예시를 제공한다.
- Microcopy rules: 버튼은 행동형으로, 빈 상태는 이유와 다음 단계를, 데이터에는 단위와 기준시각을 표시한다.
- Learning-card rules: 자세한 설명은 흰색, 빵집 예시는 옅은 노랑, 주의사항은 옅은 빨강을 사용하며 긴 문장은 문단으로 분리하고 핵심 관계·수식은 별도 박스로 강조한다.

## Implementation constraints

- Framework/styling system: Spring MVC, Thymeleaf, 공통 CSS, 화면별 CSS, vanilla JavaScript
- Design-token constraints: 기존 `--color-*`, `--radius*`, `--shadow*` 토큰을 우선 재사용한다.
- Performance constraints: 단순 레이아웃을 위해 새 프론트엔드 의존성을 추가하지 않는다.
- Compatibility constraints: 데스크톱 최신 브라우저를 기준으로 하되 기존 반응형 동작을 깨뜨리지 않는다.
- Test/screenshot expectations: 템플릿 변경 후 Gradle 리소스 처리와 diff 정적 검사를 수행하고, 서버 실행 시 주요 화면을 데스크톱 폭에서 확인한다.

## Open questions

- [ ] React 전환 시 현 디자인 토큰과 개념 카드 상호작용을 어떤 컴포넌트 경계로 옮길지 결정한다.
- [ ] 실제 사용자 데이터가 많은 경우 표 페이지네이션과 열 표시 설정이 필요한지 관찰한다.
