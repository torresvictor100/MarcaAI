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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corrida no agendamento (ADR-011): várias pessoas tentando a mesma vaga no mesmo instante. Só uma pode
 * conseguir; as outras recebem 409 — nunca dois agendamentos ativos na mesma vaga.
 */
class ConcorrenciaAgendamentoTest extends IntegracaoBase {

    private static final int TENTATIVAS_SIMULTANEAS = 8;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders cabecalhos(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JsonNode get(String path, String token) throws Exception {
        return objectMapper.readTree(restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(cabecalhos(token)),
                String.class).getBody());
    }

    @Test
    void variasPessoasNaMesmaVagaAoMesmoTempoSoUmaConsegue() throws Exception {
        String token = objectMapper.readTree(restTemplate.postForEntity(url("/auth/login"),
                Map.of("login", "secretaria", "senha", "Senha123!"), String.class).getBody()).get("token").asText();

        // Especialidade com mais gente aguardando e com vaga livre (a massa muda conforme os outros testes).
        List<Long> aguardando = List.of();
        JsonNode vagas = null;
        var filas = get("/fila/todas", token).fields();
        while (filas.hasNext()) {
            var fila = filas.next();
            List<Long> naFila = new ArrayList<>();
            fila.getValue().forEach(item -> {
                if ("NA_FILA".equals(item.get("statusEncaminhamento").asText())) {
                    naFila.add(item.get("encaminhamentoId").asLong());
                }
            });
            JsonNode livres = get("/vagas?especialidade=" + fila.getKey().replace(" ", "%20"), token);
            if (!livres.isEmpty() && naFila.size() > aguardando.size()) {
                aguardando = naFila.subList(0, Math.min(TENTATIVAS_SIMULTANEAS, naFila.size()));
                vagas = livres;
            }
        }
        assertThat(aguardando).as("massa de demonstração com pacientes aguardando e vaga livre").hasSizeGreaterThanOrEqualTo(4);
        long vagaId = vagas.get(0).get("id").asLong();

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(TENTATIVAS_SIMULTANEAS);
        List<Future<ResponseEntity<String>>> respostas = new ArrayList<>();
        for (Long encaminhamentoId : aguardando) {
            respostas.add(executor.submit(() -> {
                largada.await();
                return restTemplate.exchange(url("/agendamentos"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("encaminhamentoId", encaminhamentoId, "vagaId", vagaId), cabecalhos(token)),
                        String.class);
            }));
        }
        largada.countDown();
        List<Integer> status = new ArrayList<>();
        Long agendamentoCriado = null;
        for (Future<ResponseEntity<String>> resposta : respostas) {
            ResponseEntity<String> r = resposta.get();
            status.add(r.getStatusCode().value());
            if (r.getStatusCode().value() == 201) {
                agendamentoCriado = objectMapper.readTree(r.getBody()).get("id").asLong();
            }
        }
        executor.shutdown();

        Integer ativosNaVaga = jdbc.queryForObject(
                "SELECT count(*) FROM agendamentos WHERE vaga_id = ? AND status <> 'CANCELADO'", Integer.class, vagaId);
        try {
            assertThat(ativosNaVaga).as("agendamentos ativos na vaga %d (status recebidos: %s)", vagaId, status).isEqualTo(1);
            assertThat(status).filteredOn(s -> s == 201).hasSize(1);
            assertThat(status).filteredOn(s -> s != 201).allMatch(s -> s == 409);
        } finally {
            // Devolve a massa de demonstração como estava: cancela o que foi criado (libera a vaga e volta à fila).
            jdbc.queryForList("SELECT id FROM agendamentos WHERE vaga_id = ? AND status <> 'CANCELADO'", Long.class, vagaId)
                    .forEach(id -> restTemplate.exchange(url("/agendamentos/" + id + "/cancelar"), HttpMethod.POST,
                            new HttpEntity<>(Map.of("motivo", "Limpeza do teste de concorrência"), cabecalhos(token)), String.class));
        }
        assertThat(agendamentoCriado).isNotNull();
    }
}
