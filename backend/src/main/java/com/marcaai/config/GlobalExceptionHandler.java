package com.marcaai.config;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.marcaai.auth.LoginBloqueadoException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tradução de toda exceção para o corpo único de erro ({@link RespostaErro}) com o status certo (ADR-011).
 *
 * <p>Regra: erro de quem chamou nunca vira 500. Antes, o handler genérico ({@code Exception}) também pegava
 * as exceções do próprio Spring MVC — JSON malformado, rota inexistente, método errado, parâmetro faltando,
 * content-type errado — e tudo virava "Erro interno" (achado no scan do OWASP ZAP). Agora cada uma tem o seu
 * status, e o 500 fica só para falha de verdade do servidor, com o detalhe apenas no log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // SQLState do PostgreSQL (classe 22/23): o que cada violação do banco significa para quem chamou.
    private static final String VIOLACAO_UNICIDADE = "23505";
    private static final String VIOLACAO_CHAVE_ESTRANGEIRA = "23503";
    private static final String VIOLACAO_NAO_NULO = "23502";
    private static final String TEXTO_LONGO_DEMAIS = "22001";

    private static ResponseEntity<Map<String, Object>> responder(CodigoErro codigo, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(codigo.status()).body(RespostaErro.corpo(codigo, mensagem, request));
    }

    // --- 400: a requisição veio mal formada ----------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<RespostaErro.Campo> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new RespostaErro.Campo(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String mensagem = campos.isEmpty() ? "Dados inválidos"
                : campos.stream().map(c -> c.campo() + ": " + c.mensagem()).collect(Collectors.joining("; "));
        return ResponseEntity.status(CodigoErro.DADOS_INVALIDOS.status())
                .body(RespostaErro.corpo(CodigoErro.DADOS_INVALIDOS, mensagem, request, campos));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<RespostaErro.Campo> campos = ex.getConstraintViolations().stream()
                .map(v -> new RespostaErro.Campo(ultimoTrecho(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return ResponseEntity.status(CodigoErro.DADOS_INVALIDOS.status())
                .body(RespostaErro.corpo(CodigoErro.DADOS_INVALIDOS, "Dados inválidos", request, campos));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        return responder(CodigoErro.DADOS_INVALIDOS, "Parâmetros inválidos", request);
    }

    /** Corpo que não dá para ler: JSON quebrado, vazio ou com valor de tipo errado num campo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCorpoIlegivel(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String mensagem;
        if (ex.getCause() instanceof MismatchedInputException tipoErrado && !tipoErrado.getPath().isEmpty()) {
            String campo = tipoErrado.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            mensagem = "Valor inválido para o campo '" + campo + "'";
        } else if (ex.getCause() instanceof JsonParseException) {
            mensagem = "JSON malformado no corpo da requisição";
        } else {
            mensagem = "Corpo da requisição ausente ou ilegível";
        }
        return responder(CodigoErro.JSON_INVALIDO, mensagem, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroAusente(MissingServletRequestParameterException ex,
                                                                      HttpServletRequest request) {
        return responder(CodigoErro.PARAMETRO_INVALIDO, "Parâmetro obrigatório '" + ex.getParameterName() + "' ausente", request);
    }

    /** Parâmetro de URL com valor que não converte (situação/data/id inválido): é erro de quem chamou, não 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return responder(CodigoErro.PARAMETRO_INVALIDO, "Valor inválido para o parâmetro '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleRequisicaoInvalida(RequisicaoInvalidaException ex, HttpServletRequest request) {
        return responder(CodigoErro.PARAMETRO_INVALIDO, ex.getMessage(), request);
    }

    // --- 401 / 403 -----------------------------------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return responder(CodigoErro.CREDENCIAIS_INVALIDAS, ex.getMessage(), request);
    }

    @ExceptionHandler({NaoAutenticadoException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleNaoAutenticado(RuntimeException ex, HttpServletRequest request) {
        LogSeguranca.naoAutenticado(request);
        return responder(CodigoErro.NAO_AUTENTICADO, "Sessão ausente, inválida ou expirada. Faça login novamente.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        LogSeguranca.acessoNegado(request);
        return responder(CodigoErro.ACESSO_NEGADO, "Acesso negado para este papel/usuário", request);
    }

    // --- 404 / 405 / 406 / 415 -----------------------------------------------------------------------

    @ExceptionHandler({RecursoNaoEncontradoException.class, EntityNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return responder(CodigoErro.RECURSO_NAO_ENCONTRADO, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRotaInexistente(NoResourceFoundException ex, HttpServletRequest request) {
        return responder(CodigoErro.ROTA_NAO_ENCONTRADA, "Rota não encontrada: " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMetodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        Set<HttpMethod> permitidos = ex.getSupportedHttpMethods();
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(CodigoErro.METODO_NAO_PERMITIDO.status());
        if (permitidos != null && !permitidos.isEmpty()) {
            resposta.header(HttpHeaders.ALLOW, permitidos.stream().map(HttpMethod::name).collect(Collectors.joining(", ")));
        }
        return resposta.body(RespostaErro.corpo(CodigoErro.METODO_NAO_PERMITIDO,
                "Método " + ex.getMethod() + " não é aceito nesta rota", request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleContentType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return responder(CodigoErro.TIPO_DE_CONTEUDO_NAO_SUPORTADO, "Envie o corpo como application/json", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleAccept(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return responder(CodigoErro.FORMATO_NAO_ACEITO, "A API só responde em application/json", request);
    }

    // --- 409 / 422: o pedido é válido, mas o dado não permite ---------------------------------------

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<Map<String, Object>> handleConflito(ConflitoException ex, HttpServletRequest request) {
        return responder(CodigoErro.CONFLITO, ex.getMessage(), request);
    }

    /** Duas gravações ao mesmo tempo no mesmo registro (lock otimista, ADR-011): a segunda perde. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcorrencia(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Gravação concorrente recusada em {}: {}", request.getRequestURI(), ex.getMessage());
        return responder(CodigoErro.CONFLITO,
                "Este registro acabou de ser alterado por outra pessoa. Recarregue e tente de novo.", request);
    }

    /**
     * Restrição do banco violada. Vira o status certo pelo SQLState, sem expor nome de constraint nem SQL.
     * Na prática é a última trava contra corrida (o índice único de agendamento ativo, por exemplo).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegridade(DataIntegrityViolationException ex, HttpServletRequest request) {
        String sqlState = sqlState(ex);
        log.info("Restrição do banco violada (SQLState {}) em {}", sqlState, request.getRequestURI());
        return switch (sqlState == null ? "" : sqlState) {
            case TEXTO_LONGO_DEMAIS -> responder(CodigoErro.DADOS_INVALIDOS, "Um dos campos passou do tamanho máximo permitido", request);
            case VIOLACAO_NAO_NULO -> responder(CodigoErro.DADOS_INVALIDOS, "Falta um campo obrigatório", request);
            case VIOLACAO_CHAVE_ESTRANGEIRA -> responder(CodigoErro.REGRA_DE_NEGOCIO, "O pedido referencia um registro que não existe", request);
            case VIOLACAO_UNICIDADE -> responder(CodigoErro.CONFLITO, "Já existe um registro com esses dados", request);
            default -> responder(CodigoErro.CONFLITO, "O pedido conflita com os dados já gravados", request);
        };
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<Map<String, Object>> handleRegraDeNegocio(RegraDeNegocioException ex, HttpServletRequest request) {
        return responder(CodigoErro.REGRA_DE_NEGOCIO, ex.getMessage(), request);
    }

    // --- 429 / 503 / 500 -----------------------------------------------------------------------------

    @ExceptionHandler(LoginBloqueadoException.class)
    public ResponseEntity<Map<String, Object>> handleLoginBloqueado(LoginBloqueadoException ex, HttpServletRequest request) {
        return responder(CodigoErro.LIMITE_EXCEDIDO, ex.getMessage(), request);
    }

    /** Banco fora do ar ou sem conexão livre: é temporário — 503 com {@code Retry-After}, não 500. */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Map<String, Object>> handleBancoIndisponivel(RuntimeException ex, HttpServletRequest request) {
        log.error("Banco de dados indisponível ao processar {}", request.getRequestURI(), ex);
        return ResponseEntity.status(CodigoErro.SERVICO_INDISPONIVEL.status())
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(RespostaErro.corpo(CodigoErro.SERVICO_INDISPONIVEL,
                        "Serviço temporariamente indisponível. Tente novamente em instantes.", request));
    }

    /**
     * Rede de segurança para qualquer exceção não mapeada acima. Loga a stack trace só no servidor
     * (nunca no corpo da resposta) e devolve o mesmo envelope JSON dos demais erros, com mensagem
     * genérica — evita tanto o whitelabel error page do Spring quanto vazamento de detalhe interno.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerica(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado ao processar {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responder(CodigoErro.ERRO_INTERNO, "Erro interno inesperado. Tente novamente mais tarde.", request);
    }

    private static String sqlState(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static String ultimoTrecho(String caminho) {
        int ponto = caminho.lastIndexOf('.');
        return ponto < 0 ? caminho : caminho.substring(ponto + 1);
    }
}
