package com.marcaai.painel;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaDetalhesService;
import com.marcaai.fila.FilaItemResponse;
import com.marcaai.fila.FilaService;
import com.marcaai.vagasagendamento.AgendamentoService;
import com.marcaai.vagasagendamento.StatusVaga;
import com.marcaai.vagasagendamento.VagaHorario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Indicadores do painel da secretaria. Só lê, pelos serviços dos módulos donos de cada dado.
 * Com o volume da demonstração (dezenas de itens na fila) as consultas por item são instantâneas; com
 * milhares seria o caso de trocar por consultas agregadas.
 */
@Service
@RequiredArgsConstructor
public class PainelIndicadoresService {

    /** Ordem do funil de um encaminhamento, da entrada ao desfecho. */
    static final List<StatusEncaminhamento> ORDEM_SITUACOES = List.of(
            StatusEncaminhamento.AGUARDANDO_DOCUMENTOS, StatusEncaminhamento.EM_ANALISE,
            StatusEncaminhamento.BLOQUEADO_REVISAO, StatusEncaminhamento.NA_FILA, StatusEncaminhamento.AGENDADO,
            StatusEncaminhamento.REALIZADO, StatusEncaminhamento.CANCELADO);

    static final int DIAS_MOVIMENTO = 30;

    private final EncaminhamentoService encaminhamentoService;
    private final AtendimentoService atendimentoService;
    private final FilaService filaService;
    private final FilaDetalhesService filaDetalhesService;
    private final AgendamentoService agendamentoService;

    public PainelIndicadoresResponse indicadores() {
        LocalDateTime agora = LocalDateTime.now();
        List<String> especialidades = encaminhamentoService.listarEspecialidades();

        List<PainelIndicadoresResponse.QuantidadePorSituacao> situacoes = ORDEM_SITUACOES.stream()
                .map(s -> new PainelIndicadoresResponse.QuantidadePorSituacao(s, encaminhamentoService.contarPorStatus(s)))
                .toList();

        Map<String, List<FilaItemResponse>> filaPorEspecialidade = new java.util.LinkedHashMap<>();
        for (String especialidade : especialidades) {
            filaPorEspecialidade.put(especialidade, filaDetalhesService.detalhar(filaService.listarPorEspecialidade(especialidade)));
        }
        List<FilaItemResponse> filaInteira = filaPorEspecialidade.values().stream().flatMap(List::stream).toList();
        List<FilaItemResponse> aguardando = filaInteira.stream()
                .filter(i -> i.statusEncaminhamento() == StatusEncaminhamento.NA_FILA).toList();

        List<PainelIndicadoresResponse.FilaPorRisco> filaPorRisco = new ArrayList<>();
        filaPorEspecialidade.forEach((especialidade, itens) -> {
            if (itens.isEmpty()) {
                return;
            }
            Map<ClassificacaoRisco, Long> porRisco = new EnumMap<>(ClassificacaoRisco.class);
            for (ClassificacaoRisco risco : ClassificacaoRisco.values()) {
                porRisco.put(risco, itens.stream().filter(i -> i.classificacaoRisco() == risco).count());
            }
            filaPorRisco.add(new PainelIndicadoresResponse.FilaPorRisco(especialidade, porRisco, itens.size()));
        });

        List<VagaHorario> vagasFuturas = agendamentoService.listarVagasFuturas();
        Map<String, Long> livresPorEspecialidade = vagasFuturas.stream()
                .filter(v -> v.getStatus() == StatusVaga.DISPONIVEL)
                .collect(Collectors.groupingBy(v -> v.getEspecialidadeOuExame().toLowerCase(), Collectors.counting()));
        List<PainelIndicadoresResponse.DemandaCapacidade> demandaCapacidade = especialidades.stream()
                .map(especialidade -> new PainelIndicadoresResponse.DemandaCapacidade(especialidade,
                        filaPorEspecialidade.get(especialidade).stream()
                                .filter(i -> i.statusEncaminhamento() == StatusEncaminhamento.NA_FILA).count(),
                        livresPorEspecialidade.getOrDefault(especialidade.toLowerCase(), 0L)))
                .filter(d -> d.aguardandoAgendamento() > 0 || d.vagasLivres() > 0)
                .toList();

        long ocupadas = vagasFuturas.stream().filter(v -> v.getStatus() == StatusVaga.OCUPADA).count();
        Double ocupacao = vagasFuturas.isEmpty() ? null : arredondar(100.0 * ocupadas / vagasFuturas.size());

        PainelIndicadoresResponse.Numeros numeros = new PainelIndicadoresResponse.Numeros(
                filaInteira.size(),
                aguardando.stream().filter(i -> i.classificacaoRisco() == ClassificacaoRisco.VERMELHO).count(),
                esperaMedia(aguardando, agora),
                ocupacao,
                encaminhamentoService.contarPorStatus(StatusEncaminhamento.BLOQUEADO_REVISAO));

        return new PainelIndicadoresResponse(numeros, situacoes, filaPorRisco, demandaCapacidade, movimento(agora));
    }

    private Double esperaMedia(List<FilaItemResponse> aguardando, LocalDateTime agora) {
        if (aguardando.isEmpty()) {
            return null;
        }
        double media = aguardando.stream()
                .mapToLong(item -> {
                    Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(item.encaminhamentoId());
                    Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
                    return Math.max(0, Duration.between(atendimento.getData(), agora).toDays());
                })
                .average()
                .orElse(0);
        return arredondar(media);
    }

    private List<PainelIndicadoresResponse.MovimentoDia> movimento(LocalDateTime agora) {
        LocalDate hoje = agora.toLocalDate();
        LocalDate primeiroDia = hoje.minusDays(DIAS_MOVIMENTO - 1L);
        List<Atendimento> atendimentos = atendimentoService.listarNoPeriodo(primeiroDia.atStartOfDay(), hoje.plusDays(1).atStartOfDay());
        Map<Long, LocalDate> diaPorAtendimento = atendimentos.stream()
                .collect(Collectors.toMap(Atendimento::getId, a -> a.getData().toLocalDate()));
        Map<LocalDate, Long> atendimentosPorDia = atendimentos.stream()
                .collect(Collectors.groupingBy(a -> a.getData().toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> encaminhamentosPorDia = encaminhamentoService
                .listarPorAtendimentos(new ArrayList<>(diaPorAtendimento.keySet())).stream()
                .collect(Collectors.groupingBy(e -> diaPorAtendimento.get(e.getAtendimentoId()), Collectors.counting()));

        return primeiroDia.datesUntil(hoje.plusDays(1))
                .map(dia -> new PainelIndicadoresResponse.MovimentoDia(dia,
                        atendimentosPorDia.getOrDefault(dia, 0L), encaminhamentosPorDia.getOrDefault(dia, 0L)))
                .toList();
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
