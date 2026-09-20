package com.marcaai.resultadoexame;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "resultados_exame")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoExame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encaminhamento_id", nullable = false, unique = true)
    private Long encaminhamentoId;

    @Column(name = "referencia_arquivo", nullable = false)
    private String referenciaArquivo;

    @Column(name = "data_resultado", nullable = false)
    private LocalDate dataResultado;

    @Column(columnDefinition = "text")
    private String observacoes;
}
