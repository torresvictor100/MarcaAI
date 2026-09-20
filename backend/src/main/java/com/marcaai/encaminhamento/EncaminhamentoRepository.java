package com.marcaai.encaminhamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncaminhamentoRepository extends JpaRepository<Encaminhamento, Long> {
    List<Encaminhamento> findByAtendimentoIdIn(List<Long> atendimentoIds);
    List<Encaminhamento> findByAtendimentoId(Long atendimentoId);
    List<Encaminhamento> findByStatus(StatusEncaminhamento status);
    long countByTipo(TipoEncaminhamento tipo);
    long countByStatus(StatusEncaminhamento status);
}
