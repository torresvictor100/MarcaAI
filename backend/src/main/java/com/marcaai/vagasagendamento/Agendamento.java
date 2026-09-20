package com.marcaai.vagasagendamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "agendamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encaminhamento_id", nullable = false)
    private Long encaminhamentoId;

    @Column(name = "vaga_id", nullable = false)
    private Long vagaId;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusAgendamento status = StatusAgendamento.CONFIRMADO;

    @Column(name = "agendado_por", nullable = false)
    private String agendadoPor;

    /** Quando o paciente confirmou que vai comparecer; nulo = ainda não confirmou (zera ao mudar a data). */
    @Column(name = "presenca_confirmada_em")
    private LocalDateTime presencaConfirmadaEm;
}
