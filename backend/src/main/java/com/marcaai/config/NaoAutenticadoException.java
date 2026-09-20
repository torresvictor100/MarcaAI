package com.marcaai.config;

/** Chegou num ponto que exige usuário logado sem haver um (401, ADR-011). */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String message) {
        super(message);
    }
}
