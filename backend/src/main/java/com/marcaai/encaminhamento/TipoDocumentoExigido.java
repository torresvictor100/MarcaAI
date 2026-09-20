package com.marcaai.encaminhamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configura quais tipos de documento são obrigatórios por especialidade/exame (configurável, não um enum fixo). */
@Entity
@Table(name = "tipos_documento_exigido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoDocumentoExigido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "especialidade_ou_exame", nullable = false)
    private String especialidadeOuExame;

    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;
}
