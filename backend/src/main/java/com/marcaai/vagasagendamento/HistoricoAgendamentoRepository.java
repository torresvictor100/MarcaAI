package com.marcaai.vagasagendamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoAgendamentoRepository extends JpaRepository<HistoricoAgendamento, Long> {
    List<HistoricoAgendamento> findByAgendamentoIdOrderByFeitoEmAscIdAsc(Long agendamentoId);
}
