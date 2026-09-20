package com.marcaai.config;

/**
 * O pedido é válido, mas choca com o estado atual do dado (409, ADR-011): vaga já ocupada, encaminhamento
 * já agendado, resultado já registrado, registro duplicado, situação que não permite a mudança.
 * Tentar de novo sem mudar nada dá o mesmo erro; recarregar o dado costuma resolver.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String message) {
        super(message);
    }
}
