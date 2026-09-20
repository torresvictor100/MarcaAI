package com.marcaai.relatoriofila;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.config.AuthContext;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaDetalhesService;
import com.marcaai.fila.FilaItemResponse;
import com.marcaai.fila.FilaService;
import com.marcaai.vagasagendamento.AgendamentoService;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatório de sugestões de uma fila (ADR-008): junta os dados pelos serviços dos módulos donos, deixa o
 * {@link MotorSugestoesFila} decidir o que sugerir, pede o texto à IA (com template de reserva) e guarda
 * tudo com quem gerou e quando. Nunca altera a fila.
 */
@Service
@RequiredArgsConstructor
public class RelatorioFilaService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioFilaService.class);

    private final FilaService filaService;
    private final FilaDetalhesService filaDetalhesService;
    private final EncaminhamentoService encaminhamentoService;
    private final AtendimentoService atendimentoService;
    private final AgendamentoService agendamentoService;
    private final MotorSugestoesFila motor;
    private final AnthropicRelatorioRedator redatorIa;
    private final TemplateRelatorioRedator redatorModelo;
    private final RelatorioFilaRepository repository;
    private final ObjectMapper objectMapper;

    public RelatorioFilaResponse gerar(String especialidadeOuExame) {
        String especialidade = especialidadeDoCatalogo(especialidadeOuExame);
        LocalDateTime agora = LocalDateTime.now();

        List<ItemAnalise> itens = filaDetalhesService.detalhar(filaService.listarPorEspecialidade(especialidade)).stream()
                .map(this::paraAnalise)
                .toList();
        List<VagaLivre> vagas = agendamentoService.listarDisponiveisPorEspecialidade(especialidade).stream()
                .map(v -> new VagaLivre(v.getId(), v.getDataHora()))
                .toList();
        List<SugestaoFila> sugestoes = motor.sugerir(itens, vagas, agora);

        int aguardando = (int) itens.stream().filter(i -> i.status() == StatusEncaminhamento.NA_FILA).count();
        int vagasFuturas = (int) vagas.stream().filter(v -> v.dataHora().isAfter(agora)).count();
        ContextoRelatorio contexto = new ContextoRelatorio(especialidade, itens.size(), aguardando,
                itens.size() - aguardando, vagasFuturas, sugestoes);

        String texto;
        OrigemTexto origem;
        try {
            texto = redatorIa.redigir(contexto);
            origem = OrigemTexto.IA;
        } catch (RedacaoIndisponivelException ex) {
            log.warn("Relatório da fila de {}: IA indisponível ({}); usando o texto-padrão", especialidade, ex.getMessage());
            texto = redatorModelo.redigir(contexto);
            origem = OrigemTexto.MODELO;
        }

        RelatorioFila salvo = repository.save(RelatorioFila.builder()
                .especialidadeOuExame(especialidade)
                .geradoEm(agora)
                .geradoPor(AuthContext.atual().login())
                .totalNaFila(itens.size())
                .sugestoesJson(paraJson(sugestoes))
                .texto(texto)
                .origemTexto(origem)
                .build());
        return paraResposta(salvo);
    }

    public RelatorioFilaResponse buscarUltimo(String especialidadeOuExame) {
        String especialidade = especialidadeDoCatalogo(especialidadeOuExame);
        return repository.findFirstByEspecialidadeOuExameIgnoreCaseOrderByGeradoEmDescIdDesc(especialidade)
                .map(this::paraResposta)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ainda não há relatório para a fila de " + especialidade));
    }

    /** Aceita só especialidades/exames do catálogo (com a grafia oficial), para não gravar relatório de fila inexistente. */
    private String especialidadeDoCatalogo(String especialidadeOuExame) {
        return encaminhamentoService.listarEspecialidades().stream()
                .filter(e -> e.equalsIgnoreCase(especialidadeOuExame == null ? "" : especialidadeOuExame.trim()))
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("Especialidade/exame desconhecido: " + especialidadeOuExame));
    }

    private ItemAnalise paraAnalise(FilaItemResponse item) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(item.encaminhamentoId());
        Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
        LocalDateTime dataAgendamento = null;
        if (encaminhamento.getStatus() == StatusEncaminhamento.AGENDADO) {
            try {
                dataAgendamento = agendamentoService.buscarPorEncaminhamento(encaminhamento.getId()).getDataHora();
            } catch (RecursoNaoEncontradoException ex) {
                // Situação inconsistente (agendado sem agendamento em vigor): só não entra na regra de antecipar.
            }
        }
        return new ItemAnalise(encaminhamento.getId(), item.pacienteNome(), item.posicao(), encaminhamento.getStatus(),
                atendimento.getClassificacaoRisco(), encaminhamento.isUrgente(), atendimento.getData(), dataAgendamento);
    }

    private String paraJson(List<SugestaoFila> sugestoes) {
        try {
            return objectMapper.writeValueAsString(sugestoes);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao serializar as sugestões do relatório", ex);
        }
    }

    private RelatorioFilaResponse paraResposta(RelatorioFila relatorio) {
        try {
            List<SugestaoFila> sugestoes = objectMapper.readValue(relatorio.getSugestoesJson(), new TypeReference<>() {
            });
            return new RelatorioFilaResponse(relatorio.getId(), relatorio.getEspecialidadeOuExame(), relatorio.getGeradoEm(),
                    relatorio.getGeradoPor(), relatorio.getTotalNaFila(), relatorio.getOrigemTexto(), relatorio.getTexto(),
                    sugestoes);
        } catch (Exception ex) {
            throw new IllegalStateException("Relatório " + relatorio.getId() + " com sugestões ilegíveis", ex);
        }
    }
}
