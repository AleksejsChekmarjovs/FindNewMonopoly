package com.example.monopoly.account;

/** Неверный вход или регистрация. Сообщение показывается игроку как есть. */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
