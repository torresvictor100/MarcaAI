package com.marcaai.relatoriofila;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RelatorioFilaRepository extends JpaRepository<RelatorioFila, Long> {
    Optional<RelatorioFila> findFirstByEspecialidadeOuExameIgnoreCaseOrderByGeradoEmDescIdDesc(String especialidadeOuExame);
}
