package com.example.monopoly.ws;

import com.example.monopoly.engine.GameException;
import com.example.monopoly.engine.GameView;
import com.example.monopoly.lobby.LobbyService;
import com.example.monopoly.lobby.Room;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Один WebSocket-эндпоинт /ws. Клиент шлёт {@link ClientMessage}, сервер рассылает
 * всем в комнате полное состояние (LOBBY или STATE). Ошибки уходят только отправителю.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameSocketHandler.class);

    /** Кто за каким соединением сидит. */
    private record Connection(WebSocketSession session, String roomId, String playerId) {
    }

    private final LobbyService lobby;
    private final ObjectMapper json;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public GameSocketHandler(LobbyService lobby, ObjectMapper json) {
        this.lobby = lobby;
        this.json = json;
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws IOException {
        try {
            ClientMessage msg = json.readValue(message.getPayload(), ClientMessage.class);
            switch (msg.type()) {
                case "CREATE" -> enter(raw, lobby.create(), msg.name());
                case "JOIN" -> enter(raw, lobby.room(msg.roomId()), msg.name());
                default -> handleInRoom(raw, msg);
            }
        } catch (GameException e) {
            send(sessionFor(raw), Map.of("type", "ERROR", "message", e.getMessage()));
        } catch (Exception e) {
            log.warn("Bad message {}", message.getPayload(), e);
            send(sessionFor(raw), Map.of("type", "ERROR", "message", "Некорректное сообщение"));
        }
    }

    /** После входа в комнату пишем только через потокобезопасную обёртку сессии. */
    private WebSocketSession sessionFor(WebSocketSession raw) {
        Connection c = connections.get(raw.getId());
        return c != null ? c.session() : raw;
    }

    private void enter(WebSocketSession raw, Room room, String name) {
        if (connections.containsKey(raw.getId())) {
            throw new GameException("Вы уже в комнате");
        }
        if (name == null || name.isBlank()) {
            throw new GameException("Укажите имя");
        }
        Room.Seat seat = lobby.join(room, name.strip());
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 64 * 1024);
        connections.put(raw.getId(), new Connection(session, room.id(), seat.playerId()));
        send(session, Map.of("type", "WELCOME", "roomId", room.id(), "playerId", seat.playerId()));
        broadcast(room);
    }

    private void handleInRoom(WebSocketSession raw, ClientMessage msg) {
        Connection c = connections.get(raw.getId());
        if (c == null) {
            throw new GameException("Сначала создайте комнату или войдите в неё");
        }
        Room room = lobby.room(c.roomId());
        String pid = c.playerId();
        switch (msg.type()) {
            case "START" -> lobby.start(room, pid);
            case "ROLL" -> lobby.act(room, g -> g.roll(pid));
            case "BUY" -> lobby.act(room, g -> g.buy(pid));
            case "DECLINE" -> lobby.act(room, g -> g.declineBuy(pid));
            case "PAY_JAIL_FINE" -> lobby.act(room, g -> g.payJailFine(pid));
            case "USE_JAIL_CARD" -> lobby.act(room, g -> g.useJailFreeCard(pid));
            case "BID" -> {
                if (msg.amount() == null) {
                    throw new GameException("Укажите сумму ставки");
                }
                lobby.act(room, g -> g.bid(pid, msg.amount()));
            }
            case "PASS" -> lobby.act(room, g -> g.passAuction(pid));
            case "BUILD" -> lobby.act(room, g -> g.buildHouse(pid, requireTile(msg)));
            case "SELL_HOUSE" -> lobby.act(room, g -> g.sellHouse(pid, requireTile(msg)));
            case "MORTGAGE" -> lobby.act(room, g -> g.mortgage(pid, requireTile(msg)));
            case "UNMORTGAGE" -> lobby.act(room, g -> g.unmortgage(pid, requireTile(msg)));
            case "PAY_DEBT" -> lobby.act(room, g -> g.payDebt(pid));
            case "DECLARE_BANKRUPTCY" -> lobby.act(room, g -> g.declareBankruptcy(pid));
            case "PROPOSE_TRADE" -> {
                if (msg.trade() == null || msg.trade().toId() == null) {
                    throw new GameException("Укажите, с кем и что меняете");
                }
                lobby.act(room, g -> g.proposeTrade(msg.trade().toOffer(pid)));
            }
            case "ACCEPT_TRADE" -> lobby.act(room, g -> g.acceptTrade(pid));
            case "REJECT_TRADE" -> lobby.act(room, g -> g.rejectTrade(pid));
            case "CANCEL_TRADE" -> lobby.act(room, g -> g.cancelTrade(pid));
            case "END_TURN" -> lobby.act(room, g -> g.endTurn(pid));
            default -> throw new GameException("Неизвестная команда " + msg.type());
        }
        broadcast(room);
    }

    private static int requireTile(ClientMessage msg) {
        if (msg.tileIndex() == null) {
            throw new GameException("Укажите клетку");
        }
        return msg.tileIndex();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // TODO: переподключение игрока (сейчас после обрыва место в игре остаётся без хозяина)
        connections.remove(session.getId());
    }

    /** Раз в секунду: сроки ходов, ответов на обмен, ставок и долгов. Изменилось — рассылаем состояние. */
    @Scheduled(fixedRate = 1000)
    public void tick() {
        for (Room room : lobby.rooms()) {
            boolean changed;
            synchronized (room) {
                changed = room.game() != null && room.game().tick();
            }
            if (changed) {
                broadcast(room);
            }
        }
    }

    private void broadcast(Room room) {
        Object payload;
        synchronized (room) {
            payload = room.game() == null
                    ? Map.of("type", "LOBBY", "roomId", room.id(), "players", room.seats())
                    : Map.of("type", "STATE", "game", GameView.of(room.game()));
        }
        connections.values().stream()
                .filter(c -> c.roomId().equals(room.id()))
                .forEach(c -> send(c.session(), payload));
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        } catch (Exception e) {
            log.debug("Send failed to {}", session.getId(), e);
        }
    }
}
