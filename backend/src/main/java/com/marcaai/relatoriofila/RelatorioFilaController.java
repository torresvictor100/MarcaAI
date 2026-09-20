package com.marcaai.relatoriofila;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fila/relatorio-ia")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
@Tag(name = "Relatório IA da fila")
public class RelatorioFilaController {

    private final RelatorioFilaService relatorioFilaService;

    @PostMapping
    @Operation(summary = "Gera um relatório de sugestões para a fila de uma especialidade/exame",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "O `MotorSugestoesFila` decide as sugestões por regras (subir na fila, antecipar, espera longa, "
                    + "urgente sem vaga, falta de vagas); a IA só redige o texto, recebendo dados sem identificação. "
                    + "Não altera a fila. Sem ANTHROPIC_API_KEY (ou se a IA falhar), o texto vem do modelo padrão "
                    + "(`origemTexto` = MODELO). Cada chamada grava um relatório novo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Relatório gerado e gravado"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'especialidade' ausente"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao relatório"),
            @ApiResponse(responseCode = "422", description = "Especialidade/exame fora do catálogo")
    })
    public ResponseEntity<RelatorioFilaResponse> gerar(
            @Parameter(description = "Especialidade ou exame do catálogo", example = "Cardiologia", required = true)
            @RequestParam String especialidade) {
        return ResponseEntity.status(HttpStatus.CREATED).body(relatorioFilaService.gerar(especialidade));
    }

    @GetMapping
    @Operation(summary = "Último relatório gerado para a fila de uma especialidade/exame (404 se ainda não houver)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\nNão gera nada; só devolve o mais recente já gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatório mais recente"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'especialidade' ausente"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao relatório"),
            @ApiResponse(responseCode = "404", description = "Ainda não há relatório para essa fila"),
            @ApiResponse(responseCode = "422", description = "Especialidade/exame fora do catálogo")
    })
    public RelatorioFilaResponse ultimo(
            @Parameter(description = "Especialidade ou exame do catálogo", example = "Cardiologia", required = true)
            @RequestParam String especialidade) {
        return relatorioFilaService.buscarUltimo(especialidade);
    }
}
