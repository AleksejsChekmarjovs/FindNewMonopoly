package com.example.monopoly.engine;

/**
 * Карточка «Шанс» или «Общественной казны».
 *
 * @param amount смысл зависит от вида: сумма, индекс клетки, число шагов, цена за дом
 * @param amount2 для REPAIRS — цена за отель, иначе 0
 */
public record Card(DeckType deck, String text, Kind kind, int amount, int amount2) {

    public enum Kind {
        /** Перейти на клетку amount; за проход через «Старт» — $200. */
        MOVE_TO,
        /** Вернуться на amount клеток назад (без $200 за «Старт»). */
        MOVE_BACK,
        /** Ближайшая ж/д: если есть владелец — двойная аренда. */
        NEAREST_RAILROAD,
        /** Ближайшее предприятие: если есть владелец — 10× новый бросок кубиков. */
        NEAREST_UTILITY,
        GO_TO_JAIL,
        JAIL_FREE,
        /** Получить amount от банка. */
        GAIN,
        /** Заплатить amount банку. */
        PAY,
        /** Получить amount от каждого игрока. */
        GAIN_FROM_EACH,
        /** Заплатить amount каждому игроку. */
        PAY_EACH,
        /** Ремонт: amount за каждый дом, amount2 за каждый отель. */
        REPAIRS
    }

    static Card of(DeckType deck, String text, Kind kind, int amount) {
        return new Card(deck, text, kind, amount, 0);
    }

    static Card of(DeckType deck, String text, Kind kind) {
        return new Card(deck, text, kind, 0, 0);
    }
}
