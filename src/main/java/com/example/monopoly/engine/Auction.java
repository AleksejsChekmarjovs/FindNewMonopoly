package com.example.monopoly.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Аукцион за клетку, от которой отказался (или не смог купить) игрок.
 * Участники ходят по кругу: повышают ставку или пасуют; спасовавший выбывает.
 * Лидер торгов не ходит — круг заканчивается, когда все остальные спасовали.
 */
public class Auction {

    private final int tileIndex;
    /** Ещё не спасовавшие участники, в порядке хода. */
    private final List<String> bidders;
    private int turn = 0;
    private int highestBid = 0;
    private String highestBidderId;

    Auction(int tileIndex, List<String> bidders) {
        this.tileIndex = tileIndex;
        this.bidders = new ArrayList<>(bidders);
    }

    public int tileIndex() { return tileIndex; }
    public int highestBid() { return highestBid; }
    public String highestBidderId() { return highestBidderId; }
    public List<String> bidders() { return List.copyOf(bidders); }

    public String currentBidderId() {
        return bidders.get(turn);
    }

    void bid(String playerId, int amount) {
        highestBid = amount;
        highestBidderId = playerId;
        turn = (turn + 1) % bidders.size();
    }

    void pass(String playerId) {
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
