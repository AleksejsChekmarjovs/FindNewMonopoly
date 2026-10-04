package com.example.monopoly.lobby;

import com.example.monopoly.engine.Game;
import com.example.monopoly.engine.GameException;
import com.example.monopoly.stats.StatsService;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Хранит комнаты в памяти. Команды для одной комнаты выполняются строго по одной
 * (synchronized на объекте комнаты) — так два быстрых клика не приведут к двойной покупке.
 * Закончившаяся партия один раз записывается в статистику.
 */
@Service
public class LobbyService {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final StatsService stats;
    private final Clock clock;

    public LobbyService(StatsService stats, Clock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    public Room create() {
        String id;
        do {
            id = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        } while (rooms.putIfAbsent(id, new Room(id)) != null);
        return rooms.get(id);
    }

    public Collection<Room> rooms() {
        return rooms.values();
    }

    public Room room(String id) {
        Room room = rooms.get(id);
        if (room == null) {
            throw new GameException("Комната " + id + " не найдена");
        }
        return room;
    }

    public Room.Seat join(Room room, long accountId, String name) {
        synchronized (room) {
            return room.join(accountId, name);
        }
    }

    public Room.Seat resume(Room room, String token) {
        if (token == null || token.isBlank()) {
            throw new GameException("Нет токена");
        }
        synchronized (room) {
            return room.seatByToken(token);
        }
    }

    public void start(Room room, String playerId) {
        synchronized (room) {
            room.start(playerId, clock.instant());
        }
    }

    public void act(Room room, Consumer<Game> action) {
        synchronized (room) {
            if (room.game() == null) {
                throw new GameException("Игра ещё не началась");
            }
            action.accept(room.game());
            recordIfFinished(room);
        }
    }

    /** Сроки ходов и ответов. true — состояние изменилось. */
    public boolean tick(Room room) {
        synchronized (room) {
            if (room.game() == null || !room.game().tick()) {
                return false;
            }
            recordIfFinished(room);
            return true;
        }
    }

    /** Сбой записи статистики не должен ломать саму игру — только пишем в лог. */
    private void recordIfFinished(Room room) {
        room.takeResult().ifPresent(standings -> {
            try {
                stats.recordFinishedGame(room.id(), room.startedAt(), standings);
            } catch (RuntimeException e) {
                log.error("Не удалось записать итог партии в комнате {}", room.id(), e);
            }
        });
    }
}
