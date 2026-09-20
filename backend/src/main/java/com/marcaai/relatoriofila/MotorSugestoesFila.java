package com.marcaai.relatoriofila;

import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras determinísticas que encontram o que merece atenção numa fila (ADR-008, mesma linha da ADR-004):
 * a IA nunca escolhe quem sugerir — só redige o texto a partir do que este motor encontrou. Não altera a
 * fila: a secretaria decide pelo ajuste manual ou pelo "Antecipar".
 */
@Component
public class MotorSugestoesFila {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final List<ClassificacaoRisco> RISCO_ALTO = List.of(ClassificacaoRisco.LARANJA, ClassificacaoRisco.VERMELHO);

    private final int diasParaAntecipar;
    private final int diasEsperaLonga;

    public MotorSugestoesFila(
            @Value("${marcaai.relatorio-fila.dias-para-antecipar:7}") int diasParaAntecipar,
            @Value("${marcaai.relatorio-fila.dias-espera-longa:30}") int diasEsperaLonga) {
        this.diasParaAntecipar = diasParaAntecipar;
        this.diasEsperaLonga = diasEsperaLonga;
    }

    public List<SugestaoFila> sugerir(List<ItemAnalise> itens, List<VagaLivre> vagasLivres, LocalDateTime agora) {
        List<ItemAnalise> aguardando = itens.stream()
                .filter(i -> i.status() == StatusEncaminhamento.NA_FILA)
                .sorted(Comparator.comparingInt(ItemAnalise::posicao))
                .toList();
        List<VagaLivre> vagasFuturas = vagasLivres.stream()
                .filter(v -> v.dataHora().isAfter(agora))
                .sorted(Comparator.comparing(VagaLivre::dataHora))
                .toList();

        List<SugestaoFila> sugestoes = new ArrayList<>();
        for (ItemAnalise item : aguardando) {
            subirNaFila(item, aguardando).ifPresent(sugestoes::add);
            if (item.urgente()) {
                sugestoes.add(sugestao(item, TipoSugestao.URGENTE_SEM_VAGA, PrioridadeSugestao.ALTA,
                        "Marcado como urgente pelo médico e ainda sem agendamento.", null));
            }
            long diasEsperando = item.dataAtendimento() == null ? 0 : Duration.between(item.dataAtendimento(), agora).toDays();
            if (diasEsperando > diasEsperaLonga) {
                sugestoes.add(sugestao(item, TipoSugestao.ESPERA_LONGA,
                        ehRiscoAlto(item) ? PrioridadeSugestao.ALTA : PrioridadeSugestao.MEDIA,
                        "Aguardando agendamento há %d dias desde o atendimento.".formatted(diasEsperando), null));
            }
        }
        for (ItemAnalise item : itens) {
            antecipar(item, vagasFuturas, agora).ifPresent(sugestoes::add);
        }
        if (aguardando.size() > vagasFuturas.size()) {
            sugestoes.add(new SugestaoFila(TipoSugestao.FALTA_DE_VAGAS, PrioridadeSugestao.MEDIA, null, null, null, null,
                    "%d paciente(s) aguardando agendamento e só %d vaga(s) livre(s) — considerar abrir mais horários."
                            .formatted(aguardando.size(), vagasFuturas.size()),
                    null, null));
        }

        sugestoes.sort(Comparator.comparing(SugestaoFila::prioridade)
                .thenComparing(s -> s.posicao() == null ? Integer.MAX_VALUE : s.posicao()));
        return sugestoes;
    }

    /** Risco alto atrás de alguém (também aguardando) com risco menor. */
    private Optional<SugestaoFila> subirNaFila(ItemAnalise item, List<ItemAnalise> aguardando) {
        if (!ehRiscoAlto(item)) {
            return Optional.empty();
        }
        List<ItemAnalise> menoresNaFrente = aguardando.stream()
                .filter(outro -> outro.posicao() < item.posicao())
                .filter(outro -> outro.risco().ordinal() < item.risco().ordinal())
                .toList();
        if (menoresNaFrente.isEmpty()) {
            return Optional.empty();
        }
        int posicaoAlvo = menoresNaFrente.get(0).posicao();
        return Optional.of(sugestao(item, TipoSugestao.SUBIR_NA_FILA, prioridadePorRisco(item),
                "Risco %s na posição %d, atrás de %d paciente(s) aguardando com risco menor; sugestão: posição %d."
                        .formatted(item.risco().name().toLowerCase(), item.posicao(), menoresNaFrente.size(), posicaoAlvo),
                null));
    }

    /** Agendado com risco alto para além do limite de dias, havendo vaga livre antes da data atual. */
    private Optional<SugestaoFila> antecipar(ItemAnalise item, List<VagaLivre> vagasFuturas, LocalDateTime agora) {
        if (item.status() != StatusEncaminhamento.AGENDADO || item.dataAgendamento() == null || !ehRiscoAlto(item)) {
            return Optional.empty();
        }
        long diasAteConsulta = Duration.between(agora, item.dataAgendamento()).toDays();
        if (diasAteConsulta <= diasParaAntecipar) {
            return Optional.empty();
        }
        return vagasFuturas.stream()
                .filter(v -> v.dataHora().isBefore(item.dataAgendamento()))
                .findFirst()
                .map(vaga -> sugestao(item, TipoSugestao.ANTECIPAR, prioridadePorRisco(item),
                        "Risco %s agendado para %s (daqui a %d dias); há vaga livre em %s."
                                .formatted(item.risco().name().toLowerCase(), DATA.format(item.dataAgendamento()),
                                        diasAteConsulta, DATA.format(vaga.dataHora())),
                        vaga));
    }

    private static boolean ehRiscoAlto(ItemAnalise item) {
        return item.risco() != null && RISCO_ALTO.contains(item.risco());
    }

    private static PrioridadeSugestao prioridadePorRisco(ItemAnalise item) {
        return item.risco() == ClassificacaoRisco.VERMELHO ? PrioridadeSugestao.ALTA : PrioridadeSugestao.MEDIA;
    }

    private static SugestaoFila sugestao(ItemAnalise item, TipoSugestao tipo, PrioridadeSugestao prioridade, String motivo,
                                         VagaLivre vaga) {
        return new SugestaoFila(tipo, prioridade, item.encaminhamentoId(), item.pacienteNome(), item.posicao(), item.risco(),
                motivo, vaga == null ? null : vaga.id(), vaga == null ? null : vaga.dataHora());
    }
}
