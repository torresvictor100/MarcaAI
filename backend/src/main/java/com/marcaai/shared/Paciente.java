package com.marcaai.shared;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@EntityListeners(PacienteCpfListener.class)
@Table(name = "pacientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Cifrado no banco com AES-GCM; a aplicação só enxerga o CPF em claro (ADR-010). */
    @Convert(converter = CpfConverter.class)
    @Column(nullable = false)
    private String cpf;

    /** Índice cego (HMAC) do CPF: garante CPF único e permite buscar por CPF sem decifrar a tabela. */
    @Column(name = "cpf_hash", nullable = false, unique = true, length = 64)
    private String cpfHash;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    private String contato;

    /** Vínculo opcional com Usuario, quando o paciente também faz login (ADR-005). */
    @Column(name = "usuario_id")
    private Long usuarioId;
}
