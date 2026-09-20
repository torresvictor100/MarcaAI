package com.marcaai.encaminhamento;

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

import java.util.List;

@RestController
@RequestMapping("/encaminhamentos")
@RequiredArgsConstructor
@Tag(name = "Encaminhamento")
public class EncaminhamentoController {

    private static final String LEITURA_POR_VINCULO = "**Papéis:** todos, por vínculo (ADR-007).\n\n"
            + "Paciente: só os próprios. Médico da UBS: os que gerou. Especialista (inclusive o laboratório): só consulta "
            + "ou exame agendado numa vaga dele. Secretaria e admin: todos.";

    private final EncaminhamentoService encaminhamentoService;
    private final ControleAcessoService controleAcessoService;

    @PostMapping
    @PreAuthorize("hasRole('MEDICO_UBS')")
    @Operation(summary = "Cria um encaminhamento com pelo menos um documento (CID + flag de urgência opcional)",
            description = "**Papel:** MEDICO_UBS, a partir de um atendimento que ele mesmo registrou.\n\n"
                    + "Nasce em AGUARDANDO_DOCUMENTOS. Se os documentos exigidos pela especialidade já vierem completos, "
                    + "a triagem roda na mesma chamada e a resposta já volta NA_FILA ou BLOQUEADO_REVISAO. "
                    + "Urgente exige `justificativaUrgencia`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Encaminhamento criado (e triado, se os documentos exigidos vieram completos)"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente, ou nenhum documento"),
            @ApiResponse(responseCode = "403", description = "Não é médico da UBS, ou o atendimento é de outro médico"),
            @ApiResponse(responseCode = "404", description = "Atendimento não encontrado"),
            @ApiResponse(responseCode = "422", description = "CID fora da lista fechada, ou urgente sem justificativa")
    })
    public ResponseEntity<EncaminhamentoResponse> criar(@Valid @RequestBody EncaminhamentoRequest request) {
        controleAcessoService.verificarAtendimentoDoMedicoLogado(request.atendimentoId());
        Encaminhamento encaminhamento = encaminhamentoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(EncaminhamentoResponse.of(encaminhamento));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um encaminhamento pelo id", description = LEITURA_POR_VINCULO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encaminhamento encontrado"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado")
    })
    public ResponseEntity<EncaminhamentoResponse> buscarPorId(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return ResponseEntity.ok(EncaminhamentoResponse.of(encaminhamentoService.buscarPorId(id)));
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Checklist de status do encaminhamento (atendido → encaminhado → agendado → realizado)",
            description = LEITURA_POR_VINCULO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Etapas, com as já alcançadas marcadas"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado")
    })
    public ResponseEntity<TimelineResponse> timeline(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return ResponseEntity.ok(TimelineResponse.of(encaminhamentoService.buscarPorId(id)));
    }

    @PostMapping("/{id}/documentos")
    @PreAuthorize("hasRole('MEDICO_UBS')")
    @Operation(summary = "Anexa um documento ao encaminhamento; dispara a análise de IA quando todos os obrigatórios estiverem presentes",
            description = "**Papel:** MEDICO_UBS, só nos encaminhamentos que ele gerou.\n\n"
                    + "Quando o documento completa os tipos exigidos (GET /especialidades/{especialidade}/documentos-exigidos), "
                    + "a triagem roda e o encaminhamento vai para NA_FILA ou BLOQUEADO_REVISAO.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Documento anexado"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente"),
            @ApiResponse(responseCode = "403", description = "Não é médico da UBS, ou o encaminhamento é de outro médico"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado")
    })
    public ResponseEntity<DocumentoResponse> adicionarDocumento(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id,
            @Valid @RequestBody DocumentoRequest request) {
        controleAcessoService.verificarEncaminhamentoDoMedicoLogado(id);
        Documento documento = encaminhamentoService.adicionarDocumento(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentoResponse.of(documento));
    }

    @GetMapping("/{id}/documentos")
    @Operation(summary = "Lista os documentos anexados ao encaminhamento", description = LEITURA_POR_VINCULO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documentos anexados"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado")
    })
    public List<DocumentoResponse> listarDocumentos(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return encaminhamentoService.listarDocumentos(id).stream().map(DocumentoResponse::of).toList();
    }
}
