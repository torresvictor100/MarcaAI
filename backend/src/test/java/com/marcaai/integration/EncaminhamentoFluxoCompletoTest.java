package com.marcaai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recria de ponta a ponta os 3 cenários de demonstração do hackathon sobre os dados-base do seed
 * (V013__seed_dados_demo.sql): um caso urgente furando fila, um caso com CID incompatível sendo
 * bloqueado, e um caso normal seguindo o fluxo completo até o agendamento.
 */
class EncaminhamentoFluxoCompletoTest extends IntegracaoBase {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login(String login, String senha) {
        ResponseEntity<String> resposta = restTemplate.postForEntity(
                url("/auth/login"), Map.of("login", login, "senha", senha), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return leJson(resposta.getBody()).get("token").asText();
    }

    private HttpHeaders headersComToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode leJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long criarAtendimento(String tokenMedico, long pacienteId, String classificacaoRisco) {
        Map<String, Object> corpo = Map.of(
                "pacienteId", pacienteId,
                "profissionalId", 1,
                "unidadeId", 1,
                "data", LocalDateTime.now().toString(),
                "notas", "Atendimento de teste",
                "classificacaoRisco", classificacaoRisco);
        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/atendimentos"), HttpMethod.POST, new HttpEntity<>(corpo, headersComToken(tokenMedico)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return leJson(resposta.getBody()).get("id").asLong();
    }

    private long criarEncaminhamento(String tokenMedico, long atendimentoId, long cidId, boolean urgente, String justificativa) {
        Map<String, Object> corpo = Map.of(
                "atendimentoId", atendimentoId,
                "tipo", "CONSULTA_ESPECIALISTA",
                "especialidadeOuExame", "Cardiologia",
                "cidId", cidId,
                "urgente", urgente,
                "justificativaUrgencia", justificativa == null ? "" : justificativa,
                // Nasce com a guia; os demais obrigatórios vêm em anexarPacoteCompleto.
                "documentos", List.of(documento("GUIA_ENCAMINHAMENTO")));
        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/encaminhamentos"), HttpMethod.POST, new HttpEntity<>(corpo, headersComToken(tokenMedico)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return leJson(resposta.getBody()).get("id").asLong();
    }

    private static Map<String, Object> documento(String tipo) {
        return Map.of(
                "tipo", tipo,
                "referenciaArquivo", tipo.toLowerCase() + ".pdf",
                "dataEmissao", LocalDate.now().toString());
    }

    private void anexarDocumento(String tokenMedico, long encaminhamentoId, String tipo) {
        Map<String, Object> corpo = documento(tipo);
        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/encaminhamentos/" + encaminhamentoId + "/documentos"), HttpMethod.POST,
                new HttpEntity<>(corpo, headersComToken(tokenMedico)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void anexarPacoteCompleto(String tokenMedico, long encaminhamentoId) {
        anexarDocumento(tokenMedico, encaminhamentoId, "EXAME_ANTERIOR");
        anexarDocumento(tokenMedico, encaminhamentoId, "LAUDO");
    }

    private JsonNode buscarAnaliseIA(String token, long encaminhamentoId) {
        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/encaminhamentos/" + encaminhamentoId + "/analise-ia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(token)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return leJson(resposta.getBody());
    }

    @Test
    void casoUrgenteFuraFilaCasoIrregularEBloqueadoECasoNormalVaiAteOAgendamento() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");
        String tokenLab = login("lab.central", "Senha123!");

        // --- Caso 1: Marta (paciente 1), risco alto + urgência marcada -> deve furar a fila.
        long atendimentoMarta = criarAtendimento(tokenMedico, 1, "VERMELHO");
        long encMarta = criarEncaminhamento(tokenMedico, atendimentoMarta, 1, true, "Dor torácica com piora súbita");
        anexarPacoteCompleto(tokenMedico, encMarta);
        JsonNode analiseMarta = buscarAnaliseIA(tokenMedico, encMarta);
        assertThat(analiseMarta.get("bloqueado").asBoolean()).isFalse();

        // --- Caso 2: Carlos (paciente 3), CID incompatível com a especialidade -> bloqueado para revisão.
        long atendimentoCarlos = criarAtendimento(tokenMedico, 3, "VERDE");
        long encCarlos = criarEncaminhamento(tokenMedico, atendimentoCarlos, 3, false, null); // CID M54 é só de Ortopedia
        anexarPacoteCompleto(tokenMedico, encCarlos);
        JsonNode analiseCarlos = buscarAnaliseIA(tokenMedico, encCarlos);
        assertThat(analiseCarlos.get("bloqueado").asBoolean()).isTrue();
        assertThat(analiseCarlos.get("irregularidades").asText()).contains("BLOQUEANTE");

        ResponseEntity<String> statusCarlos = restTemplate.exchange(
                url("/encaminhamentos/" + encCarlos), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(leJson(statusCarlos.getBody()).get("status").asText()).isEqualTo("BLOQUEADO_REVISAO");

        // --- Caso 3: João (paciente 2), caso normal, sem urgência -> segue o fluxo até o agendamento.
        long atendimentoJoao = criarAtendimento(tokenMedico, 2, "AMARELO");
        long encJoao = criarEncaminhamento(tokenMedico, atendimentoJoao, 1, false, null);
        anexarPacoteCompleto(tokenMedico, encJoao);
        JsonNode analiseJoao = buscarAnaliseIA(tokenMedico, encJoao);
        assertThat(analiseJoao.get("bloqueado").asBoolean()).isFalse();
        assertThat(analiseMarta.get("scorePrioridade").asDouble())
                .isGreaterThan(analiseJoao.get("scorePrioridade").asDouble());

        // --- Fila: Marta (urgente) deve aparecer antes de João (normal) em Cardiologia.
        ResponseEntity<String> filaResposta = restTemplate.exchange(
                url("/fila?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        assertThat(filaResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode fila = leJson(filaResposta.getBody());
        assertThat(fila.isArray()).isTrue();

        int posicaoMarta = -1;
        int posicaoJoao = -1;
        long itemIdJoao = -1;
        for (JsonNode item : fila) {
            if (item.get("encaminhamentoId").asLong() == encMarta) {
                posicaoMarta = item.get("posicao").asInt();
            }
            if (item.get("encaminhamentoId").asLong() == encJoao) {
                posicaoJoao = item.get("posicao").asInt();
                itemIdJoao = item.get("itemId").asLong();
            }
        }
        assertThat(posicaoMarta).isPositive();
        assertThat(posicaoJoao).isPositive();
        assertThat(posicaoMarta).isLessThan(posicaoJoao);

        // --- Override manual da secretaria: João passa a ser o primeiro, com justificativa auditável.
        Map<String, Object> overrideBody = Map.of("posicao", 1, "justificativa", "Paciente já estava presente na unidade");
        ResponseEntity<String> overrideResposta = restTemplate.exchange(
                url("/fila/" + itemIdJoao + "/override"), HttpMethod.PATCH,
                new HttpEntity<>(overrideBody, headersComToken(tokenSecretaria)), String.class);
        assertThat(overrideResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(overrideResposta.getBody()).get("posicao").asInt()).isEqualTo(1);

        // --- Agendamento do caso normal (João) a partir de uma vaga disponível.
        ResponseEntity<String> vagasResposta = restTemplate.exchange(
                url("/vagas?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        long vagaId = leJson(vagasResposta.getBody()).get(0).get("id").asLong();

        Map<String, Object> agendamentoBody = Map.of("encaminhamentoId", encJoao, "vagaId", vagaId);
        ResponseEntity<String> agendamentoResposta = restTemplate.exchange(
                url("/agendamentos"), HttpMethod.POST,
                new HttpEntity<>(agendamentoBody, headersComToken(tokenSecretaria)), String.class);
        assertThat(agendamentoResposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> encaminhamentoJoaoAtualizado = restTemplate.exchange(
                url("/encaminhamentos/" + encJoao), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(leJson(encaminhamentoJoaoAtualizado.getBody()).get("status").asText()).isEqualTo("AGENDADO");

        // --- João confirma a presença; fica gravado e aparece para quem consulta o agendamento.
        long agendamentoJoaoId = leJson(agendamentoResposta.getBody()).get("id").asLong();
        assertThat(leJson(agendamentoResposta.getBody()).get("presencaConfirmadaEm").isNull()).isTrue();
        assertThat(leJson(agendamentoResposta.getBody()).get("unidadeEndereco").asText()).isNotBlank();
        String rotaConfirmar = "/agendamentos/" + agendamentoJoaoId + "/confirmar-presenca";
        assertThat(post(rotaConfirmar, Map.of(), login("marta.paciente", "Senha123!")).getStatusCode())
                .as("outro paciente").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(rotaConfirmar, Map.of(), tokenSecretaria).getStatusCode())
                .as("só o paciente confirma").isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> confirmado = post(rotaConfirmar, Map.of(), login("joao.paciente", "Senha123!"));
        assertThat(confirmado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(confirmado.getBody()).get("presencaConfirmadaEm").isNull()).isFalse();
        JsonNode agendamentoVistoPeloMedico = leJson(restTemplate.exchange(url("/encaminhamentos/" + encJoao + "/agendamento"),
                HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class).getBody());
        assertThat(agendamentoVistoPeloMedico.get("presencaConfirmadaEm").isNull()).isFalse();
        JsonNode filaComPresenca = leJson(restTemplate.exchange(url("/fila?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        assertThat(filaComPresenca).anyMatch(item -> item.get("encaminhamentoId").asLong() == encJoao
                && !item.get("presencaConfirmadaEm").isNull());

        // --- Timeline mostra as etapas concluídas até aqui.
        ResponseEntity<String> timelineResposta = restTemplate.exchange(
                url("/encaminhamentos/" + encJoao + "/timeline"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        JsonNode etapas = leJson(timelineResposta.getBody()).get("etapas");
        assertThat(etapas).anyMatch(e -> e.get("nome").asText().equals("AGENDADO") && e.get("concluida").asBoolean());

        // --- A especialista de Cardiologia registra o atendimento realizado; encaminhamento avança para REALIZADO.
        // (O laboratório não pode: é especialista de Hemograma, não de Cardiologia — ADR-007.)
        ResponseEntity<String> labTentandoFecharConsulta = restTemplate.exchange(
                url("/encaminhamentos/" + encJoao + "/resultado-exame"), HttpMethod.POST,
                new HttpEntity<>(Map.of("referenciaArquivo", "x.pdf", "dataResultado", LocalDate.now().toString()),
                        headersComToken(tokenLab)), String.class);
        assertThat(labTentandoFecharConsulta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String tokenEspecialista = login("elisa.cardio", "Senha123!");
        Map<String, Object> resultadoBody = Map.of(
                "referenciaArquivo", "laudo-final.pdf", "dataResultado", LocalDate.now().toString(),
                "observacoes", "Sem alterações relevantes");
        ResponseEntity<String> resultadoResposta = restTemplate.exchange(
                url("/encaminhamentos/" + encJoao + "/resultado-exame"), HttpMethod.POST,
                new HttpEntity<>(resultadoBody, headersComToken(tokenEspecialista)), String.class);
        assertThat(resultadoResposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> segundoRegistro = restTemplate.exchange(
                url("/encaminhamentos/" + encJoao + "/resultado-exame"), HttpMethod.POST,
                new HttpEntity<>(resultadoBody, headersComToken(tokenEspecialista)), String.class);
        assertThat(segundoRegistro.getStatusCode()).as("resultado já registrado é conflito").isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> encaminhamentoFinal = restTemplate.exchange(
                url("/encaminhamentos/" + encJoao), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(leJson(encaminhamentoFinal.getBody()).get("status").asText()).isEqualTo("REALIZADO");

        // Atendido sai da fila (a fila vale até o atendimento) e não tem mais posição.
        JsonNode filaCardiologia = leJson(restTemplate.exchange(url("/fila?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        assertThat(filaCardiologia).noneMatch(item -> item.get("encaminhamentoId").asLong() == encJoao);
        ResponseEntity<String> posicaoDepois = restTemplate.exchange(url("/encaminhamentos/" + encJoao + "/posicao-fila"),
                HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(posicaoDepois.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void consultasGeraisFuncionam() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");

        ResponseEntity<String> cidsResposta = restTemplate.exchange(
                url("/cids?especialidade=Cardiologia"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(cidsResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cids = leJson(cidsResposta.getBody());
        assertThat(cids).anyMatch(c -> c.get("codigo").asText().equals("I20"));

        // Busca por trecho, minúsculo: precisa achar os mesmos CIDs (não pode exigir o valor exato).
        ResponseEntity<String> cidsBuscaParcialResposta = restTemplate.exchange(
                url("/cids?especialidade=cardio"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(cidsBuscaParcialResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cidsBuscaParcial = leJson(cidsBuscaParcialResposta.getBody());
        assertThat(cidsBuscaParcial).anyMatch(c -> c.get("codigo").asText().equals("I20"));

        long atendimentoId = criarAtendimento(tokenMedico, 2, "AMARELO");
        long encaminhamentoId = criarEncaminhamento(tokenMedico, atendimentoId, 1, false, null);
        anexarPacoteCompleto(tokenMedico, encaminhamentoId);

        ResponseEntity<String> documentosResposta = restTemplate.exchange(
                url("/encaminhamentos/" + encaminhamentoId + "/documentos"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(documentosResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(documentosResposta.getBody())).hasSize(3);

        ResponseEntity<String> resumoResposta = restTemplate.exchange(
                url("/painel/resumo"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        assertThat(resumoResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(resumoResposta.getBody()).get("encaminhamentos").asLong()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> demandaResposta = restTemplate.exchange(
                url("/painel/demanda-por-especialidade"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        assertThat(demandaResposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(demandaResposta.getBody()).get("Cardiologia").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void pacienteNaoConsultaEncaminhamentoDeOutroPaciente() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenMarta = login("marta.paciente", "Senha123!");
        String tokenJoao = login("joao.paciente", "Senha123!");

        long atendimentoMarta = criarAtendimento(tokenMedico, 1, "VERDE");
        long encMarta = criarEncaminhamento(tokenMedico, atendimentoMarta, 1, false, null);

        ResponseEntity<String> martaVendoOProprio = restTemplate.exchange(
                url("/pacientes/1/encaminhamentos"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMarta)), String.class);
        assertThat(martaVendoOProprio.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(martaVendoOProprio.getBody())).anyMatch(e -> e.get("id").asLong() == encMarta);

        ResponseEntity<String> joaoTentandoVerDeMarta = restTemplate.exchange(
                url("/pacientes/1/encaminhamentos"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenJoao)), String.class);
        assertThat(joaoTentandoVerDeMarta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> joaoTentandoVerEncaminhamentoDeMarta = restTemplate.exchange(
                url("/encaminhamentos/" + encMarta), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenJoao)), String.class);
        assertThat(joaoTentandoVerEncaminhamentoDeMarta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void formatoInvalidoDa400ERegraDeNegocioDa422() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");
        long atendimentoId = criarAtendimento(tokenMedico, 2, "VERDE");

        Map<String, Object> urgenteSemJustificativa = Map.of(
                "atendimentoId", atendimentoId, "tipo", "CONSULTA_ESPECIALISTA",
                "especialidadeOuExame", "Cardiologia", "cidId", 1, "urgente", true, "justificativaUrgencia", "",
                "documentos", List.of(documento("GUIA_ENCAMINHAMENTO")));
        ResponseEntity<String> respostaUrgenteSemJustificativa = restTemplate.exchange(
                url("/encaminhamentos"), HttpMethod.POST, new HttpEntity<>(urgenteSemJustificativa, headersComToken(tokenMedico)), String.class);
        assertThat(respostaUrgenteSemJustificativa.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        Map<String, Object> cidInexistente = Map.of(
                "atendimentoId", atendimentoId, "tipo", "CONSULTA_ESPECIALISTA",
                "especialidadeOuExame", "Cardiologia", "cidId", 9999, "urgente", false, "justificativaUrgencia", "",
                "documentos", List.of(documento("GUIA_ENCAMINHAMENTO")));
        ResponseEntity<String> respostaCidInexistente = restTemplate.exchange(
                url("/encaminhamentos"), HttpMethod.POST, new HttpEntity<>(cidInexistente, headersComToken(tokenMedico)), String.class);
        assertThat(respostaCidInexistente.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        Map<String, Object> semDocumento = Map.of(
                "atendimentoId", atendimentoId, "tipo", "CONSULTA_ESPECIALISTA",
                "especialidadeOuExame", "Cardiologia", "cidId", 1, "urgente", false, "documentos", List.of());
        assertThat(restTemplate.exchange(url("/encaminhamentos"), HttpMethod.POST,
                new HttpEntity<>(semDocumento, headersComToken(tokenMedico)), String.class).getStatusCode())
                .as("encaminhamento sem documento").isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> filaSemParametro = restTemplate.exchange(
                url("/fila"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        assertThat(filaSemParametro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Cria um item de fila válido só para testar o override com justificativa em branco.
        long encaminhamentoId = criarEncaminhamento(tokenMedico, atendimentoId, 1, false, null);
        anexarPacoteCompleto(tokenMedico, encaminhamentoId);
        ResponseEntity<String> filaResposta = restTemplate.exchange(
                url("/fila?especialidade=Cardiologia"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        long itemId = -1;
        for (JsonNode item : leJson(filaResposta.getBody())) {
            if (item.get("encaminhamentoId").asLong() == encaminhamentoId) {
                itemId = item.get("itemId").asLong();
            }
        }
        assertThat(itemId).isPositive();

        Map<String, Object> overrideSemJustificativa = Map.of("posicao", 1, "justificativa", "");
        ResponseEntity<String> respostaOverrideInvalido = restTemplate.exchange(
                url("/fila/" + itemId + "/override"), HttpMethod.PATCH,
                new HttpEntity<>(overrideSemJustificativa, headersComToken(tokenSecretaria)), String.class);
        assertThat(respostaOverrideInvalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void encaminhamentoEntraNaFilaJaNaCriacaoComAlertaDoQueFaltaESaiDoAlertaAoCompletar() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        long atendimentoId = criarAtendimento(tokenMedico, 2, "LARANJA");

        long encaminhamentoId = criarEncaminhamento(tokenMedico, atendimentoId, 1, false, null); // só a guia
        JsonNode encaminhamento = leJson(restTemplate.exchange(url("/encaminhamentos/" + encaminhamentoId), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getBody());
        assertThat(encaminhamento.get("status").asText()).isEqualTo("NA_FILA");
        assertThat(buscarAnaliseIA(tokenMedico, encaminhamentoId).get("irregularidades").asText())
                .contains("Faltam documentos exigidos para Cardiologia: exame anterior, laudo médico");

        anexarPacoteCompleto(tokenMedico, encaminhamentoId);
        assertThat(buscarAnaliseIA(tokenMedico, encaminhamentoId).get("irregularidades").asText()).doesNotContain("Faltam documentos");

        JsonNode exigidos = leJson(restTemplate.exchange(url("/especialidades/Cardiologia/documentos-exigidos"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getBody());
        assertThat(exigidos).extracting(JsonNode::asText).containsExactlyInAnyOrder("GUIA_ENCAMINHAMENTO", "EXAME_ANTERIOR", "LAUDO");
    }

    @Test
    void restricoesDeAcessoPorPapelRetornam403() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");

        ResponseEntity<String> medicoTentandoVerFilaPorEspecialidade = restTemplate.exchange(
                url("/fila?especialidade=Cardiologia"), HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(medicoTentandoVerFilaPorEspecialidade.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> medicoTentandoOverride = restTemplate.exchange(
                url("/fila/1/override"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("posicao", 1, "justificativa", "x"), headersComToken(tokenMedico)), String.class);
        assertThat(medicoTentandoOverride.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> secretariaTentandoCriarAtendimento = restTemplate.exchange(
                url("/atendimentos"), HttpMethod.POST,
                new HttpEntity<>(Map.of("pacienteId", 1, "profissionalId", 1, "unidadeId", 1,
                        "data", LocalDateTime.now().toString(), "notas", "x", "classificacaoRisco", "VERDE"),
                        headersComToken(tokenSecretaria)),
                String.class);
        assertThat(secretariaTentandoCriarAtendimento.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> medicoTentandoRegistrarResultadoExame = restTemplate.exchange(
                url("/encaminhamentos/1/resultado-exame"), HttpMethod.POST,
                new HttpEntity<>(Map.of("referenciaArquivo", "x.pdf", "dataResultado", LocalDate.now().toString()), headersComToken(tokenMedico)),
                String.class);
        assertThat(medicoTentandoRegistrarResultadoExame.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> medicoTentandoAgendar = restTemplate.exchange(
                url("/agendamentos"), HttpMethod.POST,
                new HttpEntity<>(Map.of("encaminhamentoId", 1, "vagaId", 1), headersComToken(tokenMedico)), String.class);
        assertThat(medicoTentandoAgendar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void buscaDePacientePorNomeLiberadaSoParaMedicoUbsSecretariaEAdmin() {
        String tokenMedico = login("bruno.ubs", "Senha123!");

        ResponseEntity<String> resposta = restTemplate.exchange(url("/pacientes?nome=mARTA"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode encontrados = leJson(resposta.getBody());
        assertThat(encontrados).isNotEmpty();
        assertThat(encontrados.get(0).get("nome").asText()).containsIgnoringCase("marta");
        assertThat(encontrados.get(0).get("cpfMascarado").asText()).isEqualTo("***.111.111-**");
        assertThat(encontrados.get(0).has("cpf")).isFalse();

        // Paciente da massa V015: sempre tem atendimento registrado pelo bruno.ubs.
        long pacienteDemoId = leJson(restTemplate.exchange(url("/pacientes?nome=Cláudio Martins"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getBody()).get(0).get("id").asLong();
        ResponseEntity<String> atendimentosDaMarta = restTemplate.exchange(url("/pacientes/" + pacienteDemoId + "/atendimentos"),
                HttpMethod.GET, new HttpEntity<>(headersComToken(tokenMedico)), String.class);
        assertThat(atendimentosDaMarta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listaAtendimentos = leJson(atendimentosDaMarta.getBody());
        assertThat(listaAtendimentos).isNotEmpty()
                .allMatch(at -> at.get("pacienteId").asLong() == pacienteDemoId)
                .as("médico só vê os atendimentos que ele registrou")
                .allMatch(at -> at.get("profissionalId").asLong() == listaAtendimentos.get(0).get("profissionalId").asLong());
        assertThat(restTemplate.exchange(url("/pacientes/" + pacienteDemoId + "/atendimentos"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(login("lab.central", "Senha123!"))), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode semAcento = leJson(restTemplate.exchange(url("/pacientes?nome=joao"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getBody());
        assertThat(semAcento).as("busca ignora acento").anyMatch(p -> p.get("nome").asText().startsWith("João"));

        assertThat(restTemplate.exchange(url("/pacientes?nome=m"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getStatusCode())
                .as("termo curto demais").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.exchange(url("/unidades"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String login : List.of("marta.paciente", "elisa.cardio", "lab.central")) {
            String token = login(login, "Senha123!");
            assertThat(restTemplate.exchange(url("/pacientes?nome=marta"), HttpMethod.GET,
                    new HttpEntity<>(headersComToken(token)), String.class).getStatusCode())
                    .as(login).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(restTemplate.exchange(url("/unidades"), HttpMethod.GET,
                    new HttpEntity<>(headersComToken(token)), String.class).getStatusCode())
                    .as(login).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void agendamentoRecusaVagaQueJaEstaOcupada() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");

        long atendimentoA = criarAtendimento(tokenMedico, 2, "VERDE");
        long encaminhamentoA = criarEncaminhamento(tokenMedico, atendimentoA, 1, false, null);
        anexarPacoteCompleto(tokenMedico, encaminhamentoA);

        long atendimentoB = criarAtendimento(tokenMedico, 2, "VERDE");
        long encaminhamentoB = criarEncaminhamento(tokenMedico, atendimentoB, 1, false, null);
        anexarPacoteCompleto(tokenMedico, encaminhamentoB);

        long vagaId = 1L; // qualquer uma das 2 vagas seedadas — o teste não depende de qual está livre agora

        // Primeira tentativa: 201 se a vaga ainda estiver disponível, ou 400 se outro teste já a ocupou —
        // de qualquer forma, a vaga fica OCUPADA (ou já estava) ao final desta chamada.
        restTemplate.exchange(url("/agendamentos"), HttpMethod.POST,
                new HttpEntity<>(Map.of("encaminhamentoId", encaminhamentoA, "vagaId", vagaId), headersComToken(tokenSecretaria)), String.class);

        ResponseEntity<String> segundaTentativa = restTemplate.exchange(url("/agendamentos"), HttpMethod.POST,
                new HttpEntity<>(Map.of("encaminhamentoId", encaminhamentoB, "vagaId", vagaId), headersComToken(tokenSecretaria)), String.class);
        assertThat(segundaTentativa.getStatusCode()).as("vaga já ocupada é conflito").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginComSenhaErradaRetorna401() {
        ResponseEntity<String> resposta = restTemplate.postForEntity(
                url("/auth/login"), Map.of("login", "bruno.ubs", "senha", "senha-errada"), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pacienteNaoAutenticadoNaoAcessaEndpointProtegido() {
        ResponseEntity<String> resposta = restTemplate.getForEntity(url("/atendimentos/1"), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpStatusCode getStatus(String path, String token) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headersComToken(token)), String.class)
                .getStatusCode();
    }

    @Test
    void cadaPapelSoLeOsEncaminhamentosComQueTemVinculo() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenCardio = login("elisa.cardio", "Senha123!");
        String tokenDerma = login("fernanda.derma", "Senha123!");
        String tokenLab = login("lab.central", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");

        long atendimento = criarAtendimento(tokenMedico, 2, "AMARELO");
        long encCardio = criarEncaminhamento(tokenMedico, atendimento, 1, false, null);

        for (String rota : List.of("/encaminhamentos/" + encCardio, "/encaminhamentos/" + encCardio + "/documentos",
                "/encaminhamentos/" + encCardio + "/timeline", "/atendimentos/" + atendimento)) {
            assertThat(getStatus(rota, tokenMedico)).as("médico que atendeu: " + rota).isEqualTo(HttpStatus.OK);
            // Da especialidade, mas ainda não agendado numa vaga dela: não é da agenda dela (ADR-007).
            assertThat(getStatus(rota, tokenCardio)).as("especialista, antes do agendamento: " + rota).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(getStatus(rota, tokenSecretaria)).as("secretaria: " + rota).isEqualTo(HttpStatus.OK);
            assertThat(getStatus(rota, tokenDerma)).as("especialista de outra especialidade: " + rota).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(getStatus(rota, tokenLab)).as("laboratório, em consulta: " + rota).isEqualTo(HttpStatus.FORBIDDEN);
        }

        // Laboratório é especialista de Hemograma: igual ao especialista, só enxerga o exame depois de agendado
        // numa vaga dele (massa de demonstração).
        JsonNode filaHemograma = leJson(restTemplate.exchange(url("/fila?especialidade=Hemograma Completo"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        assertThat(filaHemograma).isNotEmpty();
        long encHemograma = filaHemograma.get(0).get("encaminhamentoId").asLong();
        assertThat(getStatus("/encaminhamentos/" + encHemograma, tokenLab)).as("laboratório, antes do agendamento")
                .isEqualTo(HttpStatus.FORBIDDEN);
        long vagaHemograma = leJson(restTemplate.exchange(url("/vagas?especialidade=Hemograma Completo"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody()).get(0).get("id").asLong();
        assertThat(post("/agendamentos", Map.of("encaminhamentoId", encHemograma, "vagaId", vagaHemograma), tokenSecretaria)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(getStatus("/encaminhamentos/" + encHemograma, tokenLab)).isEqualTo(HttpStatus.OK);
        assertThat(getStatus("/encaminhamentos/" + encHemograma, tokenCardio)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getStatus("/pacientes/2/encaminhamentos", tokenLab)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> post(String path, Object corpo, String token) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(corpo, headersComToken(token)), String.class);
    }

    @Test
    void secretariaRemarcaAntecipaECancelaUmAgendamentoComMotivo() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenSecretaria = login("secretaria", "Senha123!");

        // Paciente 5 (massa de demonstração) só tem encaminhamento de Ortopedia: o de Cardiologia não é duplicado.
        long atendimento = criarAtendimento(tokenMedico, 5, "AMARELO");
        long enc = criarEncaminhamento(tokenMedico, atendimento, 1, false, null);
        anexarPacoteCompleto(tokenMedico, enc);

        JsonNode vagas = leJson(restTemplate.exchange(url("/vagas?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        assertThat(vagas.size()).isGreaterThanOrEqualTo(2);
        long vagaCedo = vagas.get(0).get("id").asLong();
        long vagaTarde = vagas.get(1).get("id").asLong();

        ResponseEntity<String> agendado = post("/agendamentos", Map.of("encaminhamentoId", enc, "vagaId", vagaCedo), tokenSecretaria);
        assertThat(agendado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long agendamentoId = leJson(agendado.getBody()).get("id").asLong();
        assertThat(post("/agendamentos", Map.of("encaminhamentoId", enc, "vagaId", vagaTarde), tokenSecretaria).getStatusCode())
                .as("não agenda duas vezes o mesmo encaminhamento").isEqualTo(HttpStatus.CONFLICT);

        assertThat(post("/agendamentos/" + agendamentoId + "/remarcar", Map.of("vagaId", vagaTarde, "motivo", ""), tokenSecretaria)
                .getStatusCode()).as("motivo obrigatório").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/agendamentos/" + agendamentoId + "/remarcar", Map.of("vagaId", vagaTarde, "motivo", "Médica em congresso"),
                tokenMedico).getStatusCode()).as("só a secretaria altera").isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> remarcado = post("/agendamentos/" + agendamentoId + "/remarcar",
                Map.of("vagaId", vagaTarde, "motivo", "Médica em congresso"), tokenSecretaria);
        assertThat(remarcado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(remarcado.getBody()).get("vagaId").asLong()).isEqualTo(vagaTarde);

        assertThat(post("/agendamentos/" + agendamentoId + "/antecipar", Map.of("vagaId", vagaTarde, "motivo", "x"), tokenSecretaria)
                .getStatusCode()).as("antecipar para a própria data").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ResponseEntity<String> antecipado = post("/agendamentos/" + agendamentoId + "/antecipar",
                Map.of("vagaId", vagaCedo, "motivo", "Abriu vaga antes"), tokenSecretaria);
        assertThat(antecipado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(antecipado.getBody()).get("historico")).hasSize(2);

        ResponseEntity<String> cancelado = post("/agendamentos/" + agendamentoId + "/cancelar",
                Map.of("motivo", "Paciente pediu para desmarcar"), tokenSecretaria);
        assertThat(cancelado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(cancelado.getBody()).get("status").asText()).isEqualTo("CANCELADO");

        // Voltou para a fila aguardando agendamento, e a vaga voltou a ficar livre.
        JsonNode encDepois = leJson(restTemplate.exchange(url("/encaminhamentos/" + enc), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getBody());
        assertThat(encDepois.get("status").asText()).isEqualTo("NA_FILA");
        assertThat(restTemplate.exchange(url("/encaminhamentos/" + enc + "/agendamento"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> reagendado = post("/agendamentos", Map.of("encaminhamentoId", enc, "vagaId", vagaCedo), tokenSecretaria);
        assertThat(reagendado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(leJson(reagendado.getBody()).get("id").asLong()).isNotEqualTo(agendamentoId);
    }

    @Test
    void relatorioIaDaFilaSoParaSecretariaEComTextoPadraoSemChave() {
        String tokenSecretaria = login("secretaria", "Senha123!");
        String tokenMedico = login("bruno.ubs", "Senha123!");

        assertThat(post("/fila/relatorio-ia?especialidade=Cardiologia", Map.of(), tokenMedico).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/fila/relatorio-ia?especialidade=Astrologia", Map.of(), tokenSecretaria).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<String> gerado = post("/fila/relatorio-ia?especialidade=Cardiologia", Map.of(), tokenSecretaria);
        assertThat(gerado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode relatorio = leJson(gerado.getBody());
        assertThat(relatorio.get("origemTexto").asText()).isEqualTo("MODELO"); // sem chave da Anthropic no teste
        assertThat(relatorio.get("texto").asText()).startsWith("Fila de Cardiologia");
        assertThat(relatorio.get("geradoPor").asText()).isEqualTo("secretaria");

        ResponseEntity<String> ultimo = restTemplate.exchange(url("/fila/relatorio-ia?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        assertThat(ultimo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leJson(ultimo.getBody()).get("id").asLong()).isEqualTo(relatorio.get("id").asLong());
        assertThat(restTemplate.exchange(url("/fila/relatorio-ia?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void secretariaCadastraEspecialistaEAbreVagasEmLoteSemDuplicar() {
        String tokenSecretaria = login("secretaria", "Senha123!");
        String tokenMedico = login("bruno.ubs", "Senha123!");
        Map<String, Object> especialista = Map.of("nome", "Dra. Teste Lote", "registroConselho", "CRM-SP 990001",
                "especialidade", "dermatologia");

        assertThat(post("/profissionais", especialista, tokenMedico).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> criado = post("/profissionais", especialista, tokenSecretaria);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode profissional = leJson(criado.getBody());
        assertThat(profissional.get("especialidade").asText()).isEqualTo("Dermatologia");
        assertThat(post("/profissionais", especialista, tokenSecretaria).getStatusCode())
                .as("registro do conselho repetido").isEqualTo(HttpStatus.CONFLICT);

        JsonNode unidades = leJson(restTemplate.exchange(url("/unidades"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        assertThat(unidades).isNotEmpty();

        LocalDate segunda = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        Map<String, Object> lote = Map.of(
                "profissionalId", profissional.get("id").asLong(), "unidadeId", unidades.get(0).get("id").asLong(),
                "dataInicio", segunda.toString(), "dataFim", segunda.plusWeeks(2).minusDays(1).toString(),
                "diasDaSemana", List.of("MONDAY", "WEDNESDAY"), "horaInicio", "08:00", "horaFim", "10:00", "duracaoMinutos", 30);

        assertThat(post("/vagas/lote", lote, tokenMedico).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> primeiro = post("/vagas/lote", lote, tokenSecretaria);
        assertThat(primeiro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(leJson(primeiro.getBody()).get("criadas").asInt()).isEqualTo(16); // 2 semanas × 2 dias × 4 horários

        JsonNode repetido = leJson(post("/vagas/lote", lote, tokenSecretaria).getBody());
        assertThat(repetido.get("criadas").asInt()).isZero();
        assertThat(repetido.get("ignoradasDuplicadas").asInt()).isEqualTo(16);

        JsonNode vagasDerma = leJson(restTemplate.exchange(url("/vagas?especialidade=Dermatologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        assertThat(vagasDerma).filteredOn(v -> v.get("profissionalNome").asText().equals("Dra. Teste Lote")).hasSize(16);
    }

    @Test
    void indicadoresDoPainelSoParaGestao() {
        String tokenSecretaria = login("secretaria", "Senha123!");
        String tokenMedico = login("bruno.ubs", "Senha123!");

        ResponseEntity<String> resposta = restTemplate.exchange(url("/painel/indicadores"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode indicadores = leJson(resposta.getBody());
        assertThat(indicadores.get("numeros").get("naFila").asLong()).isPositive();
        assertThat(indicadores.get("situacaoEncaminhamentos")).hasSize(7);
        assertThat(indicadores.get("filaPorRisco")).isNotEmpty();
        assertThat(indicadores.get("movimento30Dias")).hasSize(30);

        assertThat(restTemplate.exchange(url("/painel/indicadores"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenMedico)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void atendimentoFicaNoNomeDoMedicoLogadoMesmoComOutroIdNoCorpo() {
        String tokenMedico = login("bruno.ubs", "Senha123!");
        Map<String, Object> corpo = Map.of(
                "pacienteId", 2, "profissionalId", 2, "unidadeId", 1,
                "data", "2026-09-23T10:00:00", "classificacaoRisco", "VERDE");

        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/atendimentos"), HttpMethod.POST, new HttpEntity<>(corpo, headersComToken(tokenMedico)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(leJson(resposta.getBody()).get("profissionalId").asLong()).isEqualTo(1L);
    }

    @Test
    void especialistaSoVeAAgendaEOsEncaminhamentosDasPropriasVagas() {
        String tokenSecretaria = login("secretaria", "Senha123!");
        String tokenMedico = login("bruno.ubs", "Senha123!");
        String tokenElisa = login("elisa.cardio", "Senha123!");

        JsonNode outro = leJson(post("/profissionais", Map.of("nome", "Dr. Outro Cardio", "registroConselho", "CRM-SP 990002",
                "especialidade", "Cardiologia"), tokenSecretaria).getBody());
        JsonNode cardiologistas = leJson(restTemplate.exchange(url("/profissionais?especialidade=Cardiologia"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
        long elisaId = java.util.stream.StreamSupport.stream(cardiologistas.spliterator(), false)
                .filter(p -> p.get("nome").asText().startsWith("Dra. Elisa")).findFirst().orElseThrow().get("id").asLong();
        long unidadeId = leJson(restTemplate.exchange(url("/unidades"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody()).get(0).get("id").asLong();

        // Uma vaga para cada um, num dia distante (não disputa as vagas usadas pelos outros testes).
        LocalDate dia = LocalDate.now().plusWeeks(30).with(java.time.DayOfWeek.TUESDAY);
        java.util.function.LongFunction<Long> abrirVaga = profissionalId -> {
            post("/vagas/lote", Map.of("profissionalId", profissionalId, "unidadeId", unidadeId,
                    "dataInicio", dia.toString(), "dataFim", dia.toString(), "diasDaSemana", List.of("TUESDAY"),
                    "horaInicio", "07:00", "horaFim", "07:30", "duracaoMinutos", 30), tokenSecretaria);
            JsonNode vagas = leJson(restTemplate.exchange(url("/vagas?especialidade=Cardiologia"), HttpMethod.GET,
                    new HttpEntity<>(headersComToken(tokenSecretaria)), String.class).getBody());
            return java.util.stream.StreamSupport.stream(vagas.spliterator(), false)
                    .filter(v -> v.get("profissionalId").asLong() == profissionalId
                            && v.get("dataHora").asText().startsWith(dia.toString()))
                    .findFirst().orElseThrow().get("id").asLong();
        };
        long vagaElisa = abrirVaga.apply(elisaId);
        long vagaOutro = abrirVaga.apply(outro.get("id").asLong());

        long encDaElisa = criarEncaminhamento(tokenMedico, criarAtendimento(tokenMedico, 3, "AMARELO"), 1, false, null);
        long encDoOutro = criarEncaminhamento(tokenMedico, criarAtendimento(tokenMedico, 4, "AMARELO"), 1, false, null);
        assertThat(post("/agendamentos", Map.of("encaminhamentoId", encDaElisa, "vagaId", vagaElisa), tokenSecretaria)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("/agendamentos", Map.of("encaminhamentoId", encDoOutro, "vagaId", vagaOutro), tokenSecretaria)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Detalhe e registro de resultado: só o que está na vaga dela.
        assertThat(getStatus("/encaminhamentos/" + encDaElisa, tokenElisa)).isEqualTo(HttpStatus.OK);
        assertThat(getStatus("/encaminhamentos/" + encDoOutro, tokenElisa)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/encaminhamentos/" + encDoOutro + "/resultado-exame",
                Map.of("referenciaArquivo", "x.pdf", "dataResultado", LocalDate.now().toString()), tokenElisa).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode aAtender = leJson(restTemplate.exchange(url("/agenda/encaminhamentos?situacao=A_ATENDER"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenElisa)), String.class).getBody());
        assertThat(aAtender).anyMatch(e -> e.get("encaminhamentoId").asLong() == encDaElisa)
                .noneMatch(e -> e.get("encaminhamentoId").asLong() == encDoOutro);
        JsonNode atendidos = leJson(restTemplate.exchange(url("/agenda/encaminhamentos?situacao=ATENDIDO"), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenElisa)), String.class).getBody());
        assertThat(atendidos).noneMatch(e -> e.get("encaminhamentoId").asLong() == encDaElisa);

        JsonNode vagasDela = leJson(restTemplate.exchange(url("/agenda/vagas?de=" + dia + "&ate=" + dia), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenElisa)), String.class).getBody());
        assertThat(vagasDela).hasSize(1);
        assertThat(vagasDela.get(0).get("vagaId").asLong()).isEqualTo(vagaElisa);
        assertThat(vagasDela.get(0).get("encaminhamentoId").asLong()).isEqualTo(encDaElisa);
        assertThat(vagasDela.get(0).get("pacienteNome").asText()).isNotBlank();
        assertThat(leJson(restTemplate.exchange(url("/agenda/vagas?status=DISPONIVEL&de=" + dia + "&ate=" + dia), HttpMethod.GET,
                new HttpEntity<>(headersComToken(tokenElisa)), String.class).getBody())).isEmpty();

        assertThat(getStatus("/agenda/encaminhamentos?situacao=QUALQUER", tokenElisa)).isEqualTo(HttpStatus.BAD_REQUEST);
        // O laboratório é especialista: tem a própria agenda.
        assertThat(getStatus("/agenda/encaminhamentos", login("lab.central", "Senha123!"))).isEqualTo(HttpStatus.OK);
        for (String token : List.of(tokenMedico, tokenSecretaria)) {
            assertThat(getStatus("/agenda/encaminhamentos", token)).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(getStatus("/agenda/vagas", token)).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
