package com.marcaai.painel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/painel")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
@Tag(name = "Painel")
public class PainelController {

    private static final String PAPEIS = "**Papéis:** SECRETARIA, ADMIN.\n\n";

    private final PainelService painelService;
    private final PainelIndicadoresService painelIndicadoresService;

    @GetMapping("/resumo")
    @Operation(summary = "Métricas gerais: atendidos, encaminhados, vagas, agendamentos",
            description = PAPEIS + "Contagens da rede inteira no momento da chamada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Métricas gerais"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao painel")
    })
    public PainelResumoResponse resumo() {
        return painelService.resumo();
    }

    @GetMapping("/indicadores")
    @Operation(summary = "Indicadores para os gráficos: situação dos encaminhamentos, fila por risco, demanda × vagas e movimento de 30 dias",
            description = PAPEIS + "Tudo o que os gráficos do painel mostram, numa chamada só.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indicadores do painel"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao painel")
    })
    public PainelIndicadoresResponse indicadores() {
        return painelIndicadoresService.indicadores();
    }

    @GetMapping("/demanda-por-especialidade")
    @Operation(summary = "Quantidade de encaminhamentos por especialidade/exame",
            description = PAPEIS + "Mapa de especialidade/exame → total de encaminhamentos, em qualquer situação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total por especialidade/exame, ex.: {\"Cardiologia\": 24, \"Ortopedia\": 22}"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ao painel")
    })
    public Map<String, Long> demandaPorEspecialidade() {
        return painelService.demandaPorEspecialidade();
    }
}
