package com.finmate.infra.kis.stock.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.infra.kis.core.KisProperties;
import com.finmate.infra.kis.core.KisRetryConnection;
import com.finmate.infra.kis.websocket.KisWebSocketApprovalService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// (KIS WebSocket <-> 스프링 웹서버)
// KIS 한국투자증권 WebSocket 서버에 연결해서 실시간 시세를 받고, 사용자가 구독한 종목의 최신 데이터를 메모리에 저장하는 클라이언트 서비스
// 몇명의 클라이언트들이 종목을 구독하고 있는지를 counter로 관리하면서 종목에 대한 구독정보를 관리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class KisRealtimeWebSocketClient {
    private static final String SUBSCRIBE_TYPE = "1"; // 종목 구독 타입
    private static final String UNSUBSCRIBE_TYPE = "2"; // 종목 구독해제 타입
    private static final long RECONNECT_DELAY_SECONDS = 3L; // KIS 웹소켓과 연결이 해제된 경우, 다시 WebSocket에 연결하기 위한 딜레이 설정

    private final KisProperties kisProperties; // KIS API에 연결하기 위한 속성 저장
    private final KisWebSocketApprovalService approvalService; // KIS WebSocket에 연결하기 위한 ApprovalKey 발급 및 저장
    private final KisRealtimeMessageParser messageParser; // KIS WebSocket을 통해 받은 실시간 데이터(Json 문자열)을 파싱하는 클래스
    private final KisRealtimeStore realtimeStore; // KIS WebSocket을 통해 받은 실시간 데이터들을 메모리에 저장하는 저장소
    private final ObjectMapper objectMapper; // Json 문자열 -> 자바 객체 / 자바 객체 -> Json 문자열 변환
    private final ApplicationEventPublisher eventPublisher; // KIS 수신 데이터를 내부 WebSocket 계층으로 전달하기 위한 이벤트 발생기
    private final KisRetryConnection kisRetryConnection; // KIS WebSocket 연결 실패 시 재시도

    // 실시간으로 데이터를 받을 종목들(구독한 종목들)을 관리하는 set (실시간으로 변하기 때문에 메모리에 저장)
    private final Set<KisRealtimeSubscription> activeSubscriptions = ConcurrentHashMap.newKeySet();

    // 재연결 스케줄러. 이는 데몬으로 실행해서 이로 인해 JVM 프로그램이 종료되지 않는 것을 방지
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kis-realtime-reconnect");
        thread.setDaemon(true);
        return thread;
    });
    // 재연결 중복예약 방지
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    // 연결 상태 변경용 락
    private final Object connectionLock = new Object();
    // volatile은 각 스레드마다의 캐시에서 데이터를 조회하는 것이 아니라, 메모리에서 데이터를 조회하기 떄문에,
    // 스레드마다 데이터일관성이 깨지는 것을 방지한다.
    // 현재 연결된 웹소켓 객체
    private volatile WebSocket webSocket;
    // 웹소켓 연결 여부
    private volatile boolean connected;
    // 웹소켓 연결 시도 중 여부
    private volatile boolean connecting;
    // 애플리케이션 종료 여부
    private volatile boolean shutdown;

    // KIS WebSocket에 연결을 시작하는 메서드(외부에서 명시적으로 호출하여 연결하는 용도)
    public void connect() {
        ensureConnected();
    }

    // 특정 종목을 구독하는 메서드
    public void subscribe(KisRealtimeSubscription subscription) {
        boolean added = false;

        try {
            ensureConnected(); // 연결을 보장하는 메서드(연결이 안되어 있으면 연결, 연결이 되어 있으면 유지)
            added = activeSubscriptions.add(subscription); // 종목을 구독목록에 추가 (만약 이미 종목이 구독상태였다면 false를 리턴)

            if (added) {
                // WebSocket에 종목 구독을 추가
                // added가 true라는 것은 해당 종목이 이번에 새로 구독상태가 되었다는 의미이기 때문에, KIS WebSocket에 종목을 구독한다는 메세지를 전송한다.
                sendSubscription(subscription, SUBSCRIBE_TYPE);
                log.info("KIS realtime subscribed. trId={}, trKey={}",
                        subscription.api().getTrId(),
                        subscription.trKey());
            }
        } catch (RuntimeException e) {
            if (added) {
                // 종목을 추가하는 도중에 오류 발생시 추가내용을 제거함으로서 정합성을 유지한다.
                activeSubscriptions.remove(subscription);
            }
            throw e;
        }
    }

    // 종목에 대한 구독을 해제하는 메서드
    // 이는 한 클라이언트가 구독을 해제한다고 바로 호출해서는 안되며, 각 종목에 대한 구독 숫자(counter)를 기반으로 해당 counter가 0이 된 경우에만 호출되어야 한다.
    public void unsubscribe(KisRealtimeSubscription subscription) {
        boolean removed = activeSubscriptions.remove(subscription); // 종목에 대한 구독 해제
        if (!removed) {
            return; // 만약 종목이 구독상태가 아니였다면 return
        }

        if (connected && webSocket != null) { // WebSocket이 연결상태라면 KIS WebSocket에 연결해제 메세지를 보낸다.
            sendSubscription(subscription, UNSUBSCRIBE_TYPE);
        }

        log.info("KIS realtime unsubscribed. trId={}, trKey={}",
                subscription.api().getTrId(),
                subscription.trKey());
    }

    // 현재 구독중인 종목 목록을 리턴한다.
    public List<KisRealtimeSubscription> getActiveSubscriptions() {
        return activeSubscriptions.stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }
    // 연결이 안되어 있으면 연결하고, 종료상태면 연결하지 않고, 이미 연결되어 있으면 아무것도 하지 않는다.(연결상태를 보장하는 메서드)
    private void ensureConnected() {
        synchronized (connectionLock) { // connectionLock을 획득한 상태
            while (!shutdown && connecting) {
                // 만약 아직 애플리케이션이 종료 상태가 아니며, 다른 스레드에서 이미 연결을 시도중인 상황이라 다른 스레드에서 연결이 끝날때까지 대기한다.
                waitUntilConnectionAttemptFinished();
                // 다른 스레드에서 연결을 시도 후 연결을 실패한 상황이라면 연결을 시도하고, 프로그램이 종료되었거나 이미 연결이 되었다면 종료한다.
            }
            // 만약 애플리케이션이 종료상태이거나 이미 연결되어 있는 상태라면 바로 return한다.
            if (shutdown || connected) {
                return;
            }
            connecting = true;
        }
        // 연결이 안되어 있는 경우에는 KIS WebSocket에 연결한다.
        try {
            approvalService.getApprovalKey();
            // KIS WebSocket에 연결하기 위한 URI 생성
            URI endpoint = URI.create(kisProperties.getNormalizedWebSocketEndpoint());
            // KIS WebSocket에 연결하는 메서드 이때 KisRetryConnection을 활용하여 최대 5번까지 연결을 재시도한다.
            WebSocket newWebSocket = kisRetryConnection.webSocketConnectionAndRetry(
                    endpoint,
                    new KisRealtimeWebSocketListener());

            // 연결상태를 설정하는 와중에 다른 스레드에서 연결을 시도할 수 있기 때문에 동기화 락을 건다.
            synchronized (connectionLock) {
                // 웹소켓에 연결한 이후, 상태를 설정하는 메서드
                this.webSocket = newWebSocket;
                this.connected = true;
            }

            log.info("KIS realtime websocket connected. endpoint={}", endpoint);
            // WebSocket에 재연결시, 기존에 구독중이던 종목들을 다시 구독하는 요청을 보내는 메서드
            resubscribeAll();
            // 연결 성공 시 connecting 상태를 false로 변경하고, 대기중이던 스레드들을 깨운다.
            synchronized (connectionLock) {
                this.connecting = false;
                connectionLock.notifyAll(); // 대기중인 스레드를 깨운다.
            }
        } catch (Exception e) {
            synchronized (connectionLock) {
                // 만약 웹소켓 연결에 실패하면 상태를 초기화한다.
                this.webSocket = null;
                this.connected = false;
                this.connecting = false;
                connectionLock.notifyAll(); // 대기중인 스레드를 깨운다.
            }
            throw new RuntimeException("KIS realtime websocket connection failed.", e);
        }
    }

    private void waitUntilConnectionAttemptFinished() {
        try {
            // connectionLock을 반환하면서 대기한다.
            connectionLock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("KIS realtime websocket connection wait interrupted.", e);
        }
    }

    // 기존에 구독중이던 종목들을 다시 등록하는 메서드
    private void resubscribeAll() {
        List<KisRealtimeSubscription> subscriptions = new ArrayList<>(activeSubscriptions);
        for (KisRealtimeSubscription subscription : subscriptions) {
            sendSubscription(subscription, SUBSCRIBE_TYPE);
        }
    }

    // KIS Websocket에 구독요청 / 구독해제 메세지를 보낸다. trType이 구독 요청 / 구독헤제를 의미한다.
    private void sendSubscription(KisRealtimeSubscription subscription, String trType) {
        WebSocket currentWebSocket = this.webSocket;
        if (currentWebSocket == null) { // 만약 Websocket이 연결상태가 아니였다면 예외 처리
            throw new IllegalStateException("KIS realtime websocket is not connected.");
        }

        try {
            // 구독요청 / 구독해제 메세지를 Json 문자열로 변환
            String message = buildSubscriptionMessage(subscription, trType);
            // KIS Websocket에 메세지 전송(여기서 true는 큰 메세지를 한번에 보낸다는 의미이다.)
            currentWebSocket.sendText(message, true).join();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("KIS realtime subscription message serialization failed.", e);
        }
    }

    // 구독요청 / 구독해제 요청 메세지를 Json 문자열 형태로 생성하는 메서드
    private String buildSubscriptionMessage(KisRealtimeSubscription subscription, String trType)
            throws JsonProcessingException {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("approval_key", approvalService.getApprovalKey());
        header.put("custtype", "P");
        header.put("tr_type", trType);
        header.put("content-type", "utf-8");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tr_id", subscription.api().getTrId());
        input.put("tr_key", subscription.trKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", header);
        message.put("body", body);

        // 객체를 Json 문자열로 변환
        return objectMapper.writeValueAsString(message);
    }

    // KIS Websocket으로부터 텍스트 메세지를 응답받았을때 이를 파싱하는 메서드
    private void handleTextMessage(WebSocket currentWebSocket, String rawMessage) {
        if (messageParser.isPingPongMessage(rawMessage)) {
            // 만약 KIS WebSocket에서 보낸 메세지가 PingPong 메세지라면, Pong 응답 메세지를 전달한다.
            // KIS WebSocket에서 연결확인용으로 Ping 메세지를 보냈을때, 이에 응답하지 않으면 연결이 해제될 수 있기 때문에, Pong 응답 메세지를 생성해서 전달해야 한다.
            sendPong(currentWebSocket, rawMessage);
            return;
        }

        // raw 응답 메세지를 Parsing해서 realtimeStore에 저장한다.
        messageParser.parsePayload(rawMessage)
                .ifPresentOrElse(
                        payload -> {
                            // realtimeStore에 KIS 실시간 데이터를 저장한다.
                            realtimeStore.put(payload);
                            // 이후 KIS로부터 받은 실시간 데이터를 클라이언트들에게 전달하기 위해 KisRealtimePayloadReceivedEvent라는 이벤트를 발생시킨다.
                            eventPublisher.publishEvent(new KisRealtimePayloadReceivedEvent(payload));
                        },
                        () -> log.debug("KIS realtime system message received. trId={}, raw={}",
                                messageParser.systemTrId(rawMessage).orElse("unknown"),
                                rawMessage));
    }

    // KIS Websocket에 Pong 응답 메세지를 전달한다.
    private void sendPong(WebSocket currentWebSocket, String rawMessage) {
        byte[] payload = rawMessage.getBytes(StandardCharsets.UTF_8);
        if (payload.length > 125) {
            currentWebSocket.sendPong(ByteBuffer.allocate(0));
            return;
        }

        currentWebSocket.sendPong(ByteBuffer.wrap(payload));
    }

    // 웹소켓 연결이 끊겼을 때 호출한다.
    private void markDisconnected(String reason, Throwable error) {
        synchronized (connectionLock) {
            // 연결이 끊기면 상태를 초기화한다.
            this.webSocket = null;
            this.connected = false;
            this.connecting = false;
        }

        if (shutdown) { // 애플리케이션이 종료중이면 리턴
            return;
        }

        if (error == null) {
            log.warn("KIS realtime websocket disconnected. reason={}", reason);
        } else {
            log.warn("KIS realtime websocket disconnected. reason={}", reason, error);
        }

        // 아직 구독중인 종목이 있으 재연결을 수행한다.
        if (!activeSubscriptions.isEmpty()) {
            scheduleReconnect();
        }
    }

    // 재연결을 예약하는 메서드
    private void scheduleReconnect() {
        // 만약 이미 reconnectScheduled가 예약이 되어있는지 확인하고, 예약되어있지 않으면 예약하는 로직(Atomic operation)
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        reconnectExecutor.schedule(() -> {
            reconnectScheduled.set(false); // reconnectScheduled를 false로 변환
            // 만약 프로그램이 종료중이거나 구독중인 종목 목록이 비어있으면 연결을 하지 않는다.
            if (shutdown || activeSubscriptions.isEmpty()) {
                return;
            }

            try {
                ensureConnected(); // 연결 시도
            } catch (Exception e) {
                log.warn("KIS realtime websocket reconnect failed.", e);
                scheduleReconnect();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS); // 연결 해제 후 3초뒤에 실행하는 메서드
    }

    // 스프링 애플리케이션이 종료될 때 호출되는 메서드
    @PreDestroy
    public void close() {
        shutdown = true; // 스프링 애플리케이션이 종료될 때 shutdown 시그널을 true로 설정
        WebSocket currentWebSocket = this.webSocket;
        if (currentWebSocket != null) {
            // 스프링 애플리케이션이 종료될 때 웹소켓이 열려있으면 웹소켓을 종료한다.
            currentWebSocket.sendClose(WebSocket.NORMAL_CLOSURE, "application shutdown");
        }
        // 데몬으로 실행중이던 reconnectExecutor를 종료한다.
        reconnectExecutor.shutdownNow();
    }

    // WebSocket 관련 이벤트를 처리하는 내부 클래스
    // 이를 통해 WebSocket에서 데이터를 전달받고, 이를 처리할 수 있다.
    private class KisRealtimeWebSocketListener implements WebSocket.Listener {
        // WebSocket을 통해 데이터를 전달받을 때에는 데이터를 한번에 받는 것이 아니라, 여러번에 걸쳐서 받을 수 있다.
        // 따라서 여러번에 걸쳐 데이터를 받을때, 이를 연결하기 위한 StringBuilder 설정
        private final StringBuilder partialMessage = new StringBuilder();

        // WebSocket 연결이 열렸을때 호출되는 메서드
        // webSocket.request(1)은 다음 WebSocket 이벤트/메세지 1개를 받을 준비가 되었다는 신호를 보내는 메서드이다.
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        // KIS 서버에서 WebSocket을 통해 텍스트 메세지를 전달받으면 호출된다.
        // KIS 서버에서 WebSocket을 통해 실시간 데이터를 받으면, handlerTextMessage를 호출하여 이를 파싱하여 realtimeStore에 저장하고, 이벤트를 발생시켜, 해당 종목을 구독중인 클라이언트들에게 데이터 전달
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data); // 메세지가 여러번에 걸쳐서 전송될 수 있기 때문에, StringBuilder를 통해서 메세지를 연결한다.
            if (last) { // 마지막 메세지를 받으면, 이를 raw Message로 변환하고, handleTextMessage를 호출하여 파싱 및 데이터 저장을 수행한다.
                String rawMessage = partialMessage.toString();
                partialMessage.setLength(0);
                handleTextMessage(webSocket, rawMessage);
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        // WebSocket 표준 ping frame이 들어오면 실행되며, 이경우 webSocket으로 pong 응답 데이터를 전송한다.
        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return webSocket.sendPong(message);
        }

        // WebSocket 연결이 정상적으로 닫히거나, 서버쪽에서 닫으면 실행된다.
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            // 이때 markDisconnected를 호출해 연결상태를 모두 false로 변환하고, 필요하면 재연결을 예약한다.
            markDisconnected(statusCode + ":" + reason, null);
            return CompletableFuture.completedFuture(null);
        }

        // WebSocket 처리 중 에러가 발생하면 호출된다.
        // 이떄 예외가 발생하면 markDisconnected를 호출해 연결상태를 모두 false로 변환하고, 필요하면 재연결을 예약한다.
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            markDisconnected("error", error);
        }
    }
}
