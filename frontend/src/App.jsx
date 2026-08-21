import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/auth/ProtectedRoute.jsx";
import AccountHomePage from "./pages/AccountHomePage.jsx";
import AccountListPage from "./pages/AccountListPage.jsx";
import AccountOpenPage from "./pages/AccountOpenPage.jsx";
import AccountOperationPage from "./pages/AccountOperationPage.jsx";
import { LoginPage, SignupPage } from "./pages/AuthPages.jsx";
import HomePage from "./pages/HomePage.jsx";
import InvestmentLearningPage from "./pages/InvestmentLearningPage.jsx";
import InvestmentHomePage from "./pages/InvestmentHomePage.jsx";
import InvestmentListPage from "./pages/InvestmentListPage.jsx";
import MarketMoversPage from "./pages/MarketMoversPage.jsx";
import MarketReportsPage from "./pages/MarketReportsPage.jsx";
import StockSearchPage from "./pages/StockSearchPage.jsx";
import WatchlistPage from "./pages/WatchlistPage.jsx";
import TransactionHistoryPage from "./pages/TransactionHistoryPage.jsx";
import PortfolioPage from "./pages/PortfolioPage.jsx";
import { MarketDataDetailPage, MarketDataIndexPage } from "./pages/MarketDataPage.jsx";
import StockDetailPage from "./pages/StockDetailPage.jsx";
import { OrderPage, TradingHistoryPage } from "./pages/TradingPages.jsx";

export default function App() {
  return (
    <Routes>
      {/* 브라우저의 요청 URL에 따라 적절한 컴포넌트를 호출하여 화면을 렌더링한다. */}
      <Route path="/" element={<Navigate to="/home" replace />} />
      <Route path="/home" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />

      {/* 이 그룹 안의 화면은 ProtectedRoute의 세션 검사를 통과해야 렌더링된다. */}
      <Route element={<ProtectedRoute />}>
        <Route path="/investment-learning" element={<InvestmentLearningPage />} />
        <Route path="/accounts" element={<AccountHomePage />} />
        <Route path="/accounts/list" element={<AccountListPage />} />
        <Route path="/accounts/open" element={<AccountOpenPage />} />
        <Route path="/accounts/transfer" element={<AccountOperationPage type="transfer" />} />
        <Route path="/accounts/transfer-investment" element={<AccountOperationPage type="deposit" />} />
        <Route path="/accounts/transfer-limit" element={<AccountOperationPage type="limit" />} />
        <Route path="/accounts/transactions" element={<TransactionHistoryPage type="account" />} />
        <Route path="/investments" element={<InvestmentHomePage />} />
        <Route path="/investments/list" element={<InvestmentListPage />} />
        <Route path="/investments/open" element={<AccountOpenPage investment />} />
        <Route path="/investments/transfer" element={<AccountOperationPage type="withdraw" />} />
        <Route path="/investments/currency-exchange" element={<AccountOperationPage type="exchange" />} />
        <Route path="/investments/securityCashTransaction" element={<TransactionHistoryPage type="cash" />} />
        <Route path="/investments/currency-exchange/transactions" element={<TransactionHistoryPage type="exchange" />} />
        <Route path="/investments/portfolio" element={<PortfolioPage />} />
        <Route path="/investments/market-data" element={<MarketDataIndexPage />} />
        <Route path="/investments/exchanges" element={<MarketDataDetailPage type="EXCHANGE_RATE" />} />
        <Route path="/investments/indices" element={<MarketDataDetailPage type="STOCK_INDEX" />} />
        <Route path="/investments/stocks/search" element={<StockSearchPage />} />
        <Route path="/investments/stocks/watchlist" element={<WatchlistPage />} />
        <Route path="/investments/stocks/detail" element={<StockDetailPage />} />
        <Route path="/investments/orders" element={<TradingHistoryPage />} />
        <Route path="/investments/stocks/order/:stockId" element={<OrderPage />} />
        <Route path="/investments/reports" element={<MarketReportsPage />} />
        <Route path="/investments/stocks/market-movers" element={<MarketMoversPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/home" replace />} />
    </Routes>
  );
}
