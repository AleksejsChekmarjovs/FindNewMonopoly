package com.example.monopoly.engine;

import static com.example.monopoly.engine.Card.Kind.*;
import static com.example.monopoly.engine.DeckType.CHANCE;
import static com.example.monopoly.engine.DeckType.COMMUNITY_CHEST;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Колода карточек. Вытянутая карточка уходит под низ колоды — кроме
 * «Освободиться из тюрьмы»: она остаётся у игрока и возвращается в колоду после использования.
 */
public class Deck {

    private final Deque<Card> cards;
    private final List<Card> heldByPlayers = new ArrayList<>();

    /** Колода в заданном порядке (первая карточка — верхняя). */
    public Deck(List<Card> cards) {
        this.cards = new ArrayDeque<>(cards);
    }

    public static Deck shuffled(List<Card> cards) {
        List<Card> copy = new ArrayList<>(cards);
        Collections.shuffle(copy);
        return new Deck(copy);
    }

    public Card draw() {
        Card card = cards.removeFirst();
        if (card.kind() == JAIL_FREE) {
            heldByPlayers.add(card);
        } else {
            cards.addLast(card);
        }
        return card;
    }

    /** Игрок использовал «Освободиться из тюрьмы» — карточка возвращается под низ колоды. */
    void returnJailFreeCard() {
        cards.addLast(heldByPlayers.remove(0));
    }

    int size() {
        return cards.size();
    }

    // ------------------------------------------------------------------ классические колоды

    public static List<Card> chanceCards(Board board) {
        return List.of(
                Card.of(CHANCE, "Отправляйтесь на " + board.tile(39).name(), MOVE_TO, 39),
                Card.of(CHANCE, "Отправляйтесь на «Старт» и получите $200", MOVE_TO, 0),
                Card.of(CHANCE, "Отправляйтесь на " + board.tile(24).name()
                        + ". Если проходите «Старт», получите $200", MOVE_TO, 24),
                Card.of(CHANCE, "Отправляйтесь на " + board.tile(11).name()
                        + ". Если проходите «Старт», получите $200", MOVE_TO, 11),
                Card.of(CHANCE, "Отправляйтесь на ближайшую железную дорогу. "
                        + "Если у неё есть владелец, заплатите двойную аренду", NEAREST_RAILROAD),
                Card.of(CHANCE, "Отправляйтесь на ближайшую железную дорогу. "
                        + "Если у неё есть владелец, заплатите двойную аренду", NEAREST_RAILROAD),
                Card.of(CHANCE, "Отправляйтесь на ближайшее предприятие. Если у него есть владелец, "
                        + "бросьте кубики и заплатите 10× выпавшую сумму", NEAREST_UTILITY),
                Card.of(CHANCE, "Банк платит вам дивиденды $50", GAIN, 50),
                Card.of(CHANCE, "Освободиться из тюрьмы бесплатно", JAIL_FREE),
                Card.of(CHANCE, "Вернитесь на 3 клетки назад", MOVE_BACK, 3),
                Card.of(CHANCE, "Отправляйтесь в тюрьму. Не проходите «Старт», не получайте $200", GO_TO_JAIL),
                new Card(CHANCE, "Капитальный ремонт: $25 за каждый дом, $100 за каждый отель", REPAIRS, 25, 100),
                Card.of(CHANCE, "Штраф за превышение скорости $15", PAY, 15),
                Card.of(CHANCE, "Отправляйтесь на " + board.tile(5).name()
                        + ". Если проходите «Старт», получите $200", MOVE_TO, 5),
                Card.of(CHANCE, "Вас избрали председателем совета директоров. "
                        + "Заплатите каждому игроку $50", PAY_EACH, 50),
                Card.of(CHANCE, "Срок вашего строительного займа истёк. Получите $150", GAIN, 150)
        );
    }

    public static List<Card> communityChestCards() {
        return List.of(
                Card.of(COMMUNITY_CHEST, "Отправляйтесь на «Старт» и получите $200", MOVE_TO, 0),
                Card.of(COMMUNITY_CHEST, "Ошибка банка в вашу пользу. Получите $200", GAIN, 200),
                Card.of(COMMUNITY_CHEST, "Визит к врачу. Заплатите $50", PAY, 50),
                Card.of(COMMUNITY_CHEST, "От продажи акций вы получаете $50", GAIN, 50),
                Card.of(COMMUNITY_CHEST, "Освободиться из тюрьмы бесплатно", JAIL_FREE),
                Card.of(COMMUNITY_CHEST, "Отправляйтесь в тюрьму. Не проходите «Старт», не получайте $200", GO_TO_JAIL),
                Card.of(COMMUNITY_CHEST, "Отпускной фонд выплачен. Получите $100", GAIN, 100),
                Card.of(COMMUNITY_CHEST, "Возврат подоходного налога. Получите $20", GAIN, 20),
                Card.of(COMMUNITY_CHEST, "У вас день рождения. Получите по $10 от каждого игрока", GAIN_FROM_EACH, 10),
                Card.of(COMMUNITY_CHEST, "Выплата по страхованию жизни. Получите $100", GAIN, 100),
                Card.of(COMMUNITY_CHEST, "Оплатите больничные расходы $100", PAY, 100),
                Card.of(COMMUNITY_CHEST, "Оплатите обучение $50", PAY, 50),
                Card.of(COMMUNITY_CHEST, "Гонорар за консультацию. Получите $25", GAIN, 25),
                new Card(COMMUNITY_CHEST, "Ремонт улиц: $40 за каждый дом, $115 за каждый отель", REPAIRS, 40, 115),
                Card.of(COMMUNITY_CHEST, "Второе место на конкурсе красоты. Получите $10", GAIN, 10),
                Card.of(COMMUNITY_CHEST, "Вы получили наследство $100", GAIN, 100)
        );
    }
}
