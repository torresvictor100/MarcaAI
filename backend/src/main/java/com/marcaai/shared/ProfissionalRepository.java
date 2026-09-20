package com.marcaai.shared;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {
    Optional<Profissional> findByUsuarioId(Long usuarioId);
    boolean existsByRegistroConselhoIgnoreCase(String registroConselho);
    List<Profissional> findByEspecialidadeIgnoreCaseOrderByNome(String especialidade);
    List<Profissional> findAllByOrderByNome();
}
