package com.marcaai.shared;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/** Mantém o índice cego ({@code cpf_hash}) em dia sempre que um paciente é gravado pela aplicação (ADR-010). */
public class PacienteCpfListener {

    private final CriptografiaDados criptografia;

    public PacienteCpfListener(CriptografiaDados criptografia) {
        this.criptografia = criptografia;
    }

    @PrePersist
    @PreUpdate
    void atualizarIndice(Paciente paciente) {
        paciente.setCpfHash(criptografia.indiceCpf(paciente.getCpf()));
    }
}
