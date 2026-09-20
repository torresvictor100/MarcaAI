package com.marcaai.atendimento;

import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.shared.UnidadeSaude;
import com.marcaai.shared.UnidadeSaudeRepository;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtendimentoService {

    private final AtendimentoRepository atendimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final UnidadeSaudeRepository unidadeSaudeRepository;

    public List<Atendimento> listarPorPaciente(Long pacienteId) {
        return atendimentoRepository.findByPacienteId(pacienteId);
    }

    public List<Atendimento> listarNoPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return atendimentoRepository.findByDataBetween(inicio, fim);
    }

    public long contarAtendimentosNoPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return atendimentoRepository.countByDataBetween(inicio, fim);
    }

    public Atendimento criar(AtendimentoRequest request, Long profissionalId) {
        Atendimento atendimento = Atendimento.builder()
                .pacienteId(request.pacienteId())
                .profissionalId(profissionalId)
                .unidadeId(request.unidadeId())
                .data(request.data())
                .notas(request.notas())
                .classificacaoRisco(request.classificacaoRisco())
                .build();
        return atendimentoRepository.save(atendimento);
    }

    public Atendimento buscarPorId(Long id) {
        return atendimentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado: " + id));
    }

    /** Monta a resposta com os nomes de paciente, profissional e unidade resolvidos a partir dos ids. */
    public AtendimentoResponse montarResposta(Atendimento atendimento) {
        String pacienteNome = pacienteRepository.findById(atendimento.getPacienteId())
                .map(Paciente::getNome).orElse(null);
        String profissionalNome = profissionalRepository.findById(atendimento.getProfissionalId())
                .map(Profissional::getNome).orElse(null);
        Optional<UnidadeSaude> unidade = unidadeSaudeRepository.findById(atendimento.getUnidadeId());
        return AtendimentoResponse.of(atendimento, pacienteNome, profissionalNome,
                unidade.map(UnidadeSaude::getNome).orElse(null), unidade.map(UnidadeSaude::getEndereco).orElse(null));
    }
}
