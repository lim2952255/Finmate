import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { getWatchlist, toggleFavorite } from "../api/stockCatalog.js";
import Header from "../components/layout/Header.jsx";
import OverviewStatus from "../components/accounts/OverviewStatus.jsx";
import Pagination from "../components/stocks/Pagination.jsx";
import StockTable from "../components/stocks/StockTable.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";

export default function WatchlistPage() {
  useDocumentTitle("관심 종목 | FinMate");
  const location = useLocation();
  const pageNumber = Number(new URLSearchParams(location.search).get("page") || 0);
  const [state, setState] = useState({ status: "loading", data: null, error: null, updatingId: null });

  useEffect(() => {
    const controller = new AbortController();
    getWatchlist(pageNumber, { signal: controller.signal })
      .then((data) => setState({ status: "success", data, error: null, updatingId: null }))
      .catch((error) => error.name !== "AbortError" && setState({ status: "error", data: null, error, updatingId: null }));
    return () => controller.abort();
  }, [pageNumber]);

  const changeFavorite = async (stockId) => {
    setState((current) => ({ ...current, updatingId: stockId, error: null }));
    try {
      await toggleFavorite(stockId);
      const data = await getWatchlist(pageNumber);
      setState({ status: "success", data, error: null, updatingId: null });
    } catch (error) {
      setState((current) => ({ ...current, error, updatingId: null }));
    }
  };

  return (
    <div className="page"><Header /><main className="main"><section className="content">
      <div className="page-heading"><h1>관심 종목</h1><p>관심 있게 지켜보는 종목을 모아 시세와 기업 정보를 빠르게 확인합니다.</p></div>
      <OverviewStatus status={state.status} error={state.error} />
      {state.status === "success" && <section><h2>관심 종목 목록</h2>
        {state.data.stocks.length === 0
          ? <div className="empty-state"><span className="empty-state-icon">☆</span><strong>아직 관심 종목이 없습니다</strong><p>종목을 검색하고 별표를 눌러 나만의 관심 목록을 만들어 보세요.</p><Link to="/investments/stocks/search">종목 검색하기</Link></div>
          : <StockTable stocks={state.data.stocks} onToggleFavorite={changeFavorite} updatingId={state.updatingId} showCreatedAt />}
        <Pagination page={state.data.page} createUrl={(page) => `/investments/stocks/watchlist?page=${page}`} label="관심 종목 페이지" />
        {state.error && <p className="overview-error">{state.error.message}</p>}
      </section>}
      <p className="page-links"><Link to="/investments/stocks/search">종목 검색</Link><Link to="/investments">투자 홈</Link></p>
    </section></main></div>
  );
}
