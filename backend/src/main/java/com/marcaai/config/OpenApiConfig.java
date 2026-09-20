package com.marcaai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Documentação OpenAPI (Swagger UI em {@code /swagger-ui.html}). Os controllers descrevem o que é de cada
 * endpoint (papéis, 400/403/404/409/422); o que vale para todos fica aqui, num lugar só:
 * <ul>
 *   <li>o corpo único de erro ({@code RespostaErro}, ADR-011), aplicado a toda resposta 4xx/5xx, com exemplo;</li>
 *   <li>401 em toda rota protegida, e 429 e 500 em todas.</li>
 * </ul>
 * Rota pública é a que declara {@code @SecurityRequirements} vazio (só o login).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String SCHEMA_ERRO = "RespostaErro";
    private static final String REF_ERRO = "#/components/schemas/" + SCHEMA_ERRO;

    /** Ordem das seções na Swagger UI: a ordem do fluxo do paciente, da UBS até o resultado. */
    private static final List<Tag> TAGS = List.of(
            tag("Autenticação", "Login por usuário e senha. Devolve o token JWT usado em todas as outras rotas."),
            tag("Cadastro", "Profissionais, unidades de saúde e busca de pacientes pelo nome."),
            tag("Atendimento", "Registro do atendimento do paciente na UBS, ponto de partida de todo encaminhamento."),
            tag("CID", "Catálogo fechado de CIDs, especialidades/exames e documentos exigidos por especialidade."),
            tag("Encaminhamento", "Encaminhamento da UBS para especialista ou exame, com CID, urgência e documentos."),
            tag("Paciente", "Encaminhamentos de um paciente, na visão do próprio paciente, do médico que encaminhou ou da gestão."),
            tag("Análise de IA", "Resultado da triagem: score de prioridade, irregularidades e justificativa. "
                    + "O motor de regras decide; a IA só redige o texto (ADR-004)."),
            tag("Fila", "Fila priorizada pelo score da triagem, com ajuste manual auditado pela secretaria."),
            tag("Relatório IA da fila", "Sugestões sobre uma fila. As regras decidem as sugestões; a IA só redige (ADR-008)."),
            tag("Vagas", "Horários disponíveis na rede e abertura de vagas em lote."),
            tag("Agendamento", "Agendamento a partir da fila: marcar, cancelar, remarcar, antecipar e confirmar presença."),
            tag("Agenda do especialista", "Encaminhamentos e vagas do especialista logado."),
            tag("Resultado de exame", "Resultado/laudo do exame ou registro da consulta realizada, que tira o paciente da fila."),
            tag("Painel", "Métricas e indicadores da rede para a secretaria de saúde."));

    private static final String DESCRICAO = """
            API do **MarcaAI**: encaminhamento digital da UBS para especialista ou exame, com uma fila priorizada \
            por risco clínico real, não por ordem de chegada.

            ## Como autenticar
            1. Chame `POST /auth/login` com `login` e `senha`.
            2. Copie o `token` da resposta.
            3. Clique em **Authorize** (cadeado) e cole só o token, sem a palavra `Bearer`.

            O token vale por tempo limitado. Sem token, ou com token inválido/expirado, a resposta é **401**. \
            Cinco senhas erradas seguidas bloqueiam aquele login por 5 minutos (**429**).

            ## Papéis
            | Papel | O que faz |
            |---|---|
            | `PACIENTE` | Vê os próprios encaminhamentos, posição na fila, agendamento e resultado; confirma presença. |
            | `MEDICO_UBS` | Registra atendimento, cria encaminhamento, anexa documentos e acompanha a fila de quem encaminhou. |
            | `ESPECIALISTA` | Vê só a própria agenda e registra a consulta ou o exame realizado (o laboratório também usa este papel). |
            | `SECRETARIA` | Vê todas as filas, agenda, faz ajuste manual com justificativa, abre vagas e acessa o painel. |
            | `ADMIN` | Tudo o que a secretaria faz, mais a administração geral. |

            A leitura de dado clínico é por vínculo (ADR-007): cada papel só lê o que é dele. Pedir o dado de outro \
            paciente ou de outro profissional dá **403**, mesmo com o id certo.

            ## Usuários de demonstração
            Existem só na massa de demonstração (migrations de seed). Senha de todos: `Senha123!`.
            `bruno.ubs` (médico UBS) · `elisa.cardio` (especialista de Cardiologia) · `lab.central` (especialista de Hemograma Completo) · \
            `secretaria` · `admin` · `marta.paciente`, `joao.paciente`, `carlos.paciente` (pacientes).

            ## Erros
            Todo erro tem o mesmo corpo (`RespostaErro`). Decida pelo campo `erro`, que é estável; o texto de \
            `mensagem` pode mudar.
            | HTTP | `erro` | Quando |
            |---|---|---|
            | 400 | `DADOS_INVALIDOS`, `JSON_INVALIDO`, `PARAMETRO_INVALIDO` | Corpo, JSON ou parâmetro inválido. Em `campos`, o que falhou. |
            | 401 | `NAO_AUTENTICADO`, `CREDENCIAIS_INVALIDAS` | Sem token, token inválido ou expirado; login ou senha errados. |
            | 403 | `ACESSO_NEGADO` | O papel não pode usar a rota, ou o dado não é do usuário logado. |
            | 404 | `RECURSO_NAO_ENCONTRADO`, `ROTA_NAO_ENCONTRADA` | O id não existe, ou ainda não há o dado pedido. |
            | 409 | `CONFLITO` | O estado atual não permite (vaga ocupada, resultado já registrado, gravação concorrente). |
            | 422 | `REGRA_DE_NEGOCIO` | O pedido é válido, mas fere uma regra (CID fora da lista, vaga de outra especialidade). |
            | 429 | `LIMITE_EXCEDIDO` | Muitas requisições em pouco tempo, ou login bloqueado. |
            | 500 | `ERRO_INTERNO` | Falha do servidor. O detalhe fica só no log. |

            ## Datas
            Data: `AAAA-MM-DD`. Data e hora: `AAAA-MM-DDTHH:MM:SS`, no horário de Brasília, sem fuso.
            """;

    static {
        // O Jackson escreve LocalTime como "08:00:00"; sem isto o springdoc o documentaria como objeto (hour, minute...).
        SpringDocUtils.getConfig().replaceWithSchema(LocalTime.class,
                new StringSchema().format("time").description("Horário HH:MM:SS").example("08:00:00"));
    }

    @Bean
    public OpenAPI marcaAiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MarcaAI API")
                        .description(DESCRICAO)
                        .version("v1")
                        .contact(new Contact()
                                .name("João Victor Torres e Lucas Bejamin — Hackathon Fase 5, Pós-Tech Java (FIAP)")))
                .tags(TAGS)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token devolvido por POST /auth/login. Cole só o token, sem 'Bearer'."))
                        .addSchemas(SCHEMA_ERRO, schemaRespostaErro()));
    }

    /**
     * Completa as respostas de todas as operações: 401 nas protegidas, 429 e 500 em todas, e o corpo
     * {@code RespostaErro} (com exemplo do código certo) em toda resposta de erro declarada nos controllers.
     */
    @Bean
    public OpenApiCustomizer respostasDeErroComuns() {
        return openApi -> openApi.getPaths().forEach((caminho, item) -> item.readOperations().forEach(operacao -> {
            ApiResponses respostas = operacao.getResponses();
            if (!ehPublica(operacao)) {
                respostas.putIfAbsent("401", new ApiResponse().description("Sem token, ou token inválido ou expirado"));
            }
            respostas.putIfAbsent("429", new ApiResponse().description("Muitas requisições em pouco tempo; aguarde e tente de novo"));
            respostas.putIfAbsent("500", new ApiResponse().description("Erro interno inesperado (o detalhe fica só no log do servidor)"));
            respostas.forEach((codigo, resposta) -> {
                if (codigo.startsWith("4") || codigo.startsWith("5")) {
                    resposta.setContent(corpoDeErro(codigo, resposta.getDescription(), caminho, ehPublica(operacao)));
                }
            });
            // Na Swagger UI as respostas aparecem na ordem do mapa: sucesso primeiro, depois os erros em ordem.
            Map<String, ApiResponse> ordenadas = new TreeMap<>(respostas);
            respostas.clear();
            respostas.putAll(ordenadas);
        }));
    }

    /** {@code @SecurityRequirements} vazio no método vira {@code security: []} — a rota não pede token. */
    private static boolean ehPublica(Operation operacao) {
        return operacao.getSecurity() != null && operacao.getSecurity().isEmpty();
    }

    private static Content corpoDeErro(String codigoHttp, String mensagem, String caminho, boolean publica) {
        CodigoErro codigo = codigoDeExemplo(codigoHttp, publica);
        Map<String, Object> exemplo = new LinkedHashMap<>();
        exemplo.put("timestamp", "2026-09-24T12:00:00Z");
        exemplo.put("status", Integer.parseInt(codigoHttp));
        exemplo.put("erro", codigo.name());
        exemplo.put("mensagem", mensagem);
        exemplo.put("caminho", caminho);
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, new MediaType()
                .schema(new Schema<>().$ref(REF_ERRO))
                .addExamples(codigo.name(), new Example().value(exemplo)));
    }

    /** O código de erro mais comum para cada status HTTP; no login, o 401 é de credencial errada. */
    private static CodigoErro codigoDeExemplo(String codigoHttp, boolean publica) {
        if ("401".equals(codigoHttp) && publica) {
            return CodigoErro.CREDENCIAIS_INVALIDAS;
        }
        return switch (codigoHttp) {
            case "400" -> CodigoErro.DADOS_INVALIDOS;
            case "401" -> CodigoErro.NAO_AUTENTICADO;
            case "403" -> CodigoErro.ACESSO_NEGADO;
            case "404" -> CodigoErro.RECURSO_NAO_ENCONTRADO;
            case "409" -> CodigoErro.CONFLITO;
            case "422" -> CodigoErro.REGRA_DE_NEGOCIO;
            case "429" -> CodigoErro.LIMITE_EXCEDIDO;
            default -> CodigoErro.ERRO_INTERNO;
        };
    }

    @SuppressWarnings("rawtypes")
    private static Schema schemaRespostaErro() {
        Schema<?> campo = new ObjectSchema()
                .addProperty("campo", new StringSchema().description("Nome do campo ou parâmetro inválido").example("motivo"))
                .addProperty("mensagem", new StringSchema().description("O que está errado nele").example("não deve estar em branco"));
        return new ObjectSchema()
                .description("Corpo único de erro da API (ADR-011), igual em todas as rotas.")
                .addProperty("timestamp", new StringSchema().format("date-time")
                        .description("Momento do erro, em UTC").example("2026-09-24T12:00:00Z"))
                .addProperty("status", new IntegerSchema().description("Status HTTP, repetido no corpo").example(404))
                .addProperty("erro", new StringSchema()._enum(Arrays.stream(CodigoErro.values()).map(Enum::name).toList())
                        .description("Código estável do tipo de erro. O cliente decide por ele, não pelo texto.")
                        .example(CodigoErro.RECURSO_NAO_ENCONTRADO.name()))
                .addProperty("mensagem", new StringSchema()
                        .description("Explicação para quem usa. Nunca traz detalhe interno (stack trace, SQL).")
                        .example("Encaminhamento não encontrado: 999"))
                .addProperty("caminho", new StringSchema().description("Rota chamada").example("/encaminhamentos/999"))
                .addProperty("campos", new ArraySchema().items(campo)
                        .description("Só em erro de validação (DADOS_INVALIDOS): um item por campo inválido."))
                .required(List.of("timestamp", "status", "erro", "mensagem", "caminho"));
    }

    private static Tag tag(String nome, String descricao) {
        return new Tag().name(nome).description(descricao);
    }
}
