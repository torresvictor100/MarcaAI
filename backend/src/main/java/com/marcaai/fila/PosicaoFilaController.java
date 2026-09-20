package com.marcaai.fila;

import com.marcaai.acesso.ControleAcessoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sub-recurso de encaminhamento (mesmo padrão de {@code TriagemIAController}/{@code ResultadoExameController}):
 * permite ao próprio paciente consultar a posição do seu encaminhamento na fila, sem precisar do acesso
 * completo à fila por especialidade (restrito à secretaria).
 */
@RestController
@RequestMapping("/encaminhamentos/{id}/posicao-fila")
@RequiredArgsConstructor
@Tag(name = "Fila")
public class PosicaoFilaController {

    private final FilaService filaService;
    private final ControleAcessoService controleAcessoService;

    @GetMapping
    @Operation(summary = "Consulta a posição e o score de um encaminhamento na fila",
            description = "**Papéis:** todos, por vínculo (ADR-007): o paciente vê a posição do próprio encaminhamento "
                    + "sem acesso à fila inteira.\n\n"
                    + "Só existe posição enquanto o encaminhamento está na fila (NA_FILA ou AGENDADO).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posição atual e score"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado, ainda não entrou na fila ou já foi atendido e saiu")
    })
    public FilaItemResponse buscar(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return filaService.buscarPorEncaminhamento(id);
    }
}
