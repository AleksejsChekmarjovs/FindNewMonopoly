package com.example.monopoly.stats;

import org.springframework.data.jpa.repository.JpaRepository;

interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {

    long countByAccountId(long accountId);

    long countByAccountIdAndWinnerTrue(long accountId);
}
