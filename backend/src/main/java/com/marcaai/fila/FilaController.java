package com.marcaai.fila;

import com.marcaai.auth.Papel;
import com.marcaai.config.AuthContext;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.config.RequisicaoInvalidaException;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fila")
@RequiredArgsConstructor
@Tag(name = "Fila")
public class FilaController {

    private final FilaService filaService;
    private final FilaDetalhesService filaDetalhesService;
    private final ProfissionalRepository profissionalRepository;
    private final EncaminhamentoService encaminhamentoService;

    @GetMapping("/todas")
    @Operation(summary = "Lista a fila de todas as especialidades/exames de uma vez, agrupada (secretaria)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "Um mapa de especialidade → itens na ordem da fila; especialidade sem ninguém na fila não aparece. "
                    + "Os itens vêm com nome do paciente, situação, risco e presença confirmada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filas agrupadas por especialidade/exame"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso à fila completa")
    })
    public Map<String, List<FilaItemResponse>> listarTodas() {
        MarcaAiPrincipal principal = AuthContext.atual();
        if (principal.papel() != Papel.SECRETARIA && principal.papel() != Papel.ADMIN) {
            throw new AccessDeniedException("Somente a secretaria consulta a fila completa por especialidade");
        }
        Map<String, List<FilaItemResponse>> porEspecialidade = new LinkedHashMap<>();
        for (String especialidade : encaminhamentoService.listarEspecialidades()) {
            List<FilaItemResponse> itens = filaDetalhesService.detalhar(filaService.listarPorEspecialidade(especialidade));
            if (!itens.isEmpty()) {
                porEspecialidade.put(especialidade, itens);
            }
        }
        return porEspecialidade;
    }

    @GetMapping
    @Operation(summary = "Lista a fila por especialidade (secretaria) ou pelos pacientes que um médico encaminhou",
            description = "Informe **um** dos parâmetros:\n"
                    + "- `especialidade`: fila completa, com nome, situação e risco. **Papéis:** SECRETARIA, ADMIN.\n"
                    + "- `encaminhadoPor`: só os pacientes que o médico encaminhou, com a posição de cada um na fila da "
                    + "especialidade. **Papéis:** o próprio MEDICO_UBS (o id é o `profissionalId` do login), SECRETARIA, ADMIN.\n\n"
                    + "A ordem é a do score da triagem, respeitando os ajustes manuais. Quem já foi atendido sai da fila.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Itens na ordem da fila (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Nenhum dos dois parâmetros informado"),
            @ApiResponse(responseCode = "403", description = "Médico pedindo a fila de outro médico, ou papel sem acesso à fila por especialidade")
    })
    public List<FilaItemResponse> listar(
            @Parameter(description = "Especialidade ou exame (visão da secretaria)", example = "Cardiologia")
            @RequestParam(required = false) String especialidade,
            @Parameter(description = "Id do profissional (médico da UBS) que encaminhou", example = "1")
            @RequestParam(required = false) Long encaminhadoPor) {

        if (especialidade == null && encaminhadoPor == null) {
            throw new RequisicaoInvalidaException("Informe o parâmetro 'especialidade' ou 'encaminhadoPor'");
        }

        MarcaAiPrincipal principal = AuthContext.atual();

        if (encaminhadoPor != null) {
            boolean ehOProprioMedico = principal.papel() == Papel.MEDICO_UBS
                    && profissionalRepository.findByUsuarioId(principal.usuarioId())
                            .map(Profissional::getId)
                            .map(encaminhadoPor::equals)
                            .orElse(false);
            boolean ehGestao = principal.papel() == Papel.SECRETARIA || principal.papel() == Papel.ADMIN;
            if (!ehOProprioMedico && !ehGestao) {
                throw new AccessDeniedException("Médico só pode consultar a própria fila de encaminhamentos");
            }
            return filaService.listarPorProfissional(encaminhadoPor);
        }

        if (principal.papel() != Papel.SECRETARIA && principal.papel() != Papel.ADMIN) {
            throw new AccessDeniedException("Somente a secretaria consulta a fila completa por especialidade");
        }
        return filaDetalhesService.detalhar(filaService.listarPorEspecialidade(especialidade));
    }

    @PatchMapping("/{itemId}/override")
    @Operation(summary = "Ajusta manualmente a posição de um item na fila (exige justificativa)",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "O item passa a ocupar a posição pedida; os demais seguem a ordem do score. A justificativa fica "
                    + "gravada para auditoria (LGPD). Se dois itens pedem a mesma posição, o segundo entra logo à frente "
                    + "da fila natural.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajuste aplicado; devolve a posição final do item"),
            @ApiResponse(responseCode = "400", description = "Posição menor que 1, ou justificativa em branco"),
            @ApiResponse(responseCode = "403", description = "Papel sem permissão de ajuste manual"),
            @ApiResponse(responseCode = "404", description = "Item de fila não encontrado"),
            @ApiResponse(responseCode = "409", description = "O paciente já foi atendido e saiu da fila")
    })
    public FilaItemResponse override(
            @Parameter(description = "Id do item de fila (campo itemId)", example = "7") @PathVariable Long itemId,
            @Valid @RequestBody OverrideRequest request) {
        MarcaAiPrincipal principal = AuthContext.atual();
        if (principal.papel() != Papel.SECRETARIA && principal.papel() != Papel.ADMIN) {
            throw new AccessDeniedException("Somente a secretaria pode fazer ajuste manual da fila");
        }
        return filaService.aplicarOverride(itemId, request);
    }
}
