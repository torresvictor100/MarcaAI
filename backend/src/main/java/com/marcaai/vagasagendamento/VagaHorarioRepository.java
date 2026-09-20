package com.marcaai.vagasagendamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VagaHorarioRepository extends JpaRepository<VagaHorario, Long> {
    List<VagaHorario> findByEspecialidadeOuExameIgnoreCaseAndStatus(String especialidadeOuExame, StatusVaga status);
    long countByStatus(StatusVaga status);

    List<VagaHorario> findByDataHoraAfter(LocalDateTime inicio);

    List<VagaHorario> findByProfissionalIdOrderByDataHora(Long profissionalId);

    List<VagaHorario> findByProfissionalIdAndDataHoraBetween(Long profissionalId, LocalDateTime inicio, LocalDateTime fim);

    List<VagaHorario> findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualOrderByDataHora(
            String especialidadeOuExame, StatusVaga status, LocalDateTime inicio);

    List<VagaHorario> findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHora(
            String especialidadeOuExame, StatusVaga status, LocalDateTime inicio, LocalDateTime fimExclusivo);
}
