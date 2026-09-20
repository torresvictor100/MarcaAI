package com.marcaai.encaminhamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByEncaminhamentoId(Long encaminhamentoId);
}
