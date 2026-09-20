package com.marcaai.fila;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemFilaRepository extends JpaRepository<ItemFila, Long> {
    // Só quem ainda está na fila (não atendido); quem saiu continua na tabela para auditoria.
    List<ItemFila> findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull(String especialidadeOuExame);
    List<ItemFila> findByProfissionalIdAndSaiuDaFilaEmIsNull(Long profissionalId);
    Optional<ItemFila> findByEncaminhamentoId(Long encaminhamentoId);
}
