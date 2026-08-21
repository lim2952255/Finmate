import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { searchStocks, toggleFavorite } from "../api/stockCatalog.js";
import Header from "../components/layout/Header.jsx";
import OverviewStatus from "../components/accounts/OverviewStatus.jsx";
import Pagination from "../components/stocks/Pagination.jsx";
import StockTable from "../components/stocks/StockTable.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

export default function StockSearchPage() {
  useDocumentTitle("종목 검색 | FinMate");
  const location = useLocation();
  const navigate = useNavigate();
  const query = new URLSearchParams(location.search);
  const [state, setState] = useState({ status: "loading", data: null, error: null, updatingId: null });
  const requestParams = {
    keyword: query.get("keyword") || "",
    searchType: query.get("searchType") || "STOCK",
    marketType: query.get("marketType") || "",
    page: query.get("page") || "0"
  };

  const load = useCallback((signal) => {
    setState((current) => ({ ...current, status: "loading", error: null }));
    searchStocks(requestParams, { signal })
      .then((data) => setState({ status: "success", data, error: null, updatingId: null }))
      .catch((error) => error.name !== "AbortError" && setState({ status: "error", data: null, error, updatingId: null }));
  // URL 검색문자열이 바뀔 때만 다시 조회한다.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const changeFavorite = async (stockId) => {
    setState((current) => ({ ...current, updatingId: stockId, error: null }));
    try {
      await toggleFavorite(stockId);
      setState((current) => ({
        ...current,
        updatingId: null,
        data: { ...current.data, stocks: current.data.stocks.map((stock) => stock.id === stockId ? { ...stock, favorite: !stock.favorite } : stock) }
      }));
    } catch (error) {
      setState((current) => ({ ...current, updatingId: null, error }));
    }
  };

  const submitSearch = (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    navigate(`/investments/stocks/search?${new URLSearchParams({
      searchType: form.get("searchType"),
      marketType: form.get("marketType"),
      keyword: form.get("keyword"),
      page: "0"
    })}`);
  };

  const createPageUrl = (page) => `/investments/stocks/search?${new URLSearchParams({ ...requestParams, page })}`;

  return (
    <div className="page"><Header /><main className="main"><section className="content">
      <div className="page-heading"><h1>종목 검색</h1><p>종목명, 종목코드 또는 업종으로 국내외 투자 대상을 빠르게 찾아보세요.</p></div>
      <OverviewStatus status={state.status} error={state.error} onRetry={() => load()} />
      {state.status === "success" && <>
        <section className="search-panel"><div className="search-panel-intro"><h2>검색</h2><p>조건을 조합해 원하는 종목을 빠르게 찾아보세요.</p></div>
          <form className="filter-form" onSubmit={submitSearch}>
            <label>검색 기준<select name="searchType" defaultValue={state.data.searchType}>{state.data.searchTypes.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
            <label>시장<select name="marketType" defaultValue={state.data.marketType || ""}><option value="">전체 시장</option>{state.data.marketTypes.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
            <label>검색어<input name="keyword" type="search" defaultValue={state.data.keyword || ""} placeholder={state.data.searchPlaceholder} required /></label>
            <button type="submit">검색</button>
          </form>
        </section>
        {state.data.keyword && <section><h2>검색 결과</h2><p>{state.data.page.totalElements}건</p>
          {state.data.stocks.length ? <StockTable stocks={state.data.stocks} onToggleFavorite={changeFavorite} updatingId={state.updatingId} /> : <p>검색 결과가 없습니다.</p>}
          <Pagination page={state.data.page} createUrl={createPageUrl} label="종목 검색 결과 페이지" />
        </section>}
        {state.error && <p className="overview-error">{state.error.message}</p>}
        <p className="page-links"><Link to="/investments/stocks/search">검색 초기화</Link><Link to="/investments">투자 홈</Link></p>
      </>}
    </section></main></div>
  );
}
