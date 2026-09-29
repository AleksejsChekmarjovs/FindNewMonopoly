package com.example.monopoly.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Переподключение через настоящий WebSocket: выйти, вернуться по токену, вытеснить старую вкладку. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReconnectTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private final java.util.List<Client> clients = new java.util.ArrayList<>();

    @AfterEach
    void closeAll() throws Exception {
        for (Client c : clients) {
            if (c.session.isOpen()) {
                c.session.close();
            }
        }
    }

    /** Тестовый клиент: складывает входящие сообщения в очередь. */
    class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            inbox.add(json.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closed.complete(status);
        }

        void send(Map<String, Object> msg) throws Exception {
            session.sendMessage(new TextMessage(json.writeValueAsString(msg)));
        }

        /** Ждём сообщение, подходящее под условие; прочие пропускаем. */
        JsonNode await(Predicate<JsonNode> condition) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                JsonNode m = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && condition.test(m)) {
                    return m;
                }
            }
            throw new AssertionError("Сообщение не пришло");
        }

        JsonNode await(String type) throws InterruptedException {
            return await(m -> type.equals(m.path("type").asText()));
        }
    }

    private Client connect() throws Exception {
        Client c = new Client();
        c.session = new StandardWebSocketClient()
                .execute(c, "ws://localhost:" + port + "/ws")
                .get(5, TimeUnit.SECONDS);
        clients.add(c);
        return c;
    }

    @Test
    void playerReturnsToStartedGameWithToken() throws Exception {
        Client alice = connect();
        alice.send(Map.of("type", "CREATE", "name", "Alice"));
        JsonNode aliceWelcome = alice.await("WELCOME");
        String roomId = aliceWelcome.get("roomId").asText();

        Client bob = connect();
        bob.send(Map.of("type", "JOIN", "roomId", roomId, "name", "Bob"));
        JsonNode bobWelcome = bob.await("WELCOME");
        String bobId = bobWelcome.get("playerId").asText();
        String bobToken = bobWelcome.get("token").asText();

        alice.send(Map.of("type", "START"));
        alice.await("STATE");

        // Bob пропал — Alice видит, что его нет в сети
        bob.session.close();
        alice.await(m -> "STATE".equals(m.path("type").asText()) && m.get("online").size() == 1);

        // Bob вернулся с новым соединением по токену — то же место, та же игра
        Client bobAgain = connect();
        bobAgain.send(Map.of("type", "RESUME", "roomId", roomId, "token", bobToken));
        JsonNode welcome = bobAgain.await("WELCOME");
        assertThat(welcome.get("playerId").asText()).isEqualTo(bobId);
        JsonNode state = bobAgain.await("STATE");
        assertThat(state.get("game").get("players").get(1).get("id").asText()).isEqualTo(bobId);
        assertThat(state.get("online").size()).isEqualTo(2);
    }

    @Test
    void newConnectionReplacesOldOne() throws Exception {
        Client alice = connect();
        alice.send(Map.of("type", "CREATE", "name", "Alice"));
        JsonNode welcome = alice.await("WELCOME");

        Client secondTab = connect();
        secondTab.send(Map.of("type", "RESUME",
                "roomId", welcome.get("roomId").asText(), "token", welcome.get("token").asText()));
        secondTab.await("WELCOME");

        CloseStatus status = alice.closed.get(5, TimeUnit.SECONDS);
        assertThat(status.getCode()).isEqualTo(GameSocketHandler.CLOSE_OPENED_ELSEWHERE.getCode());
        JsonNode lobby = secondTab.await("LOBBY");
        assertThat(lobby.get("players").size()).isEqualTo(1); // место не задвоилось
    }

    @Test
    void wrongTokenOrRoomFailsCleanly() throws Exception {
        Client alice = connect();
        alice.send(Map.of("type", "CREATE", "name", "Alice"));
        String roomId = alice.await("WELCOME").get("roomId").asText();

        Client stranger = connect();
        stranger.send(Map.of("type", "RESUME", "roomId", roomId, "token", "guess"));
        stranger.await("RESUME_FAILED");

        stranger.send(Map.of("type", "RESUME", "roomId", "000000", "token", "guess"));
        stranger.await("RESUME_FAILED");
    }

    @Test
    void tokensAreNeverSentToOtherPlayers() throws Exception {
        Client alice = connect();
        alice.send(Map.of("type", "CREATE", "name", "Alice"));
        JsonNode welcome = alice.await("WELCOME");
        String roomId = welcome.get("roomId").asText();

        Client bob = connect();
        bob.send(Map.of("type", "JOIN", "roomId", roomId, "name", "Bob"));
        bob.await("WELCOME");
        JsonNode lobby = bob.await("LOBBY");

        assertThat(lobby.toString()).doesNotContain(welcome.get("token").asText());
        assertThat(lobby.get("players").get(0).has("token")).isFalse();
    }
}
