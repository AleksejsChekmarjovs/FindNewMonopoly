package com.example.monopoly.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Аукцион за клетку, от которой отказался (или не смог купить) игрок.
 * Участники ходят по кругу: повышают ставку или пасуют; спасовавший выбывает.
 * Лидер торгов не ходит — круг заканчивается, когда все остальные спасовали.
 * Первая ставка — не меньше стартовой цены (цены клетки), каждая следующая — больше предыдущей.
 */
public class Auction {

    private final int tileIndex;
    private final int startPrice;
    /** Ещё не спасовавшие участники, в порядке хода. */
    private final List<String> bidders;
    private int turn = 0;
    private int highestBid = 0;
    /** Счётчик ставок и пасов — по нему таймер понимает, что очередь сменилась. */
    private int moves = 0;
    private String highestBidderId;

    Auction(int tileIndex, int startPrice, List<String> bidders) {
        this.tileIndex = tileIndex;
        this.startPrice = startPrice;
        this.bidders = new ArrayList<>(bidders);
    }

    public int tileIndex() { return tileIndex; }
    public int startPrice() { return startPrice; }
    public int highestBid() { return highestBid; }
    public String highestBidderId() { return highestBidderId; }
    public List<String> bidders() { return List.copyOf(bidders); }
    int moves() { return moves; }

    /** Самая маленькая допустимая ставка сейчас. */
    public int minBid() {
        return highestBidderId == null ? startPrice : highestBid + 1;
    }

    public String currentBidderId() {
        return bidders.get(turn);
    }

    void bid(String playerId, int amount) {
        moves++;
        highestBid = amount;
        highestBidderId = playerId;
        turn = (turn + 1) % bidders.size();
    }

    void pass(String playerId) {
        moves++;
        bidders.remove(turn);
        if (!bidders.isEmpty()) {
            turn %= bidders.size();
        }
    }

    /** Торги окончены: все спасовали или остался только лидер. */
    boolean finished() {
        return bidders.isEmpty()
                || (bidders.size() == 1 && bidders.get(0).equals(highestBidderId));
    }
}
