package com.marcaai.shared;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profissionais")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "registro_conselho", nullable = false)
    private String registroConselho;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoProfissional tipo;

    @Column(nullable = false)
    private String especialidade;

    @Column(name = "usuario_id")
    private Long usuarioId;
}
