package com.marcaai.relatoriofila;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "relatorios_fila")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelatorioFila {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "especialidade_ou_exame", nullable = false)
    private String especialidadeOuExame;

    @Column(name = "gerado_em", nullable = false)
    private LocalDateTime geradoEm;

    @Column(name = "gerado_por", nullable = false)
    private String geradoPor;

    @Column(name = "total_na_fila", nullable = false)
    private int totalNaFila;

    /** Sugestões do motor de regras, em JSON, exatamente como foram mostradas (auditoria). */
    @Column(name = "sugestoes_json", nullable = false, columnDefinition = "text")
    private String sugestoesJson;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_texto", nullable = false)
    private OrigemTexto origemTexto;
}
