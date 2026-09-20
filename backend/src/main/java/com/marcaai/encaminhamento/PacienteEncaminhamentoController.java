package com.marcaai.encaminhamento;

import com.marcaai.acesso.ControleAcessoService;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.config.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Paciente")
public class PacienteEncaminhamentoController {

    private final PacienteRepository pacienteRepository;
    private final ControleAcessoService controleAcessoService;

    @GetMapping("/pacientes/{id}/encaminhamentos")
    @Operation(summary = "Lista os encaminhamentos de um paciente",
            description = "**Papéis:** PACIENTE, MEDICO_UBS, SECRETARIA, ADMIN.\n\n"
                    + "Paciente vê os próprios; médico da UBS, só os que ele gerou; secretaria/admin, todos. "
                    + "O acesso é checado antes da existência: quem não pode listar recebe 403 mesmo para id inexistente, "
                    + "para não revelar quais pacientes existem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encaminhamentos visíveis para o usuário (pode ser lista vazia)"),
            @ApiResponse(responseCode = "403", description = "Paciente pedindo os de outro, ou especialista"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    public List<EncaminhamentoResponse> listar(
            @Parameter(description = "Id do paciente (o próprio paciente recebe o dele no login, em pacienteId)", example = "2")
            @PathVariable Long id) {
        // Acesso antes da existência: quem não pode listar recebe 403 mesmo para id inexistente (não sonda cadastro).
        List<Encaminhamento> visiveis = controleAcessoService.encaminhamentosVisiveisDoPaciente(id);
        pacienteRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
        return visiveis.stream().map(EncaminhamentoResponse::of).toList();
    }
}
