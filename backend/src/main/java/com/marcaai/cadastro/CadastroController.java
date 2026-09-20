package com.marcaai.cadastro;

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
@PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
@Tag(name = "Cadastro")
public class CadastroController {

    private final CadastroService cadastroService;

    @GetMapping("/profissionais")
    @Operation(summary = "Lista profissionais, opcionalmente só de uma especialidade/exame",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "Usado para escolher o profissional ao abrir vagas. Sem `especialidade`, lista todos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissionais encontrados (pode ser lista vazia)"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao cadastro")
    })
    public List<ProfissionalResponse> listarProfissionais(
            @Parameter(description = "Filtra pela especialidade/exame (sem diferenciar maiúsculas)", example = "Dermatologia")
            @RequestParam(required = false) String especialidade) {
        return cadastroService.listarProfissionais(especialidade).stream().map(ProfissionalResponse::of).toList();
    }

    @PostMapping("/profissionais")
    @Operation(summary = "Cadastra um especialista (especialidade do catálogo; registro do conselho único)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "Cadastra o profissional sem usuário de login; ele passa a poder receber vagas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Especialista cadastrado"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente ou longo demais"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao cadastro"),
            @ApiResponse(responseCode = "409", description = "Já existe profissional com esse registro do conselho"),
            @ApiResponse(responseCode = "422", description = "Especialidade/exame fora do catálogo")
    })
    public ResponseEntity<ProfissionalResponse> cadastrar(@Valid @RequestBody ProfissionalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfissionalResponse.of(cadastroService.cadastrarEspecialista(request)));
    }

    @GetMapping("/unidades")
    @PreAuthorize("hasAnyRole('MEDICO_UBS', 'SECRETARIA', 'ADMIN')")
    @Operation(summary = "Lista as unidades de saúde",
            description = "**Papéis:** MEDICO_UBS, SECRETARIA, ADMIN.\n\n"
                    + "O médico usa para escolher a UBS do atendimento; a secretaria, a unidade das vagas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unidades cadastradas"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso às unidades")
    })
    public List<UnidadeResponse> listarUnidades() {
        return cadastroService.listarUnidades().stream().map(UnidadeResponse::of).toList();
    }

    @GetMapping("/pacientes")
    @PreAuthorize("hasAnyRole('MEDICO_UBS', 'SECRETARIA', 'ADMIN')")
    @Operation(summary = "Busca pacientes pelo nome (mínimo 2 letras, até 20 resultados, CPF mascarado)",
            description = "**Papéis:** MEDICO_UBS, SECRETARIA, ADMIN.\n\n"
                    + "A busca é por trecho do nome e ignora maiúsculas e acentos. Devolve só id, nome e CPF mascarado "
                    + "(adendo da ADR-007, LGPD).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Até 20 pacientes, em ordem de nome (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Nome ausente ou com menos de 2 letras"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso à busca de pacientes")
    })
    public List<PacienteResumoResponse> buscarPacientes(
            @Parameter(description = "Trecho do nome, com pelo menos 2 letras", example = "joao", required = true)
            @RequestParam String nome) {
        return cadastroService.buscarPacientesPorNome(nome).stream().map(PacienteResumoResponse::of).toList();
    }
}
