package com.marcaai.vagasagendamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "vagas_horario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VagaHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "especialidade_ou_exame", nullable = false)
    private String especialidadeOuExame;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusVaga status = StatusVaga.DISPONIVEL;

    /** Lock otimista (ADR-011): duas gravações concorrentes da mesma vaga — a segunda falha e vira 409. */
    @Version
    private Long versao;
}
