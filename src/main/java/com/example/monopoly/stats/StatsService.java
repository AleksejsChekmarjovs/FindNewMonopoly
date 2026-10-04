package com.example.monopoly.stats;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Итоги сыгранных партий и статистика игрока. */
@Service
public class StatsService {

    /**
     * Статистика игрока. Проценты целые, побед и поражений вместе — 100 (если сыграна хоть одна партия).
     *
     * @param played партий, сыгранных до конца
     * @param losses партий без победы
     */
    public record Stats(long played, long wins, long losses, int winPercent, int lossPercent) {

        static Stats of(long played, long wins) {
            int winPercent = played == 0 ? 0 : (int) Math.round(100.0 * wins / played);
            return new Stats(played, wins, played - wins, winPercent, played == 0 ? 0 : 100 - winPercent);
        }
    }

    private final FinishedGameRepository games;
    private final GameParticipantRepository participants;
    private final Clock clock;

    public StatsService(FinishedGameRepository games, GameParticipantRepository participants, Clock clock) {
        this.games = games;
        this.participants = participants;
        this.clock = clock;
    }

    /**
     * Записать итог партии.
     *
     * @param standings аккаунты по местам: первый — победитель
     */
    @Transactional
    public void recordFinishedGame(String roomId, Instant startedAt, List<Long> standings) {
        FinishedGame game = new FinishedGame(roomId, startedAt, clock.instant());
        for (int i = 0; i < standings.size(); i++) {
            game.addParticipant(standings.get(i), i + 1);
        }
        games.save(game);
    }

    @Transactional(readOnly = true)
    public Stats statsFor(long accountId) {
        return Stats.of(participants.countByAccountId(accountId),
                participants.countByAccountIdAndWinnerTrue(accountId));
    }
}
