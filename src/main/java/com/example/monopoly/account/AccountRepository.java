package com.example.monopoly.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsernameKey(String usernameKey);

    boolean existsByUsernameKey(String usernameKey);
}
