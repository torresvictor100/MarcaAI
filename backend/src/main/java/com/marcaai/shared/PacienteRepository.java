package com.marcaai.shared;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {
    Optional<Paciente> findByUsuarioId(Long usuarioId);
    /** Busca pelo índice cego do CPF (ver {@link CriptografiaDados#indiceCpf}); o CPF em si está cifrado. */
    Optional<Paciente> findByCpfHash(String cpfHash);

    /**
     * Busca parcial sem diferenciar maiúsculas nem acentos ("joao" encontra "João").
     * O padrão já chega minúsculo, sem acento e com os '%' (ver CadastroService).
     */
    @Query("""
            select p from Paciente p
            where cast(function('translate', lower(p.nome), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') as String)
                like :padrao
            order by p.nome""")
    List<Paciente> buscarPorNome(@Param("padrao") String padrao, Pageable pageable);
}
