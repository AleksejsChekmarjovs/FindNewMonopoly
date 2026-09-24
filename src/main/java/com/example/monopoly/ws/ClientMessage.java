package com.example.monopoly.ws;

/**
 * Команда от клиента.
 * type: CREATE {name} | JOIN {roomId, name} | START | ROLL | BUY | DECLINE | PAY_JAIL_FINE | USE_JAIL_CARD
 *       | BID {amount} | PASS
 *       | BUILD {tileIndex} | SELL_HOUSE {tileIndex} | END_TURN
 */
public record ClientMessage(String type, String name, String roomId, Integer amount, Integer tileIndex) {
}
