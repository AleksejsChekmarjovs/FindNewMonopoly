package com.example.monopoly.stats;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Партия, сыгранная до конца. */
@Entity
@Table(name = "finished_game")
public class FinishedGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameParticipant> participants = new ArrayList<>();

    protected FinishedGame() {
    }

    FinishedGame(String roomId, Instant startedAt, Instant finishedAt) {
        this.roomId = roomId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    void addParticipant(long accountId, int place) {
        participants.add(new GameParticipant(this, accountId, place, place == 1));
        playerCount = participants.size();
    }

    public Long id() { return id; }
    public List<GameParticipant> participants() { return List.copyOf(participants); }
}
