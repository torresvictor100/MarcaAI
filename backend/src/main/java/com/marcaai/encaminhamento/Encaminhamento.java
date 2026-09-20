package com.marcaai.encaminhamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "encaminhamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encaminhamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "atendimento_id", nullable = false)
    private Long atendimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEncaminhamento tipo;

    @Column(name = "especialidade_ou_exame", nullable = false)
    private String especialidadeOuExame;

    @Column(name = "cid_id", nullable = false)
    private Long cidId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusEncaminhamento status = StatusEncaminhamento.AGUARDANDO_DOCUMENTOS;

    @Column(nullable = false)
    @Builder.Default
    private boolean urgente = false;

    @Column(name = "justificativa_urgencia", columnDefinition = "text")
    private String justificativaUrgencia;
}
