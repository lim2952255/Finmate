# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-07-29
- Primary product surfaces: 홈, 로그인·회원가입, 계좌, 거래내역, 투자, 포트폴리오, 주문, 종목·시장 데이터
- Evidence reviewed: `docs/PROJECT_OVERVIEW.md`, `docs/ARCHITECTURE.md`, `src/main/resources/static/css/common.css`, Thymeleaf templates, 2026-07-29 desktop screenshots 6장

## Brand
- Personality: 신뢰할 수 있고 차분하지만 데이터가 생동감 있게 읽히는 개인 금융 서비스
- Trust signals: 일관된 숫자 정렬, 명확한 계좌·통화 문맥, 절제된 그림자, 안정적인 네이비와 블루
- Avoid: 화면 대부분을 비워 두는 구성, 모든 상태를 같은 파란색으로 표현하기, 과도한 그라데이션, 장식 때문에 금융 수치가 묻히는 화면

## Product goals
- Goals: 계좌와 투자 현황을 빠르게 파악하고, 다음 행동으로 자연스럽게 이동하며, 수익·손실과 입금·출금을 즉시 구분한다.
- Non-goals: 백엔드 계약 변경, 금융 계산의 클라이언트 이전, 별도 디자인 프레임워크 도입
- Success signals: 핵심 정보가 첫 화면에 보이고, 포트폴리오·거래내역 표를 가로 스크롤하더라도 행과 상태를 쉽게 추적하며, 주요 링크가 올바른 경로로 이동한다.

## Personas and jobs
- Primary personas: 모의투자로 금융 흐름을 학습하고 여러 계좌와 종목을 함께 관리하는 개인 사용자
- User jobs: 잔액 확인, 거래 흐름 추적, 보유 종목 수익률 확인, 주문·이체·환전으로 이동
- Key contexts of use: 데스크톱 중심의 정보 밀도 높은 조회, 모바일의 빠른 현황 확인과 단일 열 탐색

## Information architecture
- Primary navigation: 홈 로고, 계좌 관리, 투자, 포트폴리오, 인증·사용자 메뉴
- Core routes/screens: `/home`, `/login`, `/accounts`, `/accounts/transactions`, `/investments`, `/investments/portfolio`, `/investments/orders`
- Content hierarchy: 페이지 설명 → 핵심 요약 → 필터·선택 → 상세 표 → 보조 이동

## Design principles
- 한 화면 한 초점: 첫 화면에서 제목과 핵심 지표, 다음 행동이 함께 보여야 한다.
- 의미가 있는 색: 한국 주식 차트 관례에 맞춰 상승·플러스·수익·입금은 레드, 하락·마이너스·손실·출금은 블루, 경고·대기는 앰버를 사용한다.
- 밀도와 호흡의 균형: 큰 빈 공간은 줄이고 카드 내부에는 8px 기반 간격을 유지한다.
- Tradeoffs: 화려한 장식보다 숫자의 가독성과 긴 테이블의 탐색성을 우선한다.

## Visual language
- Color: 딥 네이비 텍스트, 코발트 블루 브랜드, 레드 상승·플러스, 블루 하락·마이너스, 옅은 블루그레이 배경
- Typography: 시스템 글꼴과 `Noto Sans KR` 폴백, 숫자는 굵기와 tabular number 정렬로 강조
- Spacing/layout rhythm: 4/8/12/16/24/32px, 데스크톱 본문 최대 1320px
- Shape/radius/elevation: 12~22px 라운드, 얕은 테두리와 단계가 다른 그림자
- Motion: hover와 focus에 160~200ms, `prefers-reduced-motion` 존중
- Imagery/iconography: 단순한 선형 아이콘과 공급자별 인지 가능한 소셜 로그인 마크

## Components
- Existing components to reuse: `.header`, `.content`, `.metric-card`, `.records-table`, `.badge`, `.menu-item`, `.table-action-link`
- New/changed components: 브랜드 로고 마크, 홈 히어로, 전체 클릭 메뉴 카드, 인증 분할 패널, 소셜 로그인 버튼, 알림 배너
- Variants and states: primary/secondary action, positive/negative/neutral metric, hover/focus/disabled
- Token/component ownership: 공통 토큰과 범용 컴포넌트는 `common.css`, 포트폴리오 전용 실시간 상태는 `portfolio.html`

## Accessibility
- Target standard: WCAG 2.1 AA 수준의 대비와 키보드 사용성
- Keyboard/focus behavior: 모든 링크·폼 요소에 명확한 `:focus-visible`, 카드 링크 전체 클릭 영역 제공
- Contrast/readability: 방향색은 `+`·`-` 부호와 함께 사용하고, 평가손익·수익률의 라벨과 값에 동일한 방향색을 적용한다.
- Screen-reader semantics: 장식 아이콘은 `aria-hidden`, 페이지·내비게이션 의미 구조 유지
- Reduced motion and sensory considerations: 감소된 모션 설정에서 전환과 이동 효과 제거

## Responsive behavior
- Supported breakpoints/devices: 360px 이상 모바일, 태블릿, 1280px 이상 데스크톱
- Layout adaptations: 홈·로그인·요약 카드는 단일 열로 전환하고, 긴 금융 표는 가로 스크롤과 sticky 헤더 사용
- Touch/hover differences: 터치 목표 최소 42px, hover가 없어도 상태와 동작을 식별 가능

## Interaction states
- Loading: “수신 대기”와 데이터 출처를 별도 보조 텍스트로 표시
- Empty: 원인과 가능한 다음 행동 링크를 함께 제공
- Error: 연한 레드 배경의 알림 배너와 구체적인 메시지
- Success: 연한 그린 배경의 알림 배너
- Disabled: 채도를 낮추고 커서와 대비로 비활성 상태 표현
- Offline/slow network, if applicable: 실시간 데이터가 없을 때 최근 종가 등 서버가 제공하는 fallback 출처 표시

## Content voice
- Tone: 간결하고 친절하며 금융 용어는 정확하게 사용
- Terminology: “증권계좌”, “예수금”, “평가손익”, “수익률”을 일관되게 사용
- Microcopy rules: 버튼은 행동형, 빈 상태는 문제 설명 뒤 가능한 다음 행동을 제시

## Implementation constraints
- Framework/styling system: Spring MVC + Thymeleaf + 기존 단일 `common.css`
- Design-token constraints: 새 의존성 없이 CSS custom properties 확장
- Performance constraints: 외부 폰트·아이콘 런타임 요청 없이 로컬 HTML/CSS/SVG 사용
- Compatibility constraints: 현재 세션 로그인, OAuth 경로, Thymeleaf 바인딩과 WebSocket DOM 계약 유지
- Test/screenshot expectations: MVC 테스트, 템플릿 경로 정적 검사, 데스크톱·모바일 주요 화면 육안 검증

## Open questions
- [ ] React 전환 시 이 문서의 토큰과 컴포넌트 상태를 프런트엔드 디자인 토큰으로 이관
