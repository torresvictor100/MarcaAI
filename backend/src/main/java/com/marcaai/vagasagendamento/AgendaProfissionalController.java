package com.marcaai.vagasagendamento;

import com.marcaai.acesso.ControleAcessoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Agenda do especialista logado. O profissional vem sempre do login, nunca de um parâmetro: ele só vê as
 * próprias vagas e os encaminhamentos agendados nelas (ADR-007).
 */
@RestController
@RequestMapping("/agenda")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ESPECIALISTA')")
@Tag(name = "Agenda do especialista")
public class AgendaProfissionalController {

    private final AgendamentoService agendamentoService;
    private final ControleAcessoService controleAcessoService;

    @GetMapping("/encaminhamentos")
    @Operation(summary = "Encaminhamentos agendados nas vagas do especialista logado",
            description = "**Papel:** ESPECIALISTA. O profissional vem do login, nunca de parâmetro.\n\n"
                    + "Não inclui agendamentos cancelados. Filtros opcionais e combináveis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encaminhamentos da agenda, por data e hora (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Situação inválida ou data fora do formato AAAA-MM-DD"),
            @ApiResponse(responseCode = "403", description = "Não é especialista, ou o usuário não tem cadastro de profissional"),
            @ApiResponse(responseCode = "422", description = "'ate' antes de 'de'")
    })
    public List<AgendaEncaminhamentoResponse> encaminhamentos(
            @Parameter(description = "A_ATENDER (agendado) ou ATENDIDO (realizado); sem ela, os dois")
            @RequestParam(required = false) SituacaoAtendimento situacao,
            @Parameter(description = "Trecho do nome do paciente, sem diferenciar maiúsculas nem acentos", example = "joao")
            @RequestParam(required = false) String paciente,
            @Parameter(description = "Data inicial AAAA-MM-DD, inclusive", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Data final AAAA-MM-DD, inclusive", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return agendamentoService.listarAgendaDoProfissional(
                controleAcessoService.profissionalIdDoUsuarioLogado(), situacao, paciente, de, ate);
    }

    @GetMapping("/vagas")
    @Operation(summary = "Vagas do especialista logado, livres e ocupadas",
            description = "**Papel:** ESPECIALISTA. O profissional vem do login, nunca de parâmetro.\n\n"
                    + "Nas vagas ocupadas vêm o encaminhamento, o paciente e a presença confirmada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vagas, por data e hora (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Status inválido ou data fora do formato AAAA-MM-DD"),
            @ApiResponse(responseCode = "403", description = "Não é especialista, ou o usuário não tem cadastro de profissional"),
            @ApiResponse(responseCode = "422", description = "'ate' antes de 'de'")
    })
    public List<AgendaVagaResponse> vagas(
            @Parameter(description = "DISPONIVEL ou OCUPADA; sem ele, todas")
            @RequestParam(required = false) StatusVaga status,
            @Parameter(description = "Data inicial AAAA-MM-DD, inclusive", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Data final AAAA-MM-DD, inclusive", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return agendamentoService.listarVagasDoProfissional(
                controleAcessoService.profissionalIdDoUsuarioLogado(), status, de, ate);
    }
}
