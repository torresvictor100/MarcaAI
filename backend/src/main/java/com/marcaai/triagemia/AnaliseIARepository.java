package com.marcaai.triagemia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnaliseIARepository extends JpaRepository<AnaliseIA, Long> {
    Optional<AnaliseIA> findByEncaminhamentoId(Long encaminhamentoId);
}
