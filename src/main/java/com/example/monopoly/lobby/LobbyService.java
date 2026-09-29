package com.example.monopoly.lobby;

import com.example.monopoly.engine.Game;
import com.example.monopoly.engine.GameException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Хранит комнаты в памяти. Команды для одной комнаты выполняются строго по одной
 * (synchronized на объекте комнаты) — так два быстрых клика не приведут к двойной покупке.
 */
@Service
public class LobbyService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

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

    public Room.Seat join(Room room, String name) {
        synchronized (room) {
            return room.join(name);
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
            room.start(playerId);
        }
    }

    public void act(Room room, Consumer<Game> action) {
        synchronized (room) {
            if (room.game() == null) {
                throw new GameException("Игра ещё не началась");
            }
            action.accept(room.game());
        }
    }
}
