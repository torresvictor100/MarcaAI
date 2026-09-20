package com.marcaai.resultadoexame;

import com.marcaai.acesso.ControleAcessoService;
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

@RestController
@RequestMapping("/encaminhamentos/{id}/resultado-exame")
@RequiredArgsConstructor
@Tag(name = "Resultado de exame")
public class ResultadoExameController {

    private final ResultadoExameService resultadoExameService;
    private final ControleAcessoService controleAcessoService;

    @PostMapping
    @PreAuthorize("hasRole('ESPECIALISTA')")
    @Operation(summary = "Registra o resultado/laudo: o especialista (inclusive o laboratório), para a consulta ou exame agendado numa vaga dele",
            description = "**Papel:** ESPECIALISTA (consulta ou exame agendado numa vaga dele — o laboratório também é especialista).\n\n"
                    + "Só em encaminhamento AGENDADO e ainda sem resultado; não sobrescreve. O encaminhamento vira "
                    + "REALIZADO e o paciente sai da fila (a linha da fila é mantida para auditoria).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Resultado registrado; o paciente saiu da fila"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente"),
            @ApiResponse(responseCode = "403", description = "Não é o especialista responsável"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado"),
            @ApiResponse(responseCode = "409", description = "Encaminhamento não está agendado, ou já tem resultado")
    })
    public ResponseEntity<ResultadoExameResponse> registrar(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id,
            @Valid @RequestBody ResultadoExameRequest request) {
        controleAcessoService.verificarRegistroDeResultado(id);
        ResultadoExame resultado = resultadoExameService.registrar(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResultadoExameResponse.of(resultado));
    }

    @GetMapping
    @Operation(summary = "Consulta o resultado/laudo de um exame",
            description = "**Papéis:** todos, por vínculo (ADR-007): paciente (o próprio), médico da UBS que encaminhou, "
                    + "especialista/unidade responsável, secretaria e admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado registrado"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado, ou ainda sem resultado")
    })
    public ResultadoExameResponse buscar(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return ResultadoExameResponse.of(resultadoExameService.buscarPorEncaminhamento(id));
    }
}
