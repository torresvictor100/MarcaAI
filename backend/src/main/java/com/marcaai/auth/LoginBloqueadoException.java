package com.marcaai.auth;

public class LoginBloqueadoException extends RuntimeException {
    public LoginBloqueadoException(String message) {
        super(message);
    }
}
