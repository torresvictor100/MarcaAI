package com.marcaai.vagasagendamento;

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
 * Sub-recurso de encaminhamento (mesmo padrão de {@code ResultadoExameController}/{@code TriagemIAController}):
 * consulta o agendamento já confirmado (data/hora, vaga) de um encaminhamento. A criação continua em
 * {@code AgendamentoController} (POST /agendamentos), restrita à secretaria.
 */
@RestController
@RequestMapping("/encaminhamentos/{id}/agendamento")
@RequiredArgsConstructor
@Tag(name = "Agendamento")
public class EncaminhamentoAgendamentoController {

    private final AgendamentoService agendamentoService;
    private final ControleAcessoService controleAcessoService;

    @GetMapping
    @Operation(summary = "Consulta o agendamento confirmado de um encaminhamento",
            description = "**Papéis:** todos, por vínculo (ADR-007).\n\n"
                    + "Considera só o agendamento em vigor (cancelados ficam de fora). Traz o local com endereço, "
                    + "a presença confirmada e o histórico de mudanças.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agendamento em vigor"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado, ou ainda sem agendamento")
    })
    public AgendamentoResponse buscar(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return agendamentoService.montarResposta(agendamentoService.buscarPorEncaminhamento(id));
    }
}
