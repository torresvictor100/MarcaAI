package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Vagas")
public class VagaHorarioController {

    private final AgendamentoService agendamentoService;

    @GetMapping("/vagas")
    @Operation(summary = "Lista vagas disponíveis para uma especialidade/exame",
            description = "**Papéis:** qualquer usuário logado.\n\n"
                    + "Só vagas DISPONIVEL, por data e hora, com nomes do profissional e da unidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vagas livres (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'especialidade' ausente")
    })
    public List<VagaHorarioResponse> listar(
            @Parameter(description = "Especialidade ou exame (sem diferenciar maiúsculas)", example = "Cardiologia", required = true)
            @RequestParam String especialidade) {
        return agendamentoService.listarDisponiveisComNomes(especialidade);
    }

    @PostMapping("/vagas/lote")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    @Operation(summary = "Abre vagas em lote para um profissional, repetindo toda semana nos dias escolhidos",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "A especialidade das vagas é a do profissional. Ignora (sem duplicar) horários que o profissional "
                    + "já tem e nunca cria vaga no passado. Período de no máximo 183 dias e no máximo 500 vagas por lote.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vagas criadas; informa quantas foram ignoradas por já existirem"),
            @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente, nenhum dia da semana ou duração fora de 10 a 240 minutos"),
            @ApiResponse(responseCode = "403", description = "Papel sem permissão para abrir vagas"),
            @ApiResponse(responseCode = "404", description = "Profissional ou unidade não encontrado"),
            @ApiResponse(responseCode = "422", description = "Período invertido ou longo demais, horário final antes do inicial, "
                    + "mais de 500 vagas ou nenhum horário futuro")
    })
    public ResponseEntity<VagaLoteResponse> criarEmLote(@Valid @RequestBody VagaLoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agendamentoService.criarVagasEmLote(request));
    }

    @GetMapping("/vagas/marcadas")
    @PreAuthorize("hasAnyRole('SECRETARIA', 'ADMIN')")
    @Operation(summary = "Lista vagas já marcadas de uma especialidade/exame, com o paciente de cada uma",
            description = "**Papéis:** SECRETARIA, ADMIN.\n\n"
                    + "Sem 'de', considera a partir de hoje; sem 'ate', sem limite final. Datas no formato AAAA-MM-DD, inclusivas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vagas ocupadas no período, por data e hora (pode ser lista vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'especialidade' ausente ou data fora do formato AAAA-MM-DD"),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso às vagas marcadas"),
            @ApiResponse(responseCode = "422", description = "'ate' antes de 'de'")
    })
    public List<VagaMarcadaResponse> listarMarcadas(
            @Parameter(description = "Especialidade ou exame", example = "Cardiologia", required = true)
            @RequestParam String especialidade,
            @Parameter(description = "Data inicial AAAA-MM-DD, inclusive (padrão: hoje)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Data final AAAA-MM-DD, inclusive (padrão: sem limite)", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return agendamentoService.listarMarcadasPorEspecialidade(especialidade, de != null ? de : LocalDate.now(), ate);
    }
}
