package com.example.monopoly.account;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AuthSession s where s.expiresAt <= :now")
    int deleteExpired(Instant now);
}
