package com.marcaai.painel;

import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tudo o que os gráficos do painel mostram, numa chamada só. */
@Schema(description = "Tudo o que os gráficos do painel mostram, numa chamada só")
public record PainelIndicadoresResponse(
        @Schema(description = "Números de destaque")
        Numeros numeros,
        @Schema(description = "Quantidade de encaminhamentos por situação")
        List<QuantidadePorSituacao> situacaoEncaminhamentos,
        @Schema(description = "Pacientes na fila por especialidade e classificação de risco")
        List<FilaPorRisco> filaPorRisco,
        @Schema(description = "Por especialidade: quem aguarda agendamento × vagas livres")
        List<DemandaCapacidade> demandaCapacidade,
        @Schema(description = "Atendimentos e encaminhamentos por dia, nos últimos 30 dias")
        List<MovimentoDia> movimento30Dias
) {
    /**
     * {@code esperaMediaDias}: média de dias desde o atendimento de quem aguarda agendamento (nulo se ninguém aguarda).
     * {@code ocupacaoVagasPercentual}: vagas futuras ocupadas ÷ vagas futuras (nulo se não há vagas futuras).
     */
    @Schema(description = "Números de destaque do painel")
    public record Numeros(
            @Schema(description = "Pacientes na fila agora (aguardando ou agendados)", example = "64")
            long naFila,
            @Schema(description = "Risco vermelho aguardando agendamento (alerta)", example = "3")
            long vermelhoAguardando,
            @Schema(description = "Média de dias desde o atendimento de quem aguarda agendamento (null se ninguém aguarda)", example = "18.4", nullable = true)
            Double esperaMediaDias,
            @Schema(description = "Vagas futuras ocupadas ÷ vagas futuras, em % (null se não há vagas futuras)", example = "62.5", nullable = true)
            Double ocupacaoVagasPercentual,
            @Schema(description = "Encaminhamentos bloqueados para revisão", example = "2")
            long bloqueadosRevisao
    ) {
    }

    @Schema(description = "Quantidade de encaminhamentos numa situação")
    public record QuantidadePorSituacao(
            @Schema(description = "Situação do encaminhamento")
            StatusEncaminhamento status,
            @Schema(description = "Quantidade", example = "20")
            long quantidade
    ) {
    }

    /** Pacientes na fila (aguardando + agendados) de uma especialidade, por classificação de risco. */
    @Schema(description = "Pacientes na fila de uma especialidade, por classificação de risco")
    public record FilaPorRisco(
            @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
            String especialidadeOuExame,
            @Schema(description = "Quantidade por classificação de risco (AZUL a VERMELHO)")
            Map<ClassificacaoRisco, Long> porRisco,
            @Schema(description = "Total na fila da especialidade", example = "16")
            long total
    ) {
    }

    @Schema(description = "Demanda e capacidade de uma especialidade")
    public record DemandaCapacidade(
            @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
            String especialidadeOuExame,
            @Schema(description = "Pacientes aguardando agendamento", example = "12")
            long aguardandoAgendamento,
            @Schema(description = "Vagas livres", example = "8")
            long vagasLivres
    ) {
    }

    /** Encaminhamentos contam no dia do atendimento que os gerou (o encaminhamento não guarda data própria). */
    @Schema(description = "Movimento de um dia. Encaminhamentos contam no dia do atendimento que os gerou.")
    public record MovimentoDia(
            @Schema(description = "Dia", example = "2026-09-24")
            LocalDate data,
            @Schema(description = "Atendimentos no dia", example = "5")
            long atendimentos,
            @Schema(description = "Encaminhamentos gerados pelos atendimentos do dia", example = "5")
            long encaminhamentos
    ) {
    }
}
