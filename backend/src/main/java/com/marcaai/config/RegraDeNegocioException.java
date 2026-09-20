package com.marcaai.config;

/**
 * O pedido está bem formado, mas viola uma regra do domínio (422, ADR-011): urgente sem justificativa,
 * período maior que o permitido, data que já passou. Formato inválido é 400; conflito com o estado atual é 409
 * ({@link ConflitoException}).
 */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String message) {
        super(message);
    }
}
