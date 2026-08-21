import { Link } from "react-router-dom";

// 현재 검색조건을 유지하면서 페이지 번호만 바꾸는 공통 페이지네이션 컴포넌트
export default function Pagination({ page, createUrl, label }) {
  if (!page || page.totalPages <= 1) return null;

  return (
    <nav className="pagination-nav" aria-label={label}>
      <p>{page.number + 1} / {page.totalPages} 페이지</p>
      {!page.first && <Link to={createUrl(0)}>처음</Link>}
      {!page.first && <Link to={createUrl(page.number - 1)}>이전</Link>}
      {page.pageNumbers.map((number) => (
        number === page.number
          ? <strong key={number}>{number + 1}</strong>
          : <Link key={number} to={createUrl(number)}>{number + 1}</Link>
      ))}
      {!page.last && <Link to={createUrl(page.number + 1)}>다음</Link>}
      {!page.last && <Link to={createUrl(page.totalPages - 1)}>마지막</Link>}
    </nav>
  );
}
