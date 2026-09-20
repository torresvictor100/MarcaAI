package com.marcaai.vagasagendamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Uma mudança num agendamento confirmado (cancelamento, remarcação ou antecipação), com motivo e autor. */
@Entity
@Table(name = "historico_agendamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricoAgendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agendamento_id", nullable = false)
    private Long agendamentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcaoAgendamento acao;

    @Column(name = "vaga_anterior_id", nullable = false)
    private Long vagaAnteriorId;

    @Column(name = "data_hora_anterior", nullable = false)
    private LocalDateTime dataHoraAnterior;

    /** Nula no cancelamento. */
    @Column(name = "vaga_nova_id")
    private Long vagaNovaId;

    @Column(name = "data_hora_nova")
    private LocalDateTime dataHoraNova;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Column(name = "feito_por", nullable = false)
    private String feitoPor;

    @Column(name = "feito_em", nullable = false)
    private LocalDateTime feitoEm;
}
