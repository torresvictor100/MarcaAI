package com.marcaai.config;

/** O recurso pedido não existe (404, ADR-011). Exceção do domínio, sem depender do JPA. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
