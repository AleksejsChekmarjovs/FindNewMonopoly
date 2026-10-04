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

    /**
     * Место игрока.
     *
     * @param playerId публичный id — его видят все в состоянии игры
     * @param accountId аккаунт игрока: у аккаунта одно место в комнате
     * @param token секрет для возвращения в комнату после обрыва; знает только сам игрок
     */
    public record Seat(String playerId, long accountId, String name, String token) {
    }

    /** То, что можно показывать другим игрокам: без токена. */
    public record PublicSeat(String playerId, String name) {
    }

    private final String id;
    private final List<Seat> seats = new ArrayList<>();
    private Game game;

    Room(String id) {
        this.id = id;
    }

    /** Войти в комнату. Уже есть место у этого аккаунта — возвращаем его (в том числе после старта). */
    Seat join(long accountId, String name) {
        for (Seat seat : seats) {
            if (seat.accountId() == accountId) {
                return seat;
            }
        }
        if (game != null) {
            throw new GameException("Игра уже началась");
        }
        if (seats.size() >= 8) {
            throw new GameException("Комната заполнена");
        }
        Seat seat = new Seat(UUID.randomUUID().toString(), accountId, name, UUID.randomUUID().toString());
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

    Seat seatByToken(String token) {
        return seats.stream()
                .filter(s -> s.token().equals(token))
                .findFirst()
                .orElseThrow(() -> new GameException("Место в комнате не найдено"));
    }

    public String id() { return id; }
    public List<PublicSeat> publicSeats() {
        return seats.stream().map(s -> new PublicSeat(s.playerId(), s.name())).toList();
    }
    public Game game() { return game; }
}
