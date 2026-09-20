package com.marcaai.triagemia;

/**
 * Falha da API da Anthropic que tende a passar sozinha — 429 (limite), 5xx, 529 (sobrecarregada) — e por isso
 * é a única que o Retry repete (ADR-011). Timeout e 4xx não entram: repetir só dobraria a espera ou o erro.
 */
public class AnthropicTransitorioException extends AnthropicIndisponivelException {

    public AnthropicTransitorioException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 429 e toda a faixa 5xx, o que inclui o 529 "overloaded" da Anthropic. */
    public static boolean statusTransitorio(int status) {
        return status == 429 || (status >= 500 && status <= 599);
    }
}
