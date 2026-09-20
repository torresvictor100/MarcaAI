package com.marcaai.encaminhamento;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Lista fechada de CIDs usada no seed de demonstração — não é a tabela oficial completa de CID-10 (ver docs/TECH-SPEC.md). */
@Entity
@Table(name = "cids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String descricao;

    // EAGER: tabela pequena e fixa (lista fechada), sempre lida junto com o Cid — evita
    // LazyInitializationException ao serializar fora de uma transação (spring.jpa.open-in-view=false).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cid_especialidades", joinColumns = @JoinColumn(name = "cid_id"))
    @Column(name = "especialidade")
    @Builder.Default
    private List<String> especialidadesCompativeis = new ArrayList<>();
}
