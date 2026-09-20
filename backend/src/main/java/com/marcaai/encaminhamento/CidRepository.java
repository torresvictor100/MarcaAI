package com.marcaai.encaminhamento;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CidRepository extends JpaRepository<Cid, Long> {

    /**
     * Busca por trecho da especialidade/exame, sem diferenciar maiúsculas/minúsculas. Substitui o antigo
     * {@code findByEspecialidadesCompativeisContainingIgnoreCase}: "Containing" numa @ElementCollection
     * vira uma checagem de igualdade por elemento, não uma busca por substring — e o "IgnoreCase" nem
     * chegava a ser aplicado nessa tradução, então só um valor exato (com a caixa exata) retornava algo.
     */
    @Query("select distinct c from Cid c join c.especialidadesCompativeis e "
            + "where lower(e) like lower(concat('%', :especialidade, '%'))")
    List<Cid> buscarPorEspecialidadeParcial(@Param("especialidade") String especialidade);

    @Query("select distinct e from Cid c join c.especialidadesCompativeis e order by e")
    List<String> listarEspecialidadesDistintas();
}
