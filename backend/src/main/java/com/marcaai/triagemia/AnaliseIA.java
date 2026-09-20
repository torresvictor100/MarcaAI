package com.marcaai.triagemia;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "analises_ia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnaliseIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encaminhamento_id", nullable = false, unique = true)
    private Long encaminhamentoId;

    @Column(name = "score_prioridade", nullable = false)
    private double scorePrioridade;

    /** JSON serializado dos fatores considerados no cálculo do score. */
    @Column(name = "fatores_considerados", columnDefinition = "text", nullable = false)
    private String fatoresConsiderados;

    /** JSON serializado da lista de {@link Irregularidade} encontradas. */
    @Column(columnDefinition = "text", nullable = false)
    private String irregularidades;

    @Column(name = "justificativa_texto", columnDefinition = "text", nullable = false)
    private String justificativaTexto;

    @Column(nullable = false)
    private boolean bloqueado;

    @Column(name = "data_analise", nullable = false)
    private LocalDateTime dataAnalise;
}
