package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "CID")
public class CidController {

    private final EncaminhamentoService encaminhamentoService;

    @GetMapping("/cids")
    @Operation(summary = "Lista os CIDs compatíveis com a especialidade informada",
            description = "**Papéis:** qualquer usuário logado.\n\n"
                    + "A busca é por trecho, sem diferenciar maiúsculas. Encaminhar com CID fora desta lista faz a "
                    + "triagem bloquear o encaminhamento para revisão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CIDs compatíveis (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'especialidade' ausente")
    })
    public List<CidResponse> listar(
            @Parameter(description = "Especialidade ou exame", example = "Cardiologia", required = true)
            @RequestParam String especialidade) {
        return encaminhamentoService.listarCidsPorEspecialidade(especialidade).stream()
                .map(CidResponse::of)
                .toList();
    }

    @GetMapping("/especialidades")
    @Operation(summary = "Lista as especialidades/exames conhecidas pelo sistema",
            description = "**Papéis:** qualquer usuário logado.\n\n"
                    + "É o catálogo usado em todas as rotas que recebem `especialidade`.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Nomes das especialidades e exames"))
    public List<String> listarEspecialidades() {
        return encaminhamentoService.listarEspecialidades();
    }

    @GetMapping("/especialidades/{especialidade}/documentos-exigidos")
    @Operation(summary = "Lista os tipos de documento exigidos para a especialidade/exame (vazio se nenhum)",
            description = "**Papéis:** qualquer usuário logado.\n\n"
                    + "A triagem só roda quando todos os tipos exigidos estiverem anexados ao encaminhamento; "
                    + "até lá ele fica em AGUARDANDO_DOCUMENTOS.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Tipos exigidos, em maiúsculas (pode ser lista vazia)"))
    public List<String> listarDocumentosExigidos(
            @Parameter(description = "Especialidade ou exame (sem diferenciar maiúsculas)", example = "Cardiologia")
            @PathVariable String especialidade) {
        return encaminhamentoService.listarDocumentosExigidos(especialidade);
    }
}
