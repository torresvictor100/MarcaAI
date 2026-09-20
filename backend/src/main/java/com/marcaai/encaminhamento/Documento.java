package com.marcaai.encaminhamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "documentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encaminhamento_id", nullable = false)
    private Long encaminhamentoId;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "referencia_arquivo", nullable = false)
    private String referenciaArquivo;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    private LocalDate validade;
}
