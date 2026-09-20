package com.marcaai.fila;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "itens_fila")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemFila {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encaminhamento_id", nullable = false, unique = true)
    private Long encaminhamentoId;

    @Column(name = "especialidade_ou_exame", nullable = false)
    private String especialidadeOuExame;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "score_atual", nullable = false)
    private double scoreAtual;

    @Column(name = "override_manual", nullable = false)
    @Builder.Default
    private boolean overrideManual = false;

    /** Posição alvo (1-based) definida manualmente pela secretaria; null = ordenação natural pelo score. */
    @Column(name = "posicao_override")
    private Integer posicaoOverride;

    @Column(name = "justificativa_override", columnDefinition = "text")
    private String justificativaOverride;

    /** Quando o paciente foi atendido e saiu da fila; null = ainda na fila. A linha fica (auditoria do ajuste manual). */
    @Column(name = "saiu_da_fila_em")
    private LocalDateTime saiuDaFilaEm;
}
