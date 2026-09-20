package com.marcaai.relatoriofila;

/** A redação pela IA não pôde ser concluída (chave ausente, timeout, erro HTTP) — o serviço cai no template. */
public class RedacaoIndisponivelException extends RuntimeException {
    public RedacaoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedacaoIndisponivelException(String message) {
        super(message);
    }
}
