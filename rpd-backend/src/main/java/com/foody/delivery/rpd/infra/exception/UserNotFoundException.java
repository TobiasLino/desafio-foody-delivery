package com.foody.delivery.rpd.infra.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("Usuário " + id + " não encontrado");
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}
