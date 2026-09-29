package com.example.monopoly.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Состояние одной партии и все правила. Класс НЕ потокобезопасен:
 * вызывающий код обязан сериализовать команды для одной игры.
 */
public class Game {

    public static final int START_MONEY = 1500;
    public static final int GO_SALARY = 200;
    public static final int JAIL_FINE = 50;
    /** Уровень застройки: 1–4 — дома, 5 — отель. */
    public static final int HOTEL = 5;
    public static final int BANK_HOUSES = 32;
    public static final int BANK_HOTELS = 12;
    private static final int MAX_LOG = 50;

    private final Board board = new Board();
    private final Dice dice;
    private final List<Player> players;
    /** индекс клетки -> id владельца */
    private final Map<Integer, String> owners = new HashMap<>();
    /** индекс улицы -> уровень застройки (нет записи — пусто) */
    private final Map<Integer, Integer> buildings = new HashMap<>();
    private int housesInBank = BANK_HOUSES;
    private int hotelsInBank = BANK_HOTELS;
    /** заложенные клетки */
    private final Set<Integer> mortgaged = new HashSet<>();
    private final Deque<String> log = new ArrayDeque<>();
    private final Deck chance;
    private final Deck communityChest;

    private int currentIndex = 0;
    private TurnPhase phase = TurnPhase.WAITING_FOR_ROLL;
    private DiceRoll lastRoll;
    private Card lastCard;
    private Auction auction;
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
                // Третья неудачная попытка: штраф обязателен, и только после него — ход
                log(p.name() + " должен заплатить $" + JAIL_FINE + " после третьей попытки");
                int steps = roll.total();
                Charge result = charge(p, null, JAIL_FINE, () -> {
                    p.releaseFromJail();
                    moveBy(p, steps);
                    resolveLanding(p);
                });
                if (result != Charge.PAID) {
                    afterAction();
                }
                return;
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
        log(p.name() + " отказывается покупать " + board.tile(p.position()).name());
        startAuction(board.tile(p.position()));
    }

    public void bid(String playerId, int amount) {
        Player p = requireBidder(playerId);
        if (amount <= auction.highestBid()) {
            throw new GameException("Ставка должна быть больше $" + auction.highestBid());
        }
        if (amount > p.money()) {
            throw new GameException("Недостаточно денег для такой ставки");
        }
        auction.bid(playerId, amount);
        log(p.name() + " ставит $" + amount);
        finishAuctionIfDone();
    }

    public void passAuction(String playerId) {
        Player p = requireBidder(playerId);
        auction.pass(playerId);
        log(p.name() + " пасует");
        finishAuctionIfDone();
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

    public void buildHouse(String playerId, int tileIndex) {
        Player p = requireManagementTime(playerId, false);
        Tile tile = requireOwnStreet(p, tileIndex);
        if (!ownsWholeGroup(p.id(), tile.group())) {
            throw new GameException("Сначала соберите все улицы этого цвета");
        }
        if (board.streetsOf(tile.group()).stream().anyMatch(t -> mortgaged.contains(t.index()))) {
            throw new GameException("Сначала выкупите заложенные улицы этого цвета");
        }
        int level = level(tileIndex);
        if (level == HOTEL) {
            throw new GameException("На этой улице уже стоит отель");
        }
        if (level > minLevel(tile.group())) {
            throw new GameException("Стройте равномерно: сначала добавьте дома на другие улицы этого цвета");
        }
        int cost = tile.group().houseCost();
        if (p.money() < cost) {
            throw new GameException("Недостаточно денег");
        }
        if (level == 4) {
            if (hotelsInBank == 0) {
                throw new GameException("В банке закончились отели");
            }
            hotelsInBank--;
            housesInBank += 4;
        } else {
            if (housesInBank == 0) {
                throw new GameException("В банке закончились дома");
            }
            housesInBank--;
        }
        p.addMoney(-cost);
        buildings.put(tileIndex, level + 1);
        log(p.name() + (level == 4 ? " строит отель на " : " строит дом на ") + tile.name() + " за $" + cost);
    }

    public void sellHouse(String playerId, int tileIndex) {
        Player p = requireManagementTime(playerId, true);
        Tile tile = requireOwnStreet(p, tileIndex);
        int level = level(tileIndex);
        if (level == 0) {
            throw new GameException("На этой улице нет построек");
        }
        if (level < maxLevel(tile.group())) {
            throw new GameException("Продавайте равномерно: сначала продайте дома с других улиц этого цвета");
        }
        if (level == HOTEL) {
            // Отель меняется обратно на 4 дома — они должны быть в банке
            if (housesInBank < 4) {
                throw new GameException("В банке не хватает домов, чтобы разменять отель");
            }
            housesInBank -= 4;
            hotelsInBank++;
        } else {
            housesInBank++;
        }
        int refund = tile.group().houseCost() / 2;
        p.addMoney(refund);
        setLevel(tileIndex, level - 1);
        log(p.name() + (level == HOTEL ? " продаёт отель на " : " продаёт дом на ") + tile.name() + " за $" + refund);
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
                    // Даже если денег не хватает, игрок может заложить имущество и купить — или отказаться (аукцион)
                    phase = TurnPhase.AWAITING_BUY_DECISION;
                    return;
                } else if (!ownerId.equals(p.id()) && mortgaged.contains(tile.index())) {
                    log(tile.name() + " заложена — аренда не платится");
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
                int houses = 0;
                int hotels = 0;
                for (int i : ownedBy(p.id())) {
                    int level = level(i);
                    if (level == HOTEL) {
                        hotels++;
                    } else {
                        houses += level;
                    }
                }
                int cost = houses * card.amount() + hotels * card.amount2();
                if (cost > 0) {
                    log(p.name() + " платит за ремонт $" + cost);
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

    /** Торги начинает игрок после текущего; сам текущий тоже участвует, он последний в круге. */
    private void startAuction(Tile tile) {
        List<String> bidders = new ArrayList<>();
        for (int i = 1; i <= players.size(); i++) {
            Player pl = players.get((currentIndex + i) % players.size());
            if (!pl.bankrupt()) {
                bidders.add(pl.id());
            }
        }
        auction = new Auction(tile.index(), bidders);
        phase = TurnPhase.AUCTION;
        log("Аукцион: " + tile.name() + ". Ставку делает " + player(auction.currentBidderId()).name());
    }

    private void finishAuctionIfDone() {
        if (!auction.finished()) {
            return;
        }
        Tile tile = board.tile(auction.tileIndex());
        if (auction.highestBidderId() == null) {
            log("Никто не сделал ставку, " + tile.name() + " остаётся у банка");
        } else {
            Player winner = player(auction.highestBidderId());
            winner.addMoney(-auction.highestBid());
            owners.put(tile.index(), winner.id());
            log(winner.name() + " выигрывает аукцион: " + tile.name() + " за $" + auction.highestBid());
        }
        auction = null;
        afterAction();
    }

    private Player requireBidder(String playerId) {
        if (phase != TurnPhase.AUCTION) {
            throw new GameException("Сейчас нет аукциона");
        }
        if (!auction.currentBidderId().equals(playerId)) {
            throw new GameException("Сейчас не ваша очередь торговаться");
        }
        return player(playerId);
    }

    private Deck deck(DeckType type) {
        return type == DeckType.CHANCE ? chance : communityChest;
    }

    // ------------------------------------------------------------------ обмен

    private TradeOffer trade;
    private TurnPhase phaseBeforeTrade;

    /** Предложить обмен может текущий игрок в свой ход — до броска или перед завершением хода. */
    public void proposeTrade(TradeOffer offer) {
        Player from = player(offer.fromId());
        if (phase == TurnPhase.GAME_OVER) {
            throw new GameException("Игра окончена");
        }
        if (!current().id().equals(from.id())) {
            throw new GameException("Предлагать обмен можно только в свой ход");
        }
        if (phase != TurnPhase.WAITING_FOR_ROLL && phase != TurnPhase.TURN_END) {
            throw new GameException("Предлагать обмен можно до броска кубиков или перед завершением хода");
        }
        validateTrade(offer);
        trade = offer;
        phaseBeforeTrade = phase;
        phase = TurnPhase.TRADE_OFFER;
        log(from.name() + " предлагает обмен игроку " + player(offer.toId()).name() + ": " + describe(offer));
    }

    public void acceptTrade(String playerId) {
        requireTrade(playerId, true);
        TradeOffer offer = trade;
        validateTrade(offer); // с момента предложения всё могло измениться
        Player from = player(offer.fromId());
        Player to = player(offer.toId());

        // Получивший заложенную клетку сразу платит банку 10% от залога
        int fromFee = mortgageTransferFee(offer.takeTiles());
        int toFee = mortgageTransferFee(offer.giveTiles());
        if (from.money() - offer.giveMoney() + offer.takeMoney() < fromFee) {
            throw new GameException(from.name() + " не хватает $" + fromFee + " на 10% за заложенные клетки");
        }
        if (to.money() - offer.takeMoney() + offer.giveMoney() < toFee) {
            throw new GameException(to.name() + " не хватает $" + toFee + " на 10% за заложенные клетки");
        }

        offer.giveTiles().forEach(i -> owners.put(i, to.id()));
        offer.takeTiles().forEach(i -> owners.put(i, from.id()));
        transfer(from, to, offer.giveMoney());
        transfer(to, from, offer.takeMoney());
        for (int i = 0; i < offer.giveJailCards(); i++) {
            to.addJailFreeCard(from.takeJailFreeCard());
        }
        for (int i = 0; i < offer.takeJailCards(); i++) {
            from.addJailFreeCard(to.takeJailFreeCard());
        }
        transfer(from, null, fromFee);
        transfer(to, null, toFee);

        log(to.name() + " принимает обмен");
        if (fromFee + toFee > 0) {
            log("Проценты за заложенные клетки: " + (fromFee > 0 ? from.name() + " $" + fromFee + " " : "")
                    + (toFee > 0 ? to.name() + " $" + toFee : ""));
        }
        closeTrade();
    }

    public void rejectTrade(String playerId) {
        requireTrade(playerId, true);
        log(player(playerId).name() + " отклоняет обмен");
        closeTrade();
    }

    public void cancelTrade(String playerId) {
        requireTrade(playerId, false);
        log(player(playerId).name() + " отзывает предложение обмена");
        closeTrade();
    }

    private void closeTrade() {
        trade = null;
        phase = phaseBeforeTrade;
    }

    /** @param recipient true — действие адресата (принять/отклонить), false — автора (отозвать) */
    private void requireTrade(String playerId, boolean recipient) {
        if (phase != TurnPhase.TRADE_OFFER) {
            throw new GameException("Сейчас нет предложения обмена");
        }
        String expected = recipient ? trade.toId() : trade.fromId();
        if (!expected.equals(playerId)) {
            throw new GameException(recipient ? "Это предложение адресовано не вам" : "Это не ваше предложение");
        }
    }

    private void validateTrade(TradeOffer offer) {
        Player from = player(offer.fromId());
        Player to = player(offer.toId());
        if (from == to) {
            throw new GameException("Нельзя меняться с самим собой");
        }
        if (to.bankrupt()) {
            throw new GameException(to.name() + " выбыл из игры");
        }
        if (offer.isEmpty()) {
            throw new GameException("Пустое предложение");
        }
        if (offer.giveMoney() < 0 || offer.takeMoney() < 0 || offer.giveJailCards() < 0 || offer.takeJailCards() < 0) {
            throw new GameException("Суммы не могут быть отрицательными");
        }
        if (offer.giveMoney() > from.money()) {
            throw new GameException("У " + from.name() + " нет $" + offer.giveMoney());
        }
        if (offer.takeMoney() > to.money()) {
            throw new GameException("У " + to.name() + " нет $" + offer.takeMoney());
        }
        if (offer.giveJailCards() > from.jailFreeCards() || offer.takeJailCards() > to.jailFreeCards()) {
            throw new GameException("Нет столько карточек «Освободиться из тюрьмы»");
        }
        validateTradeTiles(offer.giveTiles(), from);
        validateTradeTiles(offer.takeTiles(), to);
    }

    private void validateTradeTiles(List<Integer> tiles, Player owner) {
        if (tiles.stream().distinct().count() != tiles.size()) {
            throw new GameException("Клетка указана дважды");
        }
        for (int i : tiles) {
            if (i < 0 || i >= Board.SIZE || !board.tile(i).type().isOwnable()) {
                throw new GameException("Эту клетку нельзя обменять");
            }
            Tile tile = board.tile(i);
            if (!owner.id().equals(owners.get(i))) {
                throw new GameException(tile.name() + " не принадлежит игроку " + owner.name());
            }
            if (tile.group() != null && maxLevel(tile.group()) > 0) {
                throw new GameException(tile.name() + ": сначала продайте постройки на улицах этого цвета");
            }
        }
    }

    /** 10% от залога за каждую полученную заложенную клетку (с округлением вверх, как при выкупе). */
    private int mortgageTransferFee(List<Integer> tiles) {
        return tiles.stream()
                .filter(mortgaged::contains)
                .mapToInt(i -> unmortgageCost(board.tile(i)) - mortgageValue(board.tile(i)))
                .sum();
    }

    private String describe(TradeOffer offer) {
        return "отдаёт " + describeSide(offer.giveTiles(), offer.giveMoney(), offer.giveJailCards())
                + ", получает " + describeSide(offer.takeTiles(), offer.takeMoney(), offer.takeJailCards());
    }

    private String describeSide(List<Integer> tiles, int money, int jailCards) {
        List<String> parts = new ArrayList<>();
        tiles.forEach(i -> parts.add(board.tile(i).name()));
        if (money > 0) {
            parts.add("$" + money);
        }
        if (jailCards > 0) {
            parts.add("карточек выхода из тюрьмы: " + jailCards);
        }
        return parts.isEmpty() ? "ничего" : String.join(", ", parts);
    }

    int rentFor(Tile tile) {
        String ownerId = owners.get(tile.index());
        return switch (tile.type()) {
            case PROPERTY -> {
                int level = level(tile.index());
                if (level > 0) {
                    yield tile.rent().get(level);
                }
                int base = tile.rent().get(0);
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

    /** Только для тестов: передать клетку игроку без покупки. */
    void setOwner(int tileIndex, String playerId) {
        owners.put(tileIndex, playerId);
    }

    private List<Integer> ownedBy(String playerId) {
        return owners.entrySet().stream()
                .filter(e -> e.getValue().equals(playerId))
                .map(Map.Entry::getKey)
                .toList();
    }

    // ------------------------------------------------------------------ застройка

    private int level(int tileIndex) {
        return buildings.getOrDefault(tileIndex, 0);
    }

    private void setLevel(int tileIndex, int level) {
        if (level == 0) {
            buildings.remove(tileIndex);
        } else {
            buildings.put(tileIndex, level);
        }
    }

    private int minLevel(ColorGroup group) {
        return board.streetsOf(group).stream().mapToInt(t -> level(t.index())).min().orElse(0);
    }

    private int maxLevel(ColorGroup group) {
        return board.streetsOf(group).stream().mapToInt(t -> level(t.index())).max().orElse(0);
    }

    /**
     * Строить, продавать и закладывать можно в свой ход, кроме аукциона.
     * Во время долга — только должнику и только чтобы собрать деньги (продать, заложить).
     */
    private Player requireManagementTime(String playerId, boolean raisesMoney) {
        if (phase == TurnPhase.GAME_OVER) {
            throw new GameException("Игра окончена");
        }
        if (phase == TurnPhase.PAYING_DEBT) {
            if (!debts.peekFirst().debt().debtorId().equals(playerId)) {
                throw new GameException("Сейчас расплачивается другой игрок");
            }
            if (!raisesMoney) {
                throw new GameException("Сначала расплатитесь с долгом");
            }
            return player(playerId);
        }
        if (!current().id().equals(playerId)) {
            throw new GameException("Сейчас не ваш ход");
        }
        if (phase == TurnPhase.AUCTION) {
            throw new GameException("Во время аукциона нельзя управлять имуществом");
        }
        if (phase == TurnPhase.TRADE_OFFER) {
            throw new GameException("Дождитесь ответа на предложение обмена");
        }
        return current();
    }

    public void mortgage(String playerId, int tileIndex) {
        Player p = requireManagementTime(playerId, true);
        Tile tile = requireOwnOwnable(p, tileIndex);
        if (mortgaged.contains(tileIndex)) {
            throw new GameException(tile.name() + " уже заложена");
        }
        if (tile.group() != null && maxLevel(tile.group()) > 0) {
            throw new GameException("Сначала продайте все постройки на улицах этого цвета");
        }
        mortgaged.add(tileIndex);
        p.addMoney(mortgageValue(tile));
        log(p.name() + " закладывает " + tile.name() + " за $" + mortgageValue(tile));
    }

    public void unmortgage(String playerId, int tileIndex) {
        Player p = requireManagementTime(playerId, false);
        Tile tile = requireOwnOwnable(p, tileIndex);
        if (!mortgaged.contains(tileIndex)) {
            throw new GameException(tile.name() + " не заложена");
        }
        int cost = unmortgageCost(tile);
        if (p.money() < cost) {
            throw new GameException("Недостаточно денег: выкуп стоит $" + cost);
        }
        p.addMoney(-cost);
        mortgaged.remove(tileIndex);
        log(p.name() + " выкупает " + tile.name() + " за $" + cost);
    }

    /** Залог — половина цены. */
    public static int mortgageValue(Tile tile) {
        return tile.price() / 2;
    }

    /** Выкуп — залог плюс 10%, с округлением вверх. */
    public static int unmortgageCost(Tile tile) {
        return (mortgageValue(tile) * 11 + 9) / 10;
    }

    private Tile requireOwnOwnable(Player p, int tileIndex) {
        if (tileIndex < 0 || tileIndex >= Board.SIZE) {
            throw new GameException("Нет такой клетки");
        }
        Tile tile = board.tile(tileIndex);
        if (!tile.type().isOwnable()) {
            throw new GameException("Эту клетку нельзя заложить");
        }
        if (!p.id().equals(owners.get(tileIndex))) {
            throw new GameException("Это не ваше имущество");
        }
        return tile;
    }

    private Tile requireOwnStreet(Player p, int tileIndex) {
        if (tileIndex < 0 || tileIndex >= Board.SIZE) {
            throw new GameException("Нет такой клетки");
        }
        Tile tile = board.tile(tileIndex);
        if (tile.type() != TileType.PROPERTY) {
            throw new GameException("Строить можно только на улицах");
        }
        if (!p.id().equals(owners.get(tileIndex))) {
            throw new GameException("Это не ваша улица");
        }
        return tile;
    }

    /** Продаёт банку все постройки игрока за полцены — при банкротстве. */
    private int sellAllBuildings(Player p) {
        int total = 0;
        for (int i : ownedBy(p.id())) {
            int level = level(i);
            if (level == 0) {
                continue;
            }
            if (level == HOTEL) {
                hotelsInBank++;
            } else {
                housesInBank += level;
            }
            total += level * board.tile(i).group().houseCost() / 2;
            setLevel(i, 0);
        }
        p.addMoney(total);
        return total;
    }

    // ------------------------------------------------------------------ платежи, долги, банкротство

    /** Результат попытки взять деньги с игрока. */
    private enum Charge { PAID, DEBT, BANKRUPT }

    /** Неоплаченный долг и что сделать сразу после его оплаты (например, ход после штрафа в тюрьме). */
    private record PendingDebt(Debt debt, Runnable afterPaid) {
    }

    private final Deque<PendingDebt> debts = new ArrayDeque<>();

    /** Платёж, после которого поток игры продолжается как обычно. {@code to == null} — банку. */
    private void pay(Player from, Player to, int amount) {
        charge(from, to, amount, null);
    }

    /**
     * Взять деньги с игрока.
     * <ul>
     *   <li>хватает наличных — платёж сразу, затем {@code afterPaid};</li>
     *   <li>не хватает, но можно продать постройки и заложить имущество — заводится долг,
     *       игра переходит в {@link TurnPhase#PAYING_DEBT}, {@code afterPaid} выполнится после оплаты;</li>
     *   <li>не покрыть даже всем имуществом — банкротство сразу.</li>
     * </ul>
     * При DEBT и BANKRUPT вызывающий код должен завершить действие через {@link #afterAction()}.
     */
    private Charge charge(Player from, Player to, int amount, Runnable afterPaid) {
        if (from.money() >= amount) {
            transfer(from, to, amount);
            if (afterPaid != null) {
                afterPaid.run();
            }
            return Charge.PAID;
        }
        if (liquidationValue(from) >= amount) {
            debts.addLast(new PendingDebt(new Debt(from.id(), to == null ? null : to.id(), amount), afterPaid));
            log(from.name() + " должен $" + amount + (to == null ? " банку" : " игроку " + to.name())
                    + ": нужно продать постройки или заложить имущество");
            return Charge.DEBT;
        }
        goBankrupt(from, to);
        return Charge.BANKRUPT;
    }

    private void transfer(Player from, Player to, int amount) {
        from.addMoney(-amount);
        if (to != null) {
            to.addMoney(amount);
        }
    }

    /** Сколько игрок может собрать: наличные + продажа всех построек + залог всего незаложенного. */
    private int liquidationValue(Player p) {
        int total = p.money();
        for (int i : ownedBy(p.id())) {
            Tile tile = board.tile(i);
            int level = level(i);
            if (level > 0) {
                total += level * tile.group().houseCost() / 2;
            }
            if (!mortgaged.contains(i)) {
                total += mortgageValue(tile);
            }
        }
        return total;
    }

    public void payDebt(String playerId) {
        PendingDebt pending = requireDebtor(playerId);
        Debt debt = pending.debt();
        Player p = player(playerId);
        if (p.money() < debt.amount()) {
            throw new GameException("Не хватает $" + (debt.amount() - p.money())
                    + ": продайте постройки или заложите имущество");
        }
        debts.removeFirst();
        Player creditor = debt.creditorId() == null ? null : player(debt.creditorId());
        transfer(p, creditor, debt.amount());
        log(p.name() + " выплачивает долг $" + debt.amount());
        if (pending.afterPaid() != null) {
            pending.afterPaid().run(); // сам завершает действие
        } else {
            afterAction();
        }
    }

    public void declareBankruptcy(String playerId) {
        PendingDebt pending = requireDebtor(playerId);
        debts.removeFirst();
        String creditorId = pending.debt().creditorId();
        goBankrupt(player(playerId), creditorId == null ? null : player(creditorId));
        afterAction();
    }

    /**
     * Банкротство: постройки продаются банку, деньги и имущество уходят кредитору
     * (банку — без залогов, игроку — заложенными как есть). Долги с участием банкрота отменяются.
     */
    private void goBankrupt(Player from, Player to) {
        sellAllBuildings(from);
        if (to != null) {
            to.addMoney(from.money());
        } else {
            mortgaged.removeIf(i -> from.id().equals(owners.get(i)));
        }
        owners.replaceAll((tile, owner) -> owner.equals(from.id()) && to != null ? to.id() : owner);
        owners.values().removeIf(owner -> owner.equals(from.id()));
        while (from.jailFreeCards() > 0) {
            deck(from.takeJailFreeCard()).returnJailFreeCard();
        }
        debts.removeIf(d -> d.debt().debtorId().equals(from.id()) || from.id().equals(d.debt().creditorId()));
        from.setBankrupt();
        log(from.name() + " банкрот!" + (to == null ? "" : " Всё имущество переходит игроку " + to.name()));
    }

    private PendingDebt requireDebtor(String playerId) {
        if (phase != TurnPhase.PAYING_DEBT) {
            throw new GameException("Сейчас нет долга");
        }
        PendingDebt pending = debts.peekFirst();
        if (!pending.debt().debtorId().equals(playerId)) {
            throw new GameException("Сейчас расплачивается другой игрок");
        }
        return pending;
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
        if (!debts.isEmpty()) {
            phase = TurnPhase.PAYING_DEBT;
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
    public Auction auction() { return auction; }
    public Map<Integer, Integer> buildings() { return Map.copyOf(buildings); }
    public int housesInBank() { return housesInBank; }
    public int hotelsInBank() { return hotelsInBank; }
    public Set<Integer> mortgaged() { return Set.copyOf(mortgaged); }
    /** Долг, который сейчас нужно закрыть, или {@code null}. */
    public Debt currentDebt() { return debts.isEmpty() ? null : debts.peekFirst().debt(); }
    /** Открытое предложение обмена или {@code null}. */
    public TradeOffer trade() { return trade; }
    public String winnerId() { return winnerId; }
    public List<String> log() { return List.copyOf(log); }

    public Player player(String id) {
        return players.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new GameException("Нет игрока " + id));
    }
}
