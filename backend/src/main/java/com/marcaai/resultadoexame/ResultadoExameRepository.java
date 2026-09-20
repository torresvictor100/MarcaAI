package com.marcaai.resultadoexame;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultadoExameRepository extends JpaRepository<ResultadoExame, Long> {
    Optional<ResultadoExame> findByEncaminhamentoId(Long encaminhamentoId);
}
