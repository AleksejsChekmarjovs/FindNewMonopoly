package com.example.monopoly.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Игрок в сыгранной партии: его место и победил ли он. */
@Entity
@Table(name = "game_participant")
public class GameParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id")
    private FinishedGame game;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(nullable = false)
    private int place;

    @Column(nullable = false)
    private boolean winner;

    protected GameParticipant() {
    }

    GameParticipant(FinishedGame game, long accountId, int place, boolean winner) {
        this.game = game;
        this.accountId = accountId;
        this.place = place;
        this.winner = winner;
    }

    public long accountId() { return accountId; }
    public int place() { return place; }
    public boolean winner() { return winner; }
}
