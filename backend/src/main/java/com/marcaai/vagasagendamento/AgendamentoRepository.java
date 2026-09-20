package com.marcaai.vagasagendamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    /** O agendamento em vigor do encaminhamento: depois de um cancelamento pode haver outro, mais novo. */
    Optional<Agendamento> findFirstByEncaminhamentoIdAndStatusNotOrderByIdDesc(Long encaminhamentoId, StatusAgendamento statusIgnorado);

    List<Agendamento> findByVagaIdOrderByIdDesc(Long vagaId);

    /** Todos os agendamentos já feitos numa especialidade/exame (inclusive cancelados) — a especialidade mora na vaga. */
    @Query("""
            select count(a) from Agendamento a, VagaHorario v
            where a.vagaId = v.id
              and lower(v.especialidadeOuExame) = lower(:especialidadeOuExame)
            """)
    long contarPorEspecialidade(@Param("especialidadeOuExame") String especialidadeOuExame);

    /** Agendamentos nas vagas de um profissional, exceto os da situação ignorada, do mais próximo ao mais distante. */
    @Query("""
            select a from Agendamento a, VagaHorario v
            where a.vagaId = v.id
              and v.profissionalId = :profissionalId
              and a.status <> :statusIgnorado
            order by a.dataHora, a.id
            """)
    List<Agendamento> listarDoProfissional(@Param("profissionalId") Long profissionalId,
                                           @Param("statusIgnorado") StatusAgendamento statusIgnorado);
}
