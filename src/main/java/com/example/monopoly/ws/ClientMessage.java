package com.example.monopoly.ws;

/**
 * Команда от клиента.
 * type: CREATE {auth} | JOIN {roomId, auth} | RESUME {roomId, token} | START | ROLL | BUY | DECLINE | CLOSE_CARD | PAY_DUE | PAY_JAIL_FINE | USE_JAIL_CARD
 *       | BID {amount} | PASS
 *       | BUILD {tileIndex} | SELL_HOUSE {tileIndex}
 *       | MORTGAGE {tileIndex} | UNMORTGAGE {tileIndex}
 *       | PAY_DEBT | DECLARE_BANKRUPTCY
 *       | PROPOSE_TRADE {trade} | ACCEPT_TRADE | REJECT_TRADE | CANCEL_TRADE | END_TURN
 *
 * auth — токен входа в аккаунт (см. AuthController): имя игрока берётся из аккаунта.
 */
public record ClientMessage(String type, String auth, String roomId, String token, Integer amount,
                            Integer tileIndex, TradeRequest trade) {
}
