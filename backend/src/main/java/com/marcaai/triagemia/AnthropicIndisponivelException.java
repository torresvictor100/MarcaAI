package com.marcaai.triagemia;

/** Sinaliza que a chamada à API da Anthropic não pôde ser completada (chave ausente, timeout, erro HTTP). */
public class AnthropicIndisponivelException extends RuntimeException {
    public AnthropicIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnthropicIndisponivelException(String message) {
        super(message);
    }
}
