package com.marcaai.triagemia;

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

@RestController
@RequestMapping("/encaminhamentos/{id}/analise-ia")
@RequiredArgsConstructor
@Tag(name = "Análise de IA")
public class TriagemIAController {

    private final TriagemIAService triagemIAService;
    private final ControleAcessoService controleAcessoService;

    @GetMapping
    @Operation(summary = "Consulta o resultado da análise de IA de um encaminhamento",
            description = "**Papéis:** todos, por vínculo (ADR-007).\n\n"
                    + "A triagem roda sozinha quando o encaminhamento tem todos os documentos exigidos. Score, bloqueio e "
                    + "irregularidades são decididos pelo motor de regras determinístico; a IA (Anthropic) só redige "
                    + "`justificativaTexto`, com texto padrão quando ela não está disponível (ADR-004).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado da triagem"),
            @ApiResponse(responseCode = "403", description = "O encaminhamento não é do usuário logado"),
            @ApiResponse(responseCode = "404", description = "Encaminhamento não encontrado, ou ainda não triado (faltam documentos)")
    })
    public AnaliseIAResponse buscar(
            @Parameter(description = "Id do encaminhamento", example = "5") @PathVariable Long id) {
        controleAcessoService.verificarLeituraDoEncaminhamento(id);
        return AnaliseIAResponse.of(triagemIAService.buscarPorEncaminhamento(id));
    }
}
