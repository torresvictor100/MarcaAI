package com.marcaai.relatoriofila;

/** Falha transitória da API da Anthropic ao redigir o relatório (429/5xx/529) — a única que o Retry repete (ADR-011). */
public class RedacaoTransitoriaException extends RedacaoIndisponivelException {

    public RedacaoTransitoriaException(String message, Throwable cause) {
        super(message, cause);
    }
}
