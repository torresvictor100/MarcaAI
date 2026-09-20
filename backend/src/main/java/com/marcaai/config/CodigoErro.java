package com.marcaai.config;

import org.springframework.http.HttpStatus;

/**
 * Código estável de cada tipo de erro da API (ADR-011), no campo {@code erro} da resposta. O cliente decide
 * pelo código, não pelo texto da mensagem (que pode mudar).
 */
public enum CodigoErro {
    DADOS_INVALIDOS(HttpStatus.BAD_REQUEST),
    JSON_INVALIDO(HttpStatus.BAD_REQUEST),
    PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED),
    CREDENCIAIS_INVALIDAS(HttpStatus.UNAUTHORIZED),
    ACESSO_NEGADO(HttpStatus.FORBIDDEN),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND),
    ROTA_NAO_ENCONTRADA(HttpStatus.NOT_FOUND),
    METODO_NAO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED),
    FORMATO_NAO_ACEITO(HttpStatus.NOT_ACCEPTABLE),
    CONFLITO(HttpStatus.CONFLICT),
    TIPO_DE_CONTEUDO_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    REGRA_DE_NEGOCIO(HttpStatus.UNPROCESSABLE_ENTITY),
    LIMITE_EXCEDIDO(HttpStatus.TOO_MANY_REQUESTS),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    CodigoErro(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
