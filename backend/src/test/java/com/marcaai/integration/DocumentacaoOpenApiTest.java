package com.marcaai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trava a documentação da API (Swagger): toda rota nova precisa chegar documentada — resumo, descrição,
 * respostas de sucesso e de erro, parâmetros e campos dos modelos. Uma rota sem documentação quebra o build.
 * Também confere que a coleção Postman chama todas as rotas. O documento gerado fica em {@code target/openapi.json},
 * para conferir ou importar em outra ferramenta.
 */
class DocumentacaoOpenApiTest extends IntegracaoBase {

    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    private JsonNode documento;

    @BeforeEach
    void carregarDocumento() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(Path.of("target", "openapi.json"), json, StandardCharsets.UTF_8);
        documento = new ObjectMapper().readTree(json);
    }

    private List<Map.Entry<String, JsonNode>> operacoes() {
        List<Map.Entry<String, JsonNode>> operacoes = new ArrayList<>();
        documento.get("paths").fields().forEachRemaining(caminho -> caminho.getValue().fields().forEachRemaining(metodo -> {
            if (METODOS.contains(metodo.getKey())) {
                operacoes.add(Map.entry(metodo.getKey().toUpperCase() + " " + caminho.getKey(), metodo.getValue()));
            }
        }));
        return operacoes;
    }

    @Test
    void documentaTodasAsRotasDaApi() {
        assertThat(operacoes()).hasSize(40);
    }

    @Test
    void todaOperacaoTemResumoDescricaoESecao() {
        for (Map.Entry<String, JsonNode> operacao : operacoes()) {
            JsonNode op = operacao.getValue();
            assertThat(op.path("summary").asText()).as("summary de %s", operacao.getKey()).isNotBlank();
            assertThat(op.path("description").asText()).as("description de %s", operacao.getKey()).isNotBlank();
            assertThat(op.path("tags")).as("tag de %s", operacao.getKey()).hasSize(1);
        }
    }

    @Test
    void todaOperacaoDeclaraSucessoEErrosComOCorpoPadrao() {
        for (Map.Entry<String, JsonNode> operacao : operacoes()) {
            JsonNode respostas = operacao.getValue().get("responses");
            List<String> codigos = new ArrayList<>();
            respostas.fieldNames().forEachRemaining(codigos::add);

            assertThat(codigos).as("respostas de %s", operacao.getKey())
                    .anyMatch(codigo -> codigo.startsWith("2"))
                    .contains("429", "500");
            respostas.fields().forEachRemaining(resposta -> {
                assertThat(resposta.getValue().path("description").asText())
                        .as("descrição da resposta %s de %s", resposta.getKey(), operacao.getKey()).isNotBlank();
                if (!resposta.getKey().startsWith("2")) {
                    assertThat(resposta.getValue().at("/content/application~1json/schema/$ref").asText())
                            .as("corpo de erro %s de %s", resposta.getKey(), operacao.getKey())
                            .isEqualTo("#/components/schemas/RespostaErro");
                }
            });
        }
    }

    @Test
    void soOLoginEPublicoETodoORestoPedeTokenEDocumenta401() {
        for (Map.Entry<String, JsonNode> operacao : operacoes()) {
            JsonNode op = operacao.getValue();
            boolean publica = op.has("security") && op.get("security").isEmpty();
            assertThat(publica).as("rota pública: %s", operacao.getKey()).isEqualTo(operacao.getKey().equals("POST /auth/login"));
            assertThat(op.get("responses").has("401")).as("401 em %s", operacao.getKey()).isTrue();
        }
    }

    @Test
    void todoParametroTemDescricao() {
        for (Map.Entry<String, JsonNode> operacao : operacoes()) {
            for (JsonNode parametro : operacao.getValue().path("parameters")) {
                assertThat(parametro.path("description").asText())
                        .as("parâmetro '%s' de %s", parametro.path("name").asText(), operacao.getKey()).isNotBlank();
            }
        }
    }

    /**
     * A coleção Postman (entregável do hackathon, junto do Swagger) precisa chamar toda rota da API ao menos uma vez.
     * Parâmetro de caminho vira curinga dos dois lados: {@code {{encaminhamentoId}}} ou {@code 1} casam com {@code {id}}.
     */
    @Test
    void colecaoPostmanChamaTodasAsRotasDaApi() throws Exception {
        Path colecao = Path.of("..", "postman", "marcaai.postman_collection.json");
        assumeTrue(Files.exists(colecao), "coleção Postman fora do módulo (build sem o repositório inteiro)");

        List<String> chamadas = new ArrayList<>();
        coletarRequisicoes(new ObjectMapper().readTree(colecao.toFile()).get("item"), chamadas);

        List<String> semRequisicao = new ArrayList<>();
        for (Map.Entry<String, JsonNode> operacao : operacoes()) {
            String[] metodoECaminho = operacao.getKey().split(" ", 2);
            Pattern rota = Pattern.compile(metodoECaminho[0] + " " + metodoECaminho[1].replaceAll("\\{[^/]+}", "[^/]+"));
            if (chamadas.stream().noneMatch(chamada -> rota.matcher(chamada).matches())) {
                semRequisicao.add(operacao.getKey());
            }
        }
        assertThat(semRequisicao).as("rotas sem requisição na coleção Postman").isEmpty();
    }

    private static void coletarRequisicoes(JsonNode itens, List<String> chamadas) {
        for (JsonNode item : itens) {
            if (item.has("item")) {
                coletarRequisicoes(item.get("item"), chamadas);
                continue;
            }
            JsonNode requisicao = item.get("request");
            String url = requisicao.get("url").isTextual() ? requisicao.get("url").asText() : requisicao.at("/url/raw").asText();
            String caminho = url.replace("{{baseUrl}}", "").split("\\?")[0].replaceAll("\\{\\{[^}]+}}", "X");
            chamadas.add(requisicao.get("method").asText() + " " + caminho);
        }
    }

    @Test
    void todoModeloETodoCampoTemDescricao() {
        Iterator<Map.Entry<String, JsonNode>> schemas = documento.at("/components/schemas").fields();
        while (schemas.hasNext()) {
            Map.Entry<String, JsonNode> schema = schemas.next();
            assertThat(schema.getValue().path("description").asText()).as("descrição do modelo %s", schema.getKey()).isNotBlank();
            schema.getValue().path("properties").fields().forEachRemaining(campo -> {
                JsonNode propriedade = campo.getValue();
                // Campo que aponta para outro modelo herda a descrição dele (o OpenAPI 3.0 ignora irmãos de $ref).
                if (!propriedade.has("$ref")) {
                    assertThat(propriedade.path("description").asText())
                            .as("campo %s.%s", schema.getKey(), campo.getKey()).isNotBlank();
                }
            });
        }
    }
}
