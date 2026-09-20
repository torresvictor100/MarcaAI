package com.marcaai.encaminhamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoDocumentoExigidoRepository extends JpaRepository<TipoDocumentoExigido, Long> {
    List<TipoDocumentoExigido> findByEspecialidadeOuExameIgnoreCase(String especialidadeOuExame);
}
