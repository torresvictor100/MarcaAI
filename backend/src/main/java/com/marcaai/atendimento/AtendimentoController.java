package com.marcaai.atendimento;

import com.marcaai.acesso.ControleAcessoService;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.config.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Atendimento")
public class AtendimentoController {

    private final AtendimentoService atendimentoService;
    private final ControleAcessoService controleAcessoService;
    private final PacienteRepository pacienteRepository;

    @PostMapping("/atendimentos")
    @PreAuthorize("hasRole('MEDICO_UBS')")
    @Operation(summary = "Registra o atendimento de um paciente na UBS",
            description = "**Papel:** MEDICO_UBS.\n\n"
                    + "O profissional do atendimento é sempre o médico logado; o corpo não aceita `profissionalId`. "
                    + "A classificação de risco (protocolo de Manchester) entra no score da fila quando o atendimento gerar "
                    + "um encaminhamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Atendimento registrado"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Não é médico da UBS, ou o usuário não tem cadastro de profissional"),
            @ApiResponse(responseCode = "422", description = "Paciente ou unidade inexistente")
    })
    public ResponseEntity<AtendimentoResponse> criar(@Valid @RequestBody AtendimentoRequest request) {
        Long profissionalId = controleAcessoService.profissionalIdDoUsuarioLogado();
        Atendimento atendimento = atendimentoService.criar(request, profissionalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(atendimentoService.montarResposta(atendimento));
    }

    @GetMapping("/atendimentos/{id}")
    @Operation(summary = "Consulta um atendimento pelo id",
            description = "**Papéis:** todos, por vínculo (ADR-007).\n\n"
                    + "Paciente: só os próprios. Médico da UBS: só os que registrou. Especialista: só o "
                    + "atendimento de origem de um encaminhamento que ele pode ver. Secretaria e admin: todos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atendimento encontrado, com nomes de paciente, médico e unidade"),
            @ApiResponse(responseCode = "403", description = "O atendimento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Atendimento não encontrado")
    })
    public ResponseEntity<AtendimentoResponse> buscarPorId(
            @Parameter(description = "Id do atendimento", example = "1") @PathVariable Long id) {
        Atendimento atendimento = atendimentoService.buscarPorId(id);
        controleAcessoService.verificarLeituraDoAtendimento(atendimento);
        return ResponseEntity.ok(atendimentoService.montarResposta(atendimento));
    }

    @GetMapping("/pacientes/{id}/atendimentos")
    @Operation(summary = "Lista os atendimentos de um paciente, do mais recente para o mais antigo",
            description = "**Papéis:** PACIENTE, MEDICO_UBS, SECRETARIA, ADMIN.\n\n"
                    + "Paciente vê os próprios; médico da UBS, só os que ele registrou; secretaria/admin, todos. "
                    + "O acesso é checado antes da existência: quem não pode listar recebe 403 mesmo para id inexistente, "
                    + "para não revelar quais pacientes existem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atendimentos visíveis para o usuário (pode ser lista vazia)"),
            @ApiResponse(responseCode = "403", description = "Paciente pedindo os de outro, ou especialista"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    public List<AtendimentoResponse> listarPorPaciente(
            @Parameter(description = "Id do paciente", example = "2") @PathVariable Long id) {
        // Acesso antes da existência: quem não pode listar recebe 403 mesmo para id inexistente (não sonda cadastro).
        List<Atendimento> visiveis = controleAcessoService.atendimentosVisiveisDoPaciente(id);
        pacienteRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
        return visiveis.stream().map(atendimentoService::montarResposta).toList();
    }
}
