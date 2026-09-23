package com.example.monopoly.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние одной партии и все правила. Класс НЕ потокобезопасен:
 * вызывающий код обязан сериализовать команды для одной игры.
 */
public class Game {

    public static final int START_MONEY = 1500;
    public static final int GO_SALARY = 200;
    public static final int JAIL_FINE = 50;
    private static final int MAX_LOG = 50;

    private final Board board = new Board();
    private final Dice dice;
    private final List<Player> players;
    /** индекс клетки -> id владельца */
    private final Map<Integer, String> owners = new HashMap<>();
    private final Deque<String> log = new ArrayDeque<>();
    private final Deck chance;
    private final Deck communityChest;

    private int currentIndex = 0;
    private TurnPhase phase = TurnPhase.WAITING_FOR_ROLL;
    private DiceRoll lastRoll;
    private Card lastCard;
    private int doublesInRow = 0;
    private boolean extraRoll = false;
    private String winnerId;

    /** Как считать аренду при попадании на клетку (карточки меняют правило). */
    private enum RentMode { NORMAL, DOUBLE, UTILITY_TEN_TIMES }

    public Game(List<Player> players, Dice dice) {
        this(players, dice,
                Deck.shuffled(Deck.chanceCards(new Board())),
                Deck.shuffled(Deck.communityChestCards()));
    }

    public Game(List<Player> players, Dice dice, Deck chance, Deck communityChest) {
        if (players.size() < 2 || players.size() > 8) {
            throw new GameException("Нужно от 2 до 8 игроков");
        }
        this.players = new ArrayList<>(players);
        this.dice = dice;
        this.chance = chance;
        this.communityChest = communityChest;
        log("Игра началась. Ходит " + current().name());
    }

    // ------------------------------------------------------------------ команды

    public void roll(String playerId) {
        Player p = requireTurn(playerId, TurnPhase.WAITING_FOR_ROLL);
        DiceRoll roll = dice.roll();
        lastRoll = roll;
        lastCard = null;
        extraRoll = false;
        log(p.name() + ": выпало " + roll.first() + "+" + roll.second());

        if (p.inJail()) {
            if (roll.isDouble()) {
                p.releaseFromJail();
                log(p.name() + " выходит из тюрьмы по дублю");
                // Выход по дублю не даёт дополнительного броска
            } else {
                p.incrementJailTurns();
                if (p.jailTurns() < 3) {
                    phase = TurnPhase.TURN_END;
                    return;
                }
                log(p.name() + " платит $" + JAIL_FINE + " после третьей попытки");
                pay(p, null, JAIL_FINE);
                if (p.bankrupt()) {
                    afterAction();
                    return;
                }
                p.releaseFromJail();
            }
        } else if (roll.isDouble()) {
            doublesInRow++;
            if (doublesInRow == 3) {
                log(p.name() + ": три дубля подряд — отправляется в тюрьму");
                sendToJail(p);
                afterAction();
                return;
            }
            extraRoll = true;
        }

        moveBy(p, roll.total());
        resolveLanding(p);
    }

    public void buy(String playerId) {
        Player p = requireTurn(playerId, TurnPhase.AWAITING_BUY_DECISION);
        Tile tile = board.tile(p.position());
        if (p.money() < tile.price()) {
            throw new GameException("Недостаточно денег");
        }
        p.addMoney(-tile.price());
        owners.put(tile.index(), p.id());
        log(p.name() + " покупает " + tile.name() + " за $" + tile.price());
        afterAction();
    }

    public void declineBuy(String playerId) {
        Player p = requireTurn(playerId, TurnPhase.AWAITING_BUY_DECISION);
        // TODO: по классическим правилам отказ от покупки запускает аукцион
        log(p.name() + " отказывается покупать " + board.tile(p.position()).name());
        afterAction();
    }

    public void payJailFine(String playerId) {
        Player p = requireTurn(playerId, TurnPhase.WAITING_FOR_ROLL);
        if (!p.inJail()) {
            throw new GameException("Вы не в тюрьме");
        }
        if (p.money() < JAIL_FINE) {
            throw new GameException("Недостаточно денег");
        }
        p.addMoney(-JAIL_FINE);
        p.releaseFromJail();
        log(p.name() + " платит $" + JAIL_FINE + " и выходит из тюрьмы");
    }

    public void useJailFreeCard(String playerId) {
        Player p = requireTurn(playerId, TurnPhase.WAITING_FOR_ROLL);
        if (!p.inJail()) {
            throw new GameException("Вы не в тюрьме");
        }
        if (p.jailFreeCards() == 0) {
            throw new GameException("Нет карточки «Освободиться из тюрьмы»");
        }
        deck(p.takeJailFreeCard()).returnJailFreeCard();
        p.releaseFromJail();
        log(p.name() + " использует карточку и выходит из тюрьмы");
    }

    public void endTurn(String playerId) {
        requireTurn(playerId, TurnPhase.TURN_END);
        nextPlayer();
    }

    // ------------------------------------------------------------------ правила

    private void moveBy(Player p, int steps) {
        moveTo(p, (p.position() + steps) % Board.SIZE);
    }

    /** Движение вперёд до клетки {@code to}; если путь идёт через «Старт» — $200. */
    private void moveTo(Player p, int to) {
        if (to < p.position()) {
            p.addMoney(GO_SALARY);
            log(p.name() + " проходит Старт и получает $" + GO_SALARY);
        }
        p.setPosition(to);
        log(p.name() + " попадает на " + board.tile(to).name());
    }

    private void resolveLanding(Player p) {
        resolveLanding(p, RentMode.NORMAL);
    }

    private void resolveLanding(Player p, RentMode rentMode) {
        Tile tile = board.tile(p.position());
        switch (tile.type()) {
            case PROPERTY, RAILROAD, UTILITY -> {
                String ownerId = owners.get(tile.index());
                if (ownerId == null) {
                    if (p.money() >= tile.price()) {
                        phase = TurnPhase.AWAITING_BUY_DECISION;
                        return; // ждём решения игрока
                    }
                    // TODO: аукцион
                    log("Недостаточно денег у " + p.name() + " на " + tile.name());
                } else if (!ownerId.equals(p.id())) {
                    Player owner = player(ownerId);
                    int rent = switch (rentMode) {
                        case NORMAL -> rentFor(tile);
                        case DOUBLE -> rentFor(tile) * 2;
                        case UTILITY_TEN_TIMES -> {
                            DiceRoll r = dice.roll();
                            log(p.name() + ": для аренды выпало " + r.first() + "+" + r.second());
                            yield 10 * r.total();
                        }
                    };
                    log(p.name() + " платит аренду $" + rent + " игроку " + owner.name());
                    pay(p, owner, rent);
                }
            }
            case TAX -> {
                log(p.name() + " платит налог $" + tile.taxAmount());
                pay(p, null, tile.taxAmount());
            }
            case GO_TO_JAIL -> {
                log(p.name() + " отправляется в тюрьму");
                sendToJail(p);
            }
            case CHANCE -> {
                applyCard(p, chance.draw());
                return; // applyCard сам завершает действие
            }
            case COMMUNITY_CHEST -> {
                applyCard(p, communityChest.draw());
                return;
            }
            case GO, JAIL, FREE_PARKING -> { }
        }
        afterAction();
    }

    private void applyCard(Player p, Card card) {
        lastCard = card;
        String deckName = card.deck() == DeckType.CHANCE ? "Шанс" : "Общественная казна";
        log(p.name() + " тянет «" + deckName + "»: " + card.text());

        switch (card.kind()) {
            case MOVE_TO -> {
                moveTo(p, card.amount());
                resolveLanding(p);
                return;
            }
            case MOVE_BACK -> {
                int to = Math.floorMod(p.position() - card.amount(), Board.SIZE);
                p.setPosition(to);
                log(p.name() + " попадает на " + board.tile(to).name());
                resolveLanding(p);
                return;
            }
            case NEAREST_RAILROAD -> {
                moveTo(p, nearest(p.position(), TileType.RAILROAD));
                resolveLanding(p, RentMode.DOUBLE);
                return;
            }
            case NEAREST_UTILITY -> {
                moveTo(p, nearest(p.position(), TileType.UTILITY));
                resolveLanding(p, RentMode.UTILITY_TEN_TIMES);
                return;
            }
            case GO_TO_JAIL -> sendToJail(p);
            case JAIL_FREE -> p.addJailFreeCard(card.deck());
            case GAIN -> p.addMoney(card.amount());
            case PAY -> pay(p, null, card.amount());
            case GAIN_FROM_EACH -> {
                for (Player other : players) {
                    if (other != p && !other.bankrupt()) {
                        pay(other, p, card.amount());
                    }
                }
            }
            case PAY_EACH -> {
                for (Player other : players) {
                    if (p.bankrupt()) {
                        break;
                    }
                    if (other != p && !other.bankrupt()) {
                        pay(p, other, card.amount());
                    }
                }
            }
            case REPAIRS -> {
                // TODO: считать дома и отели игрока, когда появится застройка
                int houses = 0;
                int hotels = 0;
                int cost = houses * card.amount() + hotels * card.amount2();
                if (cost > 0) {
                    pay(p, null, cost);
                }
            }
        }
        afterAction();
    }

    /** Ближайшая клетка заданного типа по ходу движения. */
    private int nearest(int from, TileType type) {
        for (int step = 1; step <= Board.SIZE; step++) {
            int i = (from + step) % Board.SIZE;
            if (board.tile(i).type() == type) {
                return i;
            }
        }
        throw new IllegalStateException("На поле нет клетки " + type);
    }

    private Deck deck(DeckType type) {
        return type == DeckType.CHANCE ? chance : communityChest;
    }

    int rentFor(Tile tile) {
        String ownerId = owners.get(tile.index());
        return switch (tile.type()) {
            case PROPERTY -> {
                int base = tile.rent().get(0);
                // TODO: дома/отели — tile.rent().get(houses)
                yield ownsWholeGroup(ownerId, tile.group()) ? base * 2 : base;
            }
            case RAILROAD -> {
                long count = countOwned(ownerId, TileType.RAILROAD);
                yield 25 * (1 << (count - 1));
            }
            case UTILITY -> {
                long count = countOwned(ownerId, TileType.UTILITY);
                yield (count == 2 ? 10 : 4) * lastRoll.total();
            }
            default -> 0;
        };
    }

    private boolean ownsWholeGroup(String ownerId, ColorGroup group) {
        return board.streetsOf(group).stream().allMatch(t -> ownerId.equals(owners.get(t.index())));
    }

    private long countOwned(String ownerId, TileType type) {
        return owners.entrySet().stream()
                .filter(e -> e.getValue().equals(ownerId) && board.tile(e.getKey()).type() == type)
                .count();
    }

    /** Перевод денег. {@code to == null} — платёж банку. Если денег не хватает — банкротство. */
    private void pay(Player from, Player to, int amount) {
        // TODO: вместо мгновенного банкротства дать игроку заложить/продать имущество
        if (from.money() < amount) {
            int rest = from.money();
            if (to != null) {
                to.addMoney(rest);
            }
            owners.replaceAll((tile, owner) -> owner.equals(from.id()) && to != null ? to.id() : owner);
            owners.values().removeIf(owner -> owner.equals(from.id()));
            while (from.jailFreeCards() > 0) {
                deck(from.takeJailFreeCard()).returnJailFreeCard();
            }
            from.setBankrupt();
            log(from.name() + " банкрот!");
            return;
        }
        from.addMoney(-amount);
        if (to != null) {
            to.addMoney(amount);
        }
    }

    private void sendToJail(Player p) {
        p.goToJail();
        extraRoll = false;
        doublesInRow = 0;
    }

    /** Действие на клетке завершено — решаем, что дальше. */
    private void afterAction() {
        List<Player> alive = players.stream().filter(pl -> !pl.bankrupt()).toList();
        if (alive.size() == 1) {
            winnerId = alive.get(0).id();
            phase = TurnPhase.GAME_OVER;
            log("Победитель: " + alive.get(0).name());
            return;
        }
        if (current().bankrupt()) {
            nextPlayer();
            return;
        }
        phase = extraRoll ? TurnPhase.WAITING_FOR_ROLL : TurnPhase.TURN_END;
    }

    private void nextPlayer() {
        doublesInRow = 0;
        extraRoll = false;
        do {
            currentIndex = (currentIndex + 1) % players.size();
        } while (current().bankrupt());
        phase = TurnPhase.WAITING_FOR_ROLL;
        log("Ходит " + current().name());
    }

    private Player requireTurn(String playerId, TurnPhase expected) {
        if (phase == TurnPhase.GAME_OVER) {
            throw new GameException("Игра окончена");
        }
        if (!current().id().equals(playerId)) {
            throw new GameException("Сейчас не ваш ход");
        }
        if (phase != expected) {
            throw new GameException("Действие недоступно в фазе " + phase);
        }
        return current();
    }

    private void log(String message) {
        log.addLast(message);
        while (log.size() > MAX_LOG) {
            log.removeFirst();
        }
    }

    // ------------------------------------------------------------------ чтение состояния

    public Player current() { return players.get(currentIndex); }
    public TurnPhase phase() { return phase; }
    public Board board() { return board; }
    public List<Player> players() { return List.copyOf(players); }
    public Map<Integer, String> owners() { return Map.copyOf(owners); }
    public DiceRoll lastRoll() { return lastRoll; }
    public Card lastCard() { return lastCard; }
    public String winnerId() { return winnerId; }
    public List<String> log() { return List.copyOf(log); }

    public Player player(String id) {
        return players.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new GameException("Нет игрока " + id));
    }
}
