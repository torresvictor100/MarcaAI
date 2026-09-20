package com.marcaai.atendimento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {
    List<Atendimento> findByPacienteId(Long pacienteId);
    long countByDataBetween(LocalDateTime inicio, LocalDateTime fim);
    List<Atendimento> findByDataBetween(LocalDateTime inicio, LocalDateTime fim);
}
