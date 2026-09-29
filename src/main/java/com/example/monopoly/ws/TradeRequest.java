package com.example.monopoly.ws;

import com.example.monopoly.engine.TradeOffer;

import java.util.List;

/** Тело PROPOSE_TRADE; автор предложения — отправитель сообщения. Пустые поля считаются нулями. */
public record TradeRequest(
        String toId,
        List<Integer> giveTiles,
        List<Integer> takeTiles,
        Integer giveMoney,
        Integer takeMoney,
        Integer giveJailCards,
        Integer takeJailCards
) {

    TradeOffer toOffer(String fromId) {
        return new TradeOffer(fromId, toId, giveTiles, takeTiles,
                orZero(giveMoney), orZero(takeMoney), orZero(giveJailCards), orZero(takeJailCards));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
