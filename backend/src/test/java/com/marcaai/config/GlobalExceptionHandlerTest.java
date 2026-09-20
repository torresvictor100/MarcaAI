package com.marcaai.config;

import com.marcaai.auth.LoginBloqueadoException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Envelope de erro da API: status e código certos, e nenhum detalhe interno na resposta (ADR-011). */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/agendamentos");

    private static void confere(ResponseEntity<Map<String, Object>> resposta, HttpStatus status, CodigoErro codigo) {
        assertThat(resposta.getStatusCode()).isEqualTo(status);
        assertThat(resposta.getBody())
                .containsEntry("status", status.value())
                .containsEntry("erro", codigo.name())
                .containsEntry("caminho", "/agendamentos")
                .containsKeys("timestamp", "mensagem");
    }

    @Test
    void erroNaoMapeadoVira500GenericoSemVazarAMensagemInterna() {
        ResponseEntity<Map<String, Object>> resposta = handler.handleGenerica(
                new IllegalStateException("SQL: select * from pacientes where cpf = '123'"), request);

        confere(resposta, HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO);
        assertThat(resposta.getBody().get("mensagem").toString()).doesNotContain("SQL").doesNotContain("cpf");
    }

    @Test
    void validacaoListaCadaCampoComOMotivo() {
        BeanPropertyBindingResult erros = new BeanPropertyBindingResult(new Object(), "request");
        erros.addError(new FieldError("request", "justificativa", "não deve estar em branco"));
        erros.addError(new FieldError("request", "posicao", "deve ser maior que ou igual à 1"));

        ResponseEntity<Map<String, Object>> resposta = handler.handleValidation(
                new MethodArgumentNotValidException(mock(MethodParameter.class), erros), request);

        confere(resposta, HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS);
        assertThat(resposta.getBody().get("mensagem").toString()).contains("justificativa: não deve estar em branco");
        assertThat(resposta.getBody().get("campos")).asList()
                .containsExactly(new RespostaErro.Campo("justificativa", "não deve estar em branco"),
                        new RespostaErro.Campo("posicao", "deve ser maior que ou igual à 1"));
    }

    @Test
    void validacaoSemErroDeCampoUsaMensagemPadraoESemListaDeCampos() {
        ResponseEntity<Map<String, Object>> resposta = handler.handleValidation(new MethodArgumentNotValidException(
                mock(MethodParameter.class), new BeanPropertyBindingResult(new Object(), "request")), request);

        assertThat(resposta.getBody()).containsEntry("mensagem", "Dados inválidos").doesNotContainKey("campos");
    }

    @Test
    void cadaExcecaoDeDominioTemSeuStatus() {
        confere(handler.handleRegraDeNegocio(new RegraDeNegocioException("urgente sem justificativa"), request),
                HttpStatus.UNPROCESSABLE_ENTITY, CodigoErro.REGRA_DE_NEGOCIO);
        confere(handler.handleConflito(new ConflitoException("vaga ocupada"), request),
                HttpStatus.CONFLICT, CodigoErro.CONFLITO);
        confere(handler.handleNotFound(new RecursoNaoEncontradoException("não existe"), request),
                HttpStatus.NOT_FOUND, CodigoErro.RECURSO_NAO_ENCONTRADO);
        confere(handler.handleNotFound(new EntityNotFoundException("não existe"), request),
                HttpStatus.NOT_FOUND, CodigoErro.RECURSO_NAO_ENCONTRADO);
        confere(handler.handleRequisicaoInvalida(new RequisicaoInvalidaException("faltou parâmetro"), request),
                HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO);
        confere(handler.handleNaoAutenticado(new NaoAutenticadoException("sem login"), request),
                HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO);
        confere(handler.handleBadCredentials(new BadCredentialsException("Login ou senha inválidos"), request),
                HttpStatus.UNAUTHORIZED, CodigoErro.CREDENCIAIS_INVALIDAS);
        confere(handler.handleAccessDenied(new AccessDeniedException("não pode"), request),
                HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO);
        confere(handler.handleLoginBloqueado(new LoginBloqueadoException("Muitas tentativas"), request),
                HttpStatus.TOO_MANY_REQUESTS, CodigoErro.LIMITE_EXCEDIDO);
    }

    @Test
    void metodoErradoDa405ComOsMetodosAceitosNoHeaderAllow() {
        ResponseEntity<Map<String, Object>> resposta = handler.handleMetodo(
                new HttpRequestMethodNotSupportedException("DELETE", Set.of("GET")), request);

        confere(resposta, HttpStatus.METHOD_NOT_ALLOWED, CodigoErro.METODO_NAO_PERMITIDO);
        assertThat(resposta.getHeaders().getFirst("Allow")).isEqualTo("GET");
    }

    @Test
    void gravacaoConcorrenteDa409PedindoParaRecarregar() {
        ResponseEntity<Map<String, Object>> resposta = handler.handleConcorrencia(
                new OptimisticLockingFailureException("versão mudou"), request);

        confere(resposta, HttpStatus.CONFLICT, CodigoErro.CONFLITO);
        assertThat(resposta.getBody().get("mensagem").toString()).contains("Recarregue");
    }

    private static DataIntegrityViolationException violacao(String sqlState) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("wrapper", new SQLException("ERROR: detalhe interno da constraint ux_x", sqlState)));
    }

    @Test
    void violacaoDoBancoViraOStatusCertoPeloSqlStateSemExporAConstraint() {
        confere(handler.handleIntegridade(violacao("23505"), request), HttpStatus.CONFLICT, CodigoErro.CONFLITO);
        confere(handler.handleIntegridade(violacao("22001"), request), HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS);
        confere(handler.handleIntegridade(violacao("23502"), request), HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS);
        confere(handler.handleIntegridade(violacao("23503"), request), HttpStatus.UNPROCESSABLE_ENTITY, CodigoErro.REGRA_DE_NEGOCIO);
        confere(handler.handleIntegridade(violacao("99999"), request), HttpStatus.CONFLICT, CodigoErro.CONFLITO);
        confere(handler.handleIntegridade(new DataIntegrityViolationException("sem causa SQL"), request),
                HttpStatus.CONFLICT, CodigoErro.CONFLITO);
        assertThat(handler.handleIntegridade(violacao("23505"), request).getBody().toString())
                .doesNotContain("ux_x").doesNotContain("constraint");
    }

    @Test
    void bancoForaDoArDa503ComRetryAfter() {
        ResponseEntity<Map<String, Object>> resposta = handler.handleBancoIndisponivel(
                new DataAccessResourceFailureException("Connection refused"), request);

        confere(resposta, HttpStatus.SERVICE_UNAVAILABLE, CodigoErro.SERVICO_INDISPONIVEL);
        assertThat(resposta.getHeaders().getFirst("Retry-After")).isEqualTo("10");
        assertThat(resposta.getBody().get("mensagem").toString()).doesNotContain("Connection refused");
    }

    @Test
    void corpoIlegivelDizOQueHouve() {
        var jsonQuebrado = new org.springframework.http.converter.HttpMessageNotReadableException("x",
                new com.fasterxml.jackson.core.JsonParseException(null, "quebrado"), null);
        var vazio = new org.springframework.http.converter.HttpMessageNotReadableException("x", null, null);
        var tipoErradoSemCampo = new org.springframework.http.converter.HttpMessageNotReadableException("x",
                com.fasterxml.jackson.databind.exc.MismatchedInputException.from(null, Long.class, "tipo"), null);
        var noItemDaLista = com.fasterxml.jackson.databind.exc.MismatchedInputException.from(null, Long.class, "tipo");
        noItemDaLista.prependPath(new com.fasterxml.jackson.databind.JsonMappingException.Reference(List.of(), 0));
        noItemDaLista.prependPath(new com.fasterxml.jackson.databind.JsonMappingException.Reference(new Object(), "documentos"));
        var tipoErradoEmLista = new org.springframework.http.converter.HttpMessageNotReadableException("x", noItemDaLista, null);

        assertThat(handler.handleCorpoIlegivel(jsonQuebrado, request).getBody()).containsEntry("mensagem", "JSON malformado no corpo da requisição");
        assertThat(handler.handleCorpoIlegivel(vazio, request).getBody()).containsEntry("mensagem", "Corpo da requisição ausente ou ilegível");
        assertThat(handler.handleCorpoIlegivel(tipoErradoSemCampo, request).getBody())
                .containsEntry("mensagem", "Corpo da requisição ausente ou ilegível");
        assertThat(handler.handleCorpoIlegivel(tipoErradoEmLista, request).getBody())
                .containsEntry("mensagem", "Valor inválido para o campo 'documentos.[0]'");
        confere(handler.handleCorpoIlegivel(vazio, request), HttpStatus.BAD_REQUEST, CodigoErro.JSON_INVALIDO);
    }

    @Test
    void errosDoProprioSpringMvcNaoViram500() throws Exception {
        confere(handler.handleParametroAusente(new org.springframework.web.bind.MissingServletRequestParameterException("especialidade", "String"), request),
                HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO);
        confere(handler.handleContentType(new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain"), request),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, CodigoErro.TIPO_DE_CONTEUDO_NAO_SUPORTADO);
        confere(handler.handleAccept(new org.springframework.web.HttpMediaTypeNotAcceptableException("text/csv"), request),
                HttpStatus.NOT_ACCEPTABLE, CodigoErro.FORMATO_NAO_ACEITO);
        confere(handler.handleRotaInexistente(new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "nao-existe"), request), HttpStatus.NOT_FOUND, CodigoErro.ROTA_NAO_ENCONTRADA);
        confere(handler.handleMetodo(new HttpRequestMethodNotSupportedException("PUT"), request),
                HttpStatus.METHOD_NOT_ALLOWED, CodigoErro.METODO_NAO_PERMITIDO);
        assertThat(handler.handleMetodo(new HttpRequestMethodNotSupportedException("PUT"), request).getHeaders()
                .containsKey("Allow")).isFalse();
        confere(handler.handleMethodValidation(mock(org.springframework.web.method.annotation.HandlerMethodValidationException.class), request),
                HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS);
    }

    @Test
    void violacaoDeRestricaoDeParametroListaOCampo() {
        var validador = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        record Busca(@jakarta.validation.constraints.Size(min = 2) String nome) {
        }
        var violacoes = validador.validate(new Busca("a"));

        ResponseEntity<Map<String, Object>> resposta = handler.handleConstraintViolation(
                new jakarta.validation.ConstraintViolationException(violacoes), request);

        confere(resposta, HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS);
        assertThat(resposta.getBody().get("campos")).asList().extracting("campo").containsExactly("nome");
    }
}
