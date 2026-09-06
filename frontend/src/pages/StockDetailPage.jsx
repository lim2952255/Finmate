import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { getJson } from "../api/forms.js";
import Header from "../components/layout/Header.jsx";
import PageLoading from "../components/common/PageLoading.jsx";
import CandlestickChart from "../components/stock/CandlestickChart.jsx";
import StockConceptDrawer from "../components/stock/StockConceptDrawer.jsx";
import { FinancialPanel, InvestorPanel, QuotePanel } from "../components/stock/StockDetailPanels.jsx";
import useDocumentTitle from "../hooks/useDocumentTitle.js";
import { countNewsSentiments, NEWS_SENTIMENT_LABELS } from "../utils/newsSentiment.js";
import "../styles/stock-detail.css";

const tabs = [
  { value: "quote", label: "시세" },
  { value: "financial", label: "재무" },
  { value: "investor", label: "투자자" },
  { value: "news", label: "뉴스" }
];

const MINUTE_INTERVALS = {
  MINUTE_1: 1,
  MINUTE_3: 3,
  MINUTE_5: 5,
  MINUTE_15: 15
};

function minuteBucketDate(tradeDate, tradeTime, interval) {
  const date = String(tradeDate || "").replace(/^(\d{4})(\d{2})(\d{2})$/, "$1-$2-$3");
  const time = String(tradeTime || "").padStart(6, "0");
  const minutes = MINUTE_INTERVALS[interval];
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !minutes || !/^\d{6}$/.test(time)) return null;
  const hour = Number(time.slice(0, 2));
  const minute = Math.floor(Number(time.slice(2, 4)) / minutes) * minutes;
  return `${date}T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00`;
}

function intervalStartDate(tradeDate, interval) {
  const [year, month, day] = tradeDate.split("-").map(Number);
  if (!year || !month || !day) return tradeDate;
  if (interval === "YEAR") return `${year}-01-01`;
  if (interval === "MONTH") return `${year}-${String(month).padStart(2, "0")}-01`;
  if (interval !== "WEEK") return tradeDate;
  const date = new Date(Date.UTC(year, month - 1, day));
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  date.setUTCDate(date.getUTCDate() - daysSinceMonday);
  return date.toISOString().slice(0, 10);
}

function normalizeNewsText(value) {
  if (!value) return "";
  return new DOMParser().parseFromString(value, "text/html").body.textContent || "";
}

function formatPublishedAt(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function articleUrl(item) {
  const candidate = item.originalLink || item.link;
  if (!candidate) return null;
  try {
    const url = new URL(candidate);
    return ["http:", "https:"].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function NewsPanel({ stockId }) {
  const [state, setState] = useState({ loading: true, data: null, error: null });

  useEffect(() => {
    const controller = new AbortController();
    // 운영 API는 설정으로 선택된 개선된 TF-IDF 전략의 뉴스 목록만 반환한다.
    getJson(`/api/stocks/${stockId}/news`, { signal: controller.signal })
      .then((data) => setState({ loading: false, data, error: null }))
      .catch((error) => {
        if (error.name !== "AbortError") setState({ loading: false, data: null, error });
      });
    return () => controller.abort();
  }, [stockId]);

  if (state.loading) return <PageLoading message="최신 뉴스를 불러오고 있습니다." />;
  if (state.error) return <p className="overview-error">{state.error.message}</p>;
  if (!state.data?.items) return <div className="empty-state"><strong>표시할 뉴스가 없습니다.</strong></div>;

  const newsItems = state.data.items;
  const sentimentCounts = countNewsSentiments(newsItems);

  return (
    <div className="stock-news-panel">
      <div className="stock-news-toolbar">
        <div className="stock-news-heading">
          <span className="stock-news-heading-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 5h16v14H4z" /><path d="M8 9h8M8 13h5M8 17h3" /></svg>
          </span>
          <div><span className="stock-news-kicker">STOCK BRIEFING</span><h2>관련 뉴스</h2><p>관련도와 새로운 정보를 고려해 선별한 뉴스입니다.</p></div>
        </div>
        <div className="stock-news-cache-info">
          <div className="detail-updated-at">업데이트 {formatPublishedAt(state.data.updatedAt)}</div>
          <div className="stock-news-query">{state.data.query ? `검색 기준 ${state.data.query}` : ""}</div>
        </div>
      </div>
      <div className="stock-news-keyword-area">
        <div className="stock-news-keyword-group">
          <span className="stock-news-keyword-title">선별 키워드</span>
          <div className="stock-news-keywords">{state.data.keywords?.map((keyword) => <span key={keyword}>{keyword}</span>)}</div>
        </div>
        <div className="stock-news-sentiment-summary" aria-label="뉴스 감성 분석 요약">
          <span className="stock-news-summary-item stock-news-summary-item--positive">호재: <strong>{sentimentCounts.POSITIVE}개</strong></span>
          <span className="stock-news-summary-item stock-news-summary-item--neutral">보통: <strong>{sentimentCounts.NEUTRAL}개</strong></span>
          <span className="stock-news-summary-item stock-news-summary-item--negative">악재: <strong>{sentimentCounts.NEGATIVE}개</strong></span>
        </div>
      </div>
      <div className="stock-news-list">
        {newsItems.map((item, index) => {
          const url = articleUrl(item);
          const source = url ? new URL(url).hostname.replace(/^www\./, "") : "NAVER 뉴스 검색";
          const sentimentLabel = NEWS_SENTIMENT_LABELS[item.sentiment] || "보통";
          return <article className="stock-news-item" key={url || index}>
            <div className="stock-news-item-top">
              <span className="stock-news-source">{source}</span>
              <div className="stock-news-labels">
                {/* API 감성 enum을 사용해 각 뉴스에 호재·보통·악재 배지를 표시한다. */}
                <span className={`stock-news-sentiment stock-news-sentiment--${item.sentiment?.toLowerCase() || "neutral"}`}>
                  {sentimentLabel}
                </span>
                <span className="stock-news-rank">{String(index + 1).padStart(2, "0")}</span>
              </div>
            </div>
            <h3>{url ? <a href={url} target="_blank" rel="noreferrer">{normalizeNewsText(item.title)}</a> : normalizeNewsText(item.title)}</h3>
            {item.description && <p className="stock-news-description">{normalizeNewsText(item.description)}</p>}
            <div className="stock-news-meta"><time>{formatPublishedAt(item.publishedAt)}</time>{url && <a href={url} target="_blank" rel="noreferrer">기사 읽기</a>}</div>
          </article>;
        })}
      </div>
      {!newsItems.length && <div className="empty-state"><strong>선별된 뉴스가 없습니다.</strong></div>}
    </div>
  );
}

function ChatPanel({ stockId, currentUserId, stockName }) {
  const socketRef = useRef(null);
  const [messages, setMessages] = useState([]);
  const [connected, setConnected] = useState(false);
  const [onlineCount, setOnlineCount] = useState(0);
  const [history, setHistory] = useState({ nextCursor: null, hasNext: false });
  const [content, setContent] = useState("");
  const [compose, setCompose] = useState(null);
  const [chatError, setChatError] = useState(null);

  const mergeMessages = useCallback((current, incoming) => {
    const byId = new Map(current.map((item) => [Number(item.id), item]));
    incoming.forEach((item) => byId.set(Number(item.id), item));
    return Array.from(byId.values()).sort((left, right) => Number(left.id) - Number(right.id));
  }, []);

  const loadHistory = useCallback((beforeId) => {
    const query = new URLSearchParams({ size: "50" });
    if (beforeId) query.set("beforeId", beforeId);
    return getJson(`/api/stocks/${stockId}/chat/messages?${query}`)
      .then((data) => {
        setMessages((current) => mergeMessages(current, data.messages || []));
        setHistory({ nextCursor: data.nextCursor, hasNext: data.hasNext });
      });
  }, [mergeMessages, stockId]);

  useEffect(() => {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/chat`);
    socketRef.current = socket;
    socket.addEventListener("open", () => socket.send(JSON.stringify({ type: "JOIN_ROOM", stockId: Number(stockId) })));
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.type === "SUBSCRIBED") {
        setConnected(true);
        setOnlineCount(Number(message.onlineCount) || 0);
        loadHistory(Number(message.latestMessageId) + 1 || undefined).catch(() => setMessages([]));
      }
      if (message.type === "PRESENCE") setOnlineCount(Number(message.onlineCount) || 0);
      if (message.type === "ERROR") setChatError(message.message || "채팅 요청을 처리할 수 없습니다.");
      if (message.type === "CHAT_MESSAGE") {
        setChatError(null);
        setMessages((current) => mergeMessages(current, [message]));
        if (message.deleted) setCompose((current) => current?.id === message.id ? null : current);
      }
    });
    socket.addEventListener("close", () => { setConnected(false); setOnlineCount(0); });

    return () => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "LEAVE_ROOM", stockId: Number(stockId) }));
      socket.close();
    };
  }, [loadHistory, mergeMessages, stockId]);

  const send = (event) => {
    event.preventDefault();
    if (socketRef.current?.readyState !== WebSocket.OPEN || !content.trim()) return;
    socketRef.current.send(JSON.stringify(compose?.mode === "edit"
      ? { type: "EDIT_MESSAGE", stockId: Number(stockId), messageId: compose.id, content: content.trim() }
      : { type: "SEND_MESSAGE", stockId: Number(stockId), parentMessageId: compose?.mode === "reply" ? compose.id : null, content: content.trim() }));
    setContent("");
    setCompose(null);
  };

  const beginReply = (message) => { setCompose({ mode: "reply", id: message.id, username: message.username }); setContent(""); };
  const beginEdit = (message) => { setCompose({ mode: "edit", id: message.id, username: message.username }); setContent(message.content || ""); };
  const remove = (message) => {
    if (socketRef.current?.readyState === WebSocket.OPEN && window.confirm("이 메시지를 삭제하시겠습니까?")) {
      socketRef.current.send(JSON.stringify({ type: "DELETE_MESSAGE", stockId: Number(stockId), messageId: message.id }));
    }
  };

  const formatTime = (value) => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date);
  };

  return (
    <section className="stock-chat">
      <div className="stock-chat-header"><div className="stock-chat-heading"><span className="stock-chat-heading-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z" /></svg></span><div><div className="stock-chat-title-row"><h2>종목 채팅</h2><span className="stock-chat-live-label">LIVE</span></div><p><strong>{stockName}</strong> 투자자들과 실시간으로 의견을 나눠보세요.</p></div></div><span className="stock-chat-presence"><strong>{onlineCount}</strong>명 참여 중</span></div>
      <div className="stock-chat-history-control">{history.hasNext && <button className="stock-chat-load-older" type="button" onClick={() => loadHistory(history.nextCursor, true)}>이전 대화 불러오기</button>}</div>
      <div className="stock-chat-messages" aria-live="polite">{messages.length === 0 ? <div className="stock-chat-empty">아직 대화가 없습니다. 첫 의견을 남겨보세요.</div> : messages.map((message) => {
        const own = Number(message.userId) === Number(currentUserId);
        return <article className={`stock-chat-message${own ? " own" : ""}${message.parentMessageId ? " reply" : ""}${message.deleted ? " deleted" : ""}`} key={message.id}><span className="stock-chat-avatar" aria-hidden="true">{own ? "나" : Array.from(message.username || "F")[0]}</span><div className="stock-chat-message-body"><div className="stock-chat-meta"><strong className="stock-chat-author">{own ? "나" : message.username}</strong><time className="stock-chat-time">{formatTime(message.createdAt)}</time>{message.edited && <span className="stock-chat-edited">수정됨</span>}</div>{message.parentMessageId && <div className="stock-chat-reply-reference"><strong className="stock-chat-reply-author">{message.replyToUsername}</strong><span className="stock-chat-reply-content">{message.replyToDeleted ? "삭제된 메시지입니다." : message.replyToContent}</span></div>}<div className="stock-chat-bubble">{message.deleted ? "삭제된 메시지입니다." : message.content}</div>{!message.deleted && <div className="stock-chat-message-actions"><button className="stock-chat-message-action" type="button" onClick={() => beginReply(message)}>답글</button>{own && <><button className="stock-chat-message-action" type="button" onClick={() => beginEdit(message)}>수정</button><button className="stock-chat-message-action delete" type="button" onClick={() => remove(message)}>삭제</button></>}</div>}</div></article>;
      })}</div>
      {chatError && <p className="overview-error" role="alert">{chatError}</p>}
      <form className="stock-chat-form" onSubmit={send}>{compose && <div className="stock-chat-compose-context"><span>{compose.mode === "edit" ? "메시지 수정 중" : `${compose.username}님에게 답글 작성 중`}</span><button type="button" onClick={() => { setCompose(null); setContent(""); }} aria-label="답글 또는 수정 취소">×</button></div>}<div className="stock-chat-composer"><textarea maxLength="500" rows="1" placeholder="이 종목에 대한 의견을 남겨보세요." aria-label="채팅 메시지" value={content} onChange={(event) => setContent(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); event.currentTarget.form.requestSubmit(); } }} /><div className="stock-chat-compose-actions"><span className="stock-chat-character-count">{content.length}/500</span><button type="submit" disabled={!connected || !content.trim()}>전송</button></div></div></form>
      <div className="stock-chat-footer"><p className={`stock-chat-status${connected ? "" : " error"}`}>{connected ? "실시간 채팅에 연결되었습니다." : "채팅 서버에 연결 중입니다."}</p><span className="stock-chat-input-hint">Enter 전송 · Shift + Enter 줄바꿈</span></div>
    </section>
  );
}

export default function StockDetailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const stockId = new URLSearchParams(location.search).get("stockId");
  const requestedInterval = new URLSearchParams(location.search).get("interval") || "DAY";
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loadedSearch, setLoadedSearch] = useState(null);
  const [tab, setTab] = useState("quote");
  const [orderbook, setOrderbook] = useState(null);
  const [drawer, setDrawer] = useState({ open: false, loading: false, title: "", concept: null, error: null });
  const detailLoading = loadedSearch !== location.search;
  const pageError = stockId ? error : new Error("조회할 종목이 지정되지 않았습니다.");
  useDocumentTitle(`${data?.nameKo || "종목 상세"} | FinMate`);

  useEffect(() => {
    if (!stockId) {
      return undefined;
    }

    const controller = new AbortController();
    getJson(`/api/investment-read/stock-detail${location.search}`, { signal: controller.signal })
      .then((detail) => { setData(detail); setError(null); setLoadedSearch(location.search); })
      .catch((requestError) => {
        if (requestError.name !== "AbortError") {
          setError(requestError);
          setLoadedSearch(location.search);
        }
      });
    return () => controller.abort();
  }, [location.search, stockId]);

  useEffect(() => {
    if (!stockId) return undefined;
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/stocks`);
    socket.addEventListener("open", () => socket.send(JSON.stringify({ type: "SUBSCRIBE_STOCK", stockId: Number(stockId) })));
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.type === "STOCK_TRADE" && Number(message.stockId) === Number(stockId)) {
        setData((current) => {
          if (!current) return current;
          const currentPrice = Number(message.currentPrice);
          const minuteBucket = minuteBucketDate(message.tradeDate, message.tradeTime, current.selectedInterval);
          if (minuteBucket) {
            const candles = [...current.candles];
            const existingCandle = candles.at(-1)?.tradeDate === minuteBucket ? candles.at(-1) : null;
            const tradeVolume = Number(message.tradeVolume ?? 0);
            const realtimeCandle = {
              tradeDate: minuteBucket,
              openPrice: String(existingCandle?.openPrice ?? currentPrice),
              highPrice: String(Math.max(Number(existingCandle?.highPrice ?? currentPrice), currentPrice)),
              lowPrice: String(Math.min(Number(existingCandle?.lowPrice ?? currentPrice), currentPrice)),
              closePrice: String(currentPrice),
              accumulatedVolume: String(Number(existingCandle?.accumulatedVolume ?? 0) + tradeVolume),
              accumulatedTradeAmount: String(Number(existingCandle?.accumulatedTradeAmount ?? 0) + currentPrice * tradeVolume),
              completed: false
            };
            if (existingCandle) {
              candles[candles.length - 1] = realtimeCandle;
            } else {
              if (candles.length) candles[candles.length - 1] = { ...candles.at(-1), completed: true };
              candles.push(realtimeCandle);
            }
            return {
              ...current,
              // 유효한 실시간 체결가가 도착하면 주문 화면 진입을 즉시 활성화한다.
              realtimePriceAvailable: Number.isFinite(currentPrice) && currentPrice > 0,
              candles,
              latestTradeDate: minuteBucket.slice(0, 10),
              latestCandleAt: minuteBucket,
              latestClosePrice: String(message.currentPrice),
              latestChangeAmount: String(message.change ?? current.latestChangeAmount),
              latestChangeRate: String(message.changeRate ?? current.latestChangeRate)
            };
          }
          const openPrice = Number(message.openPrice ?? currentPrice);
          const highPrice = Math.max(Number(message.highPrice ?? currentPrice), openPrice, currentPrice);
          const lowPrice = Math.min(Number(message.lowPrice ?? currentPrice), openPrice, currentPrice);
          const tradeDate = String(message.tradeDate || current.latestTradeDate || "").replace(/^(\d{4})(\d{2})(\d{2})$/, "$1-$2-$3");
          const candleDate = intervalStartDate(tradeDate, current.selectedInterval);
          const existingCandle = current.candles.at(-1)?.tradeDate === candleDate ? current.candles.at(-1) : null;
          const dailyVolume = Number(message.accumulatedVolume ?? 0);
          const dailyTradeAmount = Number(message.accumulatedTradeAmount ?? 0);
          const sameRealtimeDay = current.realtimeTradeDate === tradeDate;
          const serverBaseApplies = current.currentCandleTradeDate === tradeDate;
          const baseVolume = sameRealtimeDay
            ? Number(current.realtimeBaseVolume ?? 0)
            : current.selectedInterval === "DAY" ? 0
              : serverBaseApplies ? Number(current.currentCandleBaseVolume ?? 0)
                : Number(existingCandle?.accumulatedVolume ?? 0);
          const rawBaseTradeAmount = sameRealtimeDay
            ? current.realtimeBaseTradeAmount
            : current.selectedInterval === "DAY" ? 0
              : serverBaseApplies ? current.currentCandleBaseTradeAmount
                : existingCandle?.accumulatedTradeAmount;
          const baseTradeAmountKnown = current.selectedInterval === "DAY" || rawBaseTradeAmount != null;
          const baseTradeAmount = Number(rawBaseTradeAmount ?? 0);
          const realtimeCandle = {
            tradeDate: candleDate,
            openPrice: String(existingCandle?.openPrice ?? openPrice),
            highPrice: String(Math.max(Number(existingCandle?.highPrice ?? highPrice), highPrice)),
            lowPrice: String(Math.min(Number(existingCandle?.lowPrice ?? lowPrice), lowPrice)),
            closePrice: String(currentPrice),
            accumulatedVolume: String(baseVolume + dailyVolume),
            accumulatedTradeAmount: baseTradeAmountKnown && message.accumulatedTradeAmount != null
              ? String(baseTradeAmount + dailyTradeAmount)
              : null
          };
          const candles = [...current.candles];
          if (existingCandle) candles[candles.length - 1] = realtimeCandle;
          else candles.push(realtimeCandle);
          return {
            ...current,
            // 유효한 실시간 체결가가 도착하면 주문 화면 진입을 즉시 활성화한다.
            realtimePriceAvailable: Number.isFinite(currentPrice) && currentPrice > 0,
            candles,
            realtimeTradeDate: tradeDate,
            realtimeBaseVolume: baseVolume,
            realtimeBaseTradeAmount: baseTradeAmountKnown ? baseTradeAmount : null,
            latestTradeDate: tradeDate,
            latestClosePrice: String(message.currentPrice),
            latestChangeAmount: String(message.change ?? current.latestChangeAmount),
            latestChangeRate: String(message.changeRate ?? current.latestChangeRate)
          };
        });
      }
      if (message.type === "STOCK_ORDERBOOK" && Number(message.stockId) === Number(stockId)) {
        const askLevels = Array.isArray(message.askLevels) ? message.askLevels : [];
        const bidLevels = Array.isArray(message.bidLevels) ? message.bidLevels : [];
        setOrderbook({
          quoteTime: message.quoteTime,
          levels: Array.from({ length: Math.max(askLevels.length, bidLevels.length) }, (_, index) => ({
            askPrice: askLevels[index]?.price,
            askQuantity: askLevels[index]?.quantity,
            bidPrice: bidLevels[index]?.price,
            bidQuantity: bidLevels[index]?.quantity
          }))
        });
      }
    });
    return () => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "UNSUBSCRIBE_STOCK", stockId: Number(stockId) }));
      socket.close();
    };
  }, [stockId]);

  const changeInterval = (value) => {
    if (value === requestedInterval) return;
    setError(null);
    const next = new URLSearchParams(location.search);
    next.delete("period");
    next.set("interval", value);
    navigate(`${location.pathname}?${next}`);
  };

  const openConcept = (code, title) => {
    setDrawer({ open: true, loading: true, title, concept: null, error: null });
    getJson(`/api/stocks/${stockId}/concepts/${code}`)
      .then((concept) => setDrawer({ open: true, loading: false, title: concept.title || title, concept, error: null }))
      .catch((requestError) => setDrawer({ open: true, loading: false, title, concept: null, error: requestError }));
  };

  return (
    <div className="page">
      <Header />
      <main className="main">
        <section className="content">
          {!data && !pageError && <PageLoading message="실시간 시세와 차트를 불러오고 있습니다." />}
          {pageError && <p className="overview-error" role="alert">{pageError.message}</p>}
          {data && (
            <>
              <div className="page-heading"><h1>{data.nameKo}{data.nameEn ? ` (${data.nameEn})` : ""}</h1><p>실시간 시세와 기업 재무, 투자자별 매매 수급을 탭으로 확인합니다.</p></div>
              <section>
                <div className="detail-heading-with-concept"><h2>{data.intervals.find((item) => item.value === requestedInterval)?.label || requestedInterval} 차트</h2><button className="concept-help-button concept-help-button--summary" type="button" onClick={() => openConcept("DAILY_CANDLE_CHART", "캔들 차트 보는 법")} aria-label="캔들 차트 보는 법 개념 익히기">차트 보는 법</button></div>
                <form className="chart-control-form" onSubmit={(event) => event.preventDefault()}><label>봉 주기<select value={requestedInterval} onChange={(event) => changeInterval(event.target.value)}>{data.intervals.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><button type="submit" disabled={detailLoading}>{detailLoading ? "조회 중" : "조회"}</button></form>
                {detailLoading
                  ? <div className="chart-loading-state" role="status" aria-live="polite"><span className="route-spinner" aria-hidden="true" /><strong>{data.intervals.find((item) => item.value === requestedInterval)?.label || requestedInterval} 데이터를 불러오는 중입니다.</strong><p>KIS API 응답을 기다리고 있습니다.</p></div>
                  : <CandlestickChart candles={data.candles} currency={data.currency} interval={data.selectedInterval} intervalLabel={data.intervals.find((item) => item.value === data.selectedInterval)?.label || data.selectedInterval} detail={data} orderbook={orderbook} stockId={data.id} />}
              </section>
              <div className="stock-detail-tabs" role="tablist" aria-label="종목 상세 정보">{tabs.map((item) => <button className="stock-detail-tab" role="tab" aria-selected={tab === item.value} tabIndex={tab === item.value ? 0 : -1} key={item.value} type="button" onClick={() => setTab(item.value)}>{item.label}</button>)}</div>
              <div className="stock-detail-panel" role="tabpanel" tabIndex="0">
                {tab === "quote" && <QuotePanel detail={data} onConcept={openConcept} />}
                {tab === "financial" && <FinancialPanel detail={data} onConcept={openConcept} />}
                {tab === "investor" && <InvestorPanel detail={data} onConcept={openConcept} />}
                {tab === "news" && <NewsPanel stockId={stockId} />}
              </div>
              <ChatPanel stockId={stockId} currentUserId={data.currentUserId} stockName={data.nameKo} />
              <section className="stock-action-section"><div>{data.tradingAvailable && !data.realtimePriceAvailable ? <span className="stock-action-button primary disabled" aria-disabled="true">실시간 시세 수신 대기</span> : <Link className="stock-action-button primary" to={`/investments/stocks/order/${stockId}`}>{data.tradingAvailable ? "주식 매수 / 매도" : "예약 주문"}</Link>}<p className="stock-action-note">{data.tradingAvailable && !data.realtimePriceAvailable ? "실시간 주가가 수신되면 주문할 수 있습니다." : data.tradingAvailable ? `거래 가능 시간: ${data.tradingTimeDescription}` : "장 마감 후에는 예약 주문을 등록할 수 있습니다."}</p></div><div className="stock-action-links"><Link className="stock-action-button" to="/investments/stocks/search">종목 검색</Link><Link className="stock-action-button" to="/investments/stocks/watchlist">관심 종목</Link><Link className="stock-action-button" to="/investments">투자 홈</Link></div></section>
              <StockConceptDrawer state={drawer} onClose={() => setDrawer((current) => ({ ...current, open: false }))} />
            </>
          )}
        </section>
      </main>
    </div>
  );
}
