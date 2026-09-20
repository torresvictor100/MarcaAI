package com.marcaai.config;

/** A requisição em si está incompleta ou mal formada — parâmetro faltando, texto curto demais (400, ADR-011). */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String message) {
        super(message);
    }
}
