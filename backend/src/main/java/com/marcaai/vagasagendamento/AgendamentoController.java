package com.marcaai.vagasagendamento;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agendamentos")
@RequiredArgsConstructor
@Tag(name = "Agendamento")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;
    private final ControleAcessoService controleAcessoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    @Operation(summary = "Confirma um agendamento vinculando um encaminhamento a uma vaga disponível",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "A vaga passa a OCUPADA e o encaminhamento a AGENDADO. O paciente continua na fila até o "
                    + "atendimento. Para trocar a data de um agendamento existente, use remarcar ou antecipar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Agendamento confirmado"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente"),
            @ApiResponse(responseCode = "403", description = "Papel sem permissão para agendar"),
            @ApiResponse(responseCode = "404", description = "Vaga ou encaminhamento não encontrado"),
            @ApiResponse(responseCode = "409", description = "Vaga não está disponível, ou o encaminhamento já tem agendamento ativo")
    })
    public ResponseEntity<AgendamentoResponse> criar(@Valid @RequestBody AgendamentoRequest request) {
        Agendamento agendamento = agendamentoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(agendamentoService.montarResposta(agendamento));
    }

    @PostMapping("/{id}/confirmar-presenca")
    @PreAuthorize("hasRole('PACIENTE')")
    @Operation(summary = "O paciente confirma que vai comparecer ao próprio agendamento",
            description = "**Papel:** PACIENTE, só no próprio agendamento (ADR-007 item 9).\n\n"
                    + "Só agendamento confirmado, não atendido e com data futura. Confirmar de novo não muda nada; "
                    + "remarcar ou antecipar zera a confirmação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presença confirmada; devolve o agendamento com presencaConfirmadaEm"),
            @ApiResponse(responseCode = "403", description = "Não é paciente, ou o agendamento é de outro paciente"),
            @ApiResponse(responseCode = "404", description = "Agendamento não encontrado"),
            @ApiResponse(responseCode = "409", description = "Agendamento cancelado ou já atendido"),
            @ApiResponse(responseCode = "422", description = "A data do agendamento já passou")
    })
    public AgendamentoResponse confirmarPresenca(
            @Parameter(description = "Id do agendamento", example = "12") @PathVariable Long id) {
        // Acesso antes da regra: paciente só confirma o que é dele (403 para o de outro).
        controleAcessoService.verificarLeituraDoEncaminhamento(agendamentoService.buscarPorId(id).getEncaminhamentoId());
        return agendamentoService.montarResposta(agendamentoService.confirmarPresenca(id));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    @Operation(summary = "Cancela um agendamento confirmado (motivo obrigatório)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "A vaga volta a ficar livre e o paciente volta a aguardar agendamento na fila (NA_FILA). "
                    + "O cancelamento entra no histórico do agendamento, com motivo, autor e horário (auditoria).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agendamento cancelado"),
            @ApiResponse(responseCode = "400", description = "Motivo em branco"),
            @ApiResponse(responseCode = "403", description = "Papel sem permissão para cancelar"),
            @ApiResponse(responseCode = "404", description = "Agendamento não encontrado"),
            @ApiResponse(responseCode = "409", description = "Agendamento já cancelado ou realizado")
    })
    public AgendamentoResponse cancelar(
            @Parameter(description = "Id do agendamento", example = "12") @PathVariable Long id,
            @Valid @RequestBody CancelamentoRequest request) {
        return agendamentoService.montarResposta(agendamentoService.cancelar(id, request.motivo()));
    }

    @PostMapping("/{id}/remarcar")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    @Operation(summary = "Troca a vaga por outra livre da mesma especialidade, em qualquer data (motivo obrigatório)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "Libera a vaga antiga, ocupa a nova, zera a presença confirmada e registra a mudança no histórico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agendamento remarcado"),
            @ApiResponse(responseCode = "400", description = "Vaga ou motivo ausente"),
            @ApiResponse(responseCode = "403", description = "Papel sem permissão para remarcar"),
            @ApiResponse(responseCode = "404", description = "Agendamento ou vaga não encontrado"),
            @ApiResponse(responseCode = "409", description = "Agendamento não está confirmado, ou a vaga nova não está disponível"),
            @ApiResponse(responseCode = "422", description = "Vaga nova igual à atual ou de outra especialidade")
    })
    public AgendamentoResponse remarcar(
            @Parameter(description = "Id do agendamento", example = "12") @PathVariable Long id,
            @Valid @RequestBody RemarcacaoRequest request) {
        return agendamentoService.montarResposta(agendamentoService.remarcar(id, request.vagaId(), request.motivo()));
    }

    @PostMapping("/{id}/antecipar")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    @Operation(summary = "Troca a vaga por outra livre da mesma especialidade, anterior à data atual (motivo obrigatório)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "Igual a remarcar, mas só aceita vaga antes da data atual do agendamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agendamento antecipado"),
            @ApiResponse(responseCode = "400", description = "Vaga ou motivo ausente"),
            @ApiResponse(responseCode = "403", description = "Papel sem permissão para antecipar"),
            @ApiResponse(responseCode = "404", description = "Agendamento ou vaga não encontrado"),
            @ApiResponse(responseCode = "409", description = "Agendamento não está confirmado, ou a vaga nova não está disponível"),
            @ApiResponse(responseCode = "422", description = "Vaga nova igual à atual, de outra especialidade ou não anterior à data atual")
    })
    public AgendamentoResponse antecipar(
            @Parameter(description = "Id do agendamento", example = "12") @PathVariable Long id,
            @Valid @RequestBody RemarcacaoRequest request) {
        return agendamentoService.montarResposta(agendamentoService.antecipar(id, request.vagaId(), request.motivo()));
    }
}
