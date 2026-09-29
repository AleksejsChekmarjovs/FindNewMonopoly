package com.example.monopoly.engine;

import java.util.List;

/**
 * Предложение обмена: {@code fromId} отдаёт give*, получает take* от {@code toId}.
 *
 * @param giveTiles клетки, которые отдаёт автор предложения
 * @param takeTiles клетки, которые автор хочет получить
 */
public record TradeOffer(
        String fromId,
        String toId,
        List<Integer> giveTiles,
        List<Integer> takeTiles,
        int giveMoney,
        int takeMoney,
        int giveJailCards,
        int takeJailCards
) {

    public TradeOffer {
        giveTiles = giveTiles == null ? List.of() : List.copyOf(giveTiles);
        takeTiles = takeTiles == null ? List.of() : List.copyOf(takeTiles);
    }

    boolean isEmpty() {
        return giveTiles.isEmpty() && takeTiles.isEmpty()
                && giveMoney == 0 && takeMoney == 0
                && giveJailCards == 0 && takeJailCards == 0;
    }
}
