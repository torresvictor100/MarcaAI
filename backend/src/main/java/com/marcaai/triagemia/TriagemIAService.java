package com.marcaai.triagemia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.encaminhamento.*;
import com.marcaai.fila.FilaService;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de regras determinístico: SEMPRE a fonte da verdade do score de prioridade e do bloqueio por
 * irregularidade (ADR-004). A única parte que passa por uma API externa é a justificativa em texto,
 * via {@link JustificativaGenerator} — trocável sem alterar nenhuma regra abaixo (Dependency Inversion).
 */
@Service
@RequiredArgsConstructor
public class TriagemIAService {

    private static final Map<com.marcaai.atendimento.ClassificacaoRisco, Double> PESO_RISCO = new EnumMap<>(Map.of(
            com.marcaai.atendimento.ClassificacaoRisco.VERMELHO, 100.0,
            com.marcaai.atendimento.ClassificacaoRisco.LARANJA, 75.0,
            com.marcaai.atendimento.ClassificacaoRisco.AMARELO, 50.0,
            com.marcaai.atendimento.ClassificacaoRisco.VERDE, 25.0,
            com.marcaai.atendimento.ClassificacaoRisco.AZUL, 10.0));

    private static final double BONUS_URGENCIA = 30.0;
    private static final double PESO_TEMPO_ESPERA_POR_DIA = 0.5;

    private final EncaminhamentoService encaminhamentoService;
    private final AtendimentoService atendimentoService;
    private final FilaService filaService;
    private final AnaliseIARepository analiseIARepository;
    private final JustificativaGenerator justificativaGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AnaliseIA analisar(Long encaminhamentoId) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(encaminhamentoId);
        Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
        Cid cid = encaminhamentoService.buscarCid(encaminhamento.getCidId());
        List<Documento> documentos = encaminhamentoService.listarDocumentos(encaminhamentoId);

        List<Irregularidade> irregularidades = checarIrregularidades(encaminhamento, documentos, cid, atendimento);
        boolean bloqueado = irregularidades.stream().anyMatch(i -> i.severidade() == Severidade.BLOQUEANTE);

        Map<String, Object> fatores = calcularFatores(atendimento, encaminhamento);
        double score = bloqueado ? 0.0 : somarScore(fatores);

        ContextoJustificativa contexto = new ContextoJustificativa(
                encaminhamentoId, encaminhamento.getEspecialidadeOuExame(), score, fatores, irregularidades, bloqueado);
        String justificativa = justificativaGenerator.gerar(contexto);

        AnaliseIA analise = analiseIARepository.findByEncaminhamentoId(encaminhamentoId)
                .orElse(AnaliseIA.builder().encaminhamentoId(encaminhamentoId).build());
        analise.setScorePrioridade(score);
        analise.setFatoresConsiderados(paraJson(fatores));
        analise.setIrregularidades(paraJson(irregularidades));
        analise.setJustificativaTexto(justificativa);
        analise.setBloqueado(bloqueado);
        analise.setDataAnalise(LocalDateTime.now());
        analiseIARepository.save(analise);

        if (bloqueado) {
            encaminhamentoService.atualizarStatus(encaminhamentoId, StatusEncaminhamento.BLOQUEADO_REVISAO);
        } else {
            encaminhamentoService.atualizarStatus(encaminhamentoId, StatusEncaminhamento.NA_FILA);
            Long profissionalId = encaminhamentoService.profissionalIdDoEncaminhamento(encaminhamentoId);
            filaService.adicionarNaFila(encaminhamentoId, encaminhamento.getEspecialidadeOuExame(), score, profissionalId);
        }

        return analise;
    }

    public AnaliseIA buscarPorEncaminhamento(Long encaminhamentoId) {
        return analiseIARepository.findByEncaminhamentoId(encaminhamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Ainda não há análise de IA para o encaminhamento " + encaminhamentoId));
    }

    List<Irregularidade> checarIrregularidades(
            Encaminhamento encaminhamento, List<Documento> documentos, Cid cid, Atendimento atendimento) {
        List<Irregularidade> irregularidades = new ArrayList<>();

        boolean cidCompativel = cid.getEspecialidadesCompativeis().stream()
                .anyMatch(esp -> esp.equalsIgnoreCase(encaminhamento.getEspecialidadeOuExame()));
        if (!cidCompativel) {
            irregularidades.add(new Irregularidade(
                    "CID %s (%s) não é compatível com a especialidade/exame solicitado (%s)"
                            .formatted(cid.getCodigo(), cid.getDescricao(), encaminhamento.getEspecialidadeOuExame()),
                    Severidade.BLOQUEANTE));
        }

        LocalDate hoje = LocalDate.now();
        for (Documento documento : documentos) {
            if (documento.getValidade() != null && documento.getValidade().isBefore(hoje)) {
                irregularidades.add(new Irregularidade(
                        "Documento '%s' está vencido desde %s".formatted(documento.getTipo(), documento.getValidade()),
                        Severidade.BLOQUEANTE));
            }
        }

        List<String> faltantes = encaminhamentoService.tiposDocumentoFaltantes(encaminhamento, documentos);
        if (!faltantes.isEmpty()) {
            irregularidades.add(new Irregularidade(
                    "Faltam documentos exigidos para %s: %s".formatted(encaminhamento.getEspecialidadeOuExame(),
                            String.join(", ", faltantes.stream().map(TiposDocumento::rotulo).toList())),
                    Severidade.ALERTA));
        }

        boolean duplicado = encaminhamentoService.existeOutroEncaminhamentoAtivo(
                atendimento.getPacienteId(), encaminhamento.getEspecialidadeOuExame(), encaminhamento.getId());
        if (duplicado) {
            irregularidades.add(new Irregularidade(
                    "Já existe outro encaminhamento ativo do mesmo paciente para " + encaminhamento.getEspecialidadeOuExame(),
                    Severidade.ALERTA));
        }

        return irregularidades;
    }

    Map<String, Object> calcularFatores(Atendimento atendimento, Encaminhamento encaminhamento) {
        Map<String, Object> fatores = new LinkedHashMap<>();
        double pesoRisco = PESO_RISCO.get(atendimento.getClassificacaoRisco());
        fatores.put("classificacaoRisco", atendimento.getClassificacaoRisco().name());
        fatores.put("pesoRisco", pesoRisco);

        long diasDesdeAtendimento = Math.max(0, Duration.between(atendimento.getData(), LocalDateTime.now()).toDays());
        double bonusTempoEspera = diasDesdeAtendimento * PESO_TEMPO_ESPERA_POR_DIA;
        fatores.put("diasDesdeAtendimento", diasDesdeAtendimento);
        fatores.put("bonusTempoEspera", bonusTempoEspera);

        fatores.put("urgenteMarcadoPeloMedico", encaminhamento.isUrgente());
        fatores.put("bonusUrgencia", encaminhamento.isUrgente() ? BONUS_URGENCIA : 0.0);
        if (encaminhamento.isUrgente()) {
            fatores.put("justificativaUrgencia", encaminhamento.getJustificativaUrgencia());
        }

        return fatores;
    }

    private double somarScore(Map<String, Object> fatores) {
        double pesoRisco = (double) fatores.get("pesoRisco");
        double bonusTempoEspera = (double) fatores.get("bonusTempoEspera");
        double bonusUrgencia = (double) fatores.get("bonusUrgencia");
        return pesoRisco + bonusTempoEspera + bonusUrgencia;
    }

    private String paraJson(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao serializar resultado da triagem de IA", ex);
        }
    }
}
