package com.example.monopoly.lobby;

import com.example.monopoly.engine.Dice;
import com.example.monopoly.engine.Game;
import com.example.monopoly.engine.GameException;
import com.example.monopoly.engine.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Игровая комната: лобби до старта и сама партия после. Все методы вызываются под локом комнаты. */
public class Room {

    public record Seat(String playerId, String name) {
    }

    private final String id;
    private final List<Seat> seats = new ArrayList<>();
    private Game game;

    Room(String id) {
        this.id = id;
    }

    Seat join(String name) {
        if (game != null) {
            throw new GameException("Игра уже началась");
        }
        if (seats.size() >= 8) {
            throw new GameException("Комната заполнена");
        }
        Seat seat = new Seat(UUID.randomUUID().toString(), name);
        seats.add(seat);
        return seat;
    }

    void start(String playerId) {
        if (game != null) {
            throw new GameException("Игра уже началась");
        }
        if (seats.isEmpty() || !seats.get(0).playerId().equals(playerId)) {
            throw new GameException("Начать игру может только создатель комнаты");
        }
        List<Player> players = seats.stream()
                .map(s -> new Player(s.playerId(), s.name(), Game.START_MONEY))
                .toList();
        game = new Game(players, Dice.random());
    }

    public String id() { return id; }
    public List<Seat> seats() { return List.copyOf(seats); }
    public Game game() { return game; }
}
