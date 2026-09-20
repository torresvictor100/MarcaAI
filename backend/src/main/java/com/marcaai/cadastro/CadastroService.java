package com.marcaai.cadastro;

import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.config.RequisicaoInvalidaException;
import com.marcaai.config.ConflitoException;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.shared.TipoProfissional;
import com.marcaai.shared.UnidadeSaude;
import com.marcaai.shared.UnidadeSaudeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Cadastro de profissionais (especialistas), consulta de unidades e busca de pacientes por nome. */
@Service
@RequiredArgsConstructor
public class CadastroService {

    private final ProfissionalRepository profissionalRepository;
    private final UnidadeSaudeRepository unidadeSaudeRepository;
    private final EncaminhamentoService encaminhamentoService;
    private final PacienteRepository pacienteRepository;

    static final int TAMANHO_MINIMO_BUSCA = 2;
    static final int LIMITE_RESULTADOS_BUSCA = 20;

    public List<Profissional> listarProfissionais(String especialidade) {
        return especialidade == null || especialidade.isBlank()
                ? profissionalRepository.findAllByOrderByNome()
                : profissionalRepository.findByEspecialidadeIgnoreCaseOrderByNome(especialidade.trim());
    }

    public Profissional cadastrarEspecialista(ProfissionalRequest request) {
        String especialidade = encaminhamentoService.listarEspecialidades().stream()
                .filter(e -> e.equalsIgnoreCase(request.especialidade().trim()))
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Especialidade/exame fora do catálogo: " + request.especialidade()));
        String registro = request.registroConselho().trim();
        if (profissionalRepository.existsByRegistroConselhoIgnoreCase(registro)) {
            throw new ConflitoException("Já existe profissional com o registro " + registro);
        }
        return profissionalRepository.save(Profissional.builder()
                .nome(request.nome().trim())
                .registroConselho(registro)
                .tipo(TipoProfissional.ESPECIALISTA)
                .especialidade(especialidade)
                .build());
    }

    public List<UnidadeSaude> listarUnidades() {
        return unidadeSaudeRepository.findAll(Sort.by("nome"));
    }

    /**
     * Busca parcial por nome, sem diferenciar caixa. Exige um termo e limita o resultado para
     * nunca devolver a lista inteira de pacientes (dado sensível, LGPD).
     */
    public List<Paciente> buscarPacientesPorNome(String nome) {
        String termo = nome == null ? "" : nome.trim();
        if (termo.length() < TAMANHO_MINIMO_BUSCA) {
            throw new RequisicaoInvalidaException("Digite pelo menos " + TAMANHO_MINIMO_BUSCA + " letras do nome do paciente");
        }
        String semAcento = Normalizer.normalize(termo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String padrao = "%" + semAcento.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        return pacienteRepository.buscarPorNome(padrao, PageRequest.of(0, LIMITE_RESULTADOS_BUSCA));
    }
}
