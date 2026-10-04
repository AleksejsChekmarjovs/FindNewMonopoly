package com.example.monopoly.stats;

import org.springframework.data.jpa.repository.JpaRepository;

interface FinishedGameRepository extends JpaRepository<FinishedGame, Long> {
}
