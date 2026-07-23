# FinMate Codex Policy

## Operating mode

- Codex handles ordinary requests directly. Do not start, route into, or require an OMX workflow unless the user explicitly invokes a dollar-prefixed skill such as `$analyze` or `$ultragoal`.
- Explicit OMX skills remain available under `.codex/skills/`; native agents under `.codex/agents/` may be used when delegation materially improves an in-scope task.
- Proceed autonomously with clear, local, reversible work. Ask before destructive or irreversible actions, production or external-system changes, credential use, adding dependencies, materially expanding scope, or changing financial contracts or state transitions.
- Preserve unrelated work. Keep diffs small, use existing patterns and utilities, and do not add dependencies unless the user explicitly requests them.

## Source of truth

- Read the relevant documentation before changing code, then verify claims against the current source and tests. Source code is the implementation source of truth when documentation disagrees.
- If behavior or architecture changes, update the relevant documentation in the same change. Do not silently preserve stale documentation.
- Use `rg` or `rg --files` first for repository discovery.

## Reuse-first implementation

Before creating logic or an abstraction:

1. Search for the same concept, method names, error messages, calculations, validators, parsers, and formatting rules.
2. Read the candidate implementation and its call sites; compare responsibility, inputs, outputs, boundaries, exceptions, scale, and rounding contract.
3. Reuse it when responsibility and contract match.
4. If responsibility is shared but the contract is incomplete, add regression coverage first, then extend the existing implementation without breaking callers.
5. Keep the implementation local when domain meaning, lifecycle, or reason to change differs.

Useful starting points include `RequiredValidator`, `NumericValidator`, `TradingAmountValidator`, `KisValueParser`, `DisplayFormatUtils`, and `CurrencyCode`. Do not extract code merely because it may be reused later; require actual duplication or a demonstrated shared policy. Do not introduce premature generic helpers, service layers, frameworks, or extension points.

## Documentation router

- Architecture, packages, and component boundaries: `docs/PROJECT_OVERVIEW.md`, `docs/ARCHITECTURE.md`
- Entities, relationships, and domain rules: `docs/DOMAIN_MODEL.md`
- Any monetary, quantity, balance, holding, ledger, lock, transaction, order, execution, cancellation, expiry, or settlement work: `docs/FINANCIAL_INVARIANTS.md` plus the applicable domain document
- Stock order and trading lifecycle: `docs/TRADING_FLOW.md`
- KIS REST, authentication, rate limits, retry, WebSocket, realtime subscription, and market-data boundaries: `docs/KIS_INTEGRATION.md`
- Local setup, configuration, Docker, builds, and tests: `docs/DEVELOPMENT_GUIDE.md`

## Financial invariants guard

- Never change financial state transitions, settlement formulas, transaction boundaries, or lock acquisition order without explicit user approval and evidence from `docs/FINANCIAL_INVARIANTS.md` and the relevant domain flow.
- Use `BigDecimal` for monetary and quantity calculations and preserve the documented scale and rounding policy.
- Preserve account and cash balances, available and locked cash, holding and locked quantities, ledger atomicity, and concurrency invariants.
- For order execution, cancellation, and expiry, account for concurrent events and add regression and concurrency tests appropriate to the affected invariant.

## Verification

Run the smallest relevant test first, then the broader checks justified by the change:

```sh
./gradlew test
./gradlew clean build
./gradlew bootJar
```

For policy or documentation-only changes, run targeted static checks for syntax, references, ignore behavior, and forbidden routing text. Report commands run, results, and any validation gap.
