package com.marcaai.atendimento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "atendimentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;

    @Column(nullable = false)
    private LocalDateTime data;

    @Column(columnDefinition = "text")
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(name = "classificacao_risco", nullable = false)
    private ClassificacaoRisco classificacaoRisco;
}
