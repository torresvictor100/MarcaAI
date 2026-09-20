package com.marcaai.atendimento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Os nomes de paciente/profissional/unidade vão junto dos ids para quem consome a API (ex.: a tela da
 * secretaria) não precisar resolver cada id em outra chamada; ficam {@code null} se o cadastro não existir.
 */
@Schema(description = "Atendimento na UBS, com os nomes já resolvidos (null se o cadastro não existir)")
public record AtendimentoResponse(
        @Schema(description = "Id do atendimento", example = "1")
        Long id,
        @Schema(description = "Id do paciente", example = "2")
        Long pacienteId,
        @Schema(description = "Nome do paciente", example = "João Normal", nullable = true)
        String pacienteNome,
        @Schema(description = "Id do médico da UBS que registrou", example = "1")
        Long profissionalId,
        @Schema(description = "Nome do médico da UBS", example = "Dr. Bruno (UBS)", nullable = true)
        String profissionalNome,
        @Schema(description = "Id da UBS", example = "1")
        Long unidadeId,
        @Schema(description = "Nome da UBS", example = "UBS Jardim das Flores", nullable = true)
        String unidadeNome,
        @Schema(description = "Endereço da UBS", example = "Rua das Flores, 100", nullable = true)
        String unidadeEndereco,
        @Schema(description = "Data e hora do atendimento", example = "2026-09-24T09:30:00")
        LocalDateTime data,
        @Schema(description = "Anotações clínicas do médico", example = "Dor torácica aos esforços há 2 semanas.", nullable = true)
        String notas,
        @Schema(description = "Classificação de risco (protocolo de Manchester)")
        ClassificacaoRisco classificacaoRisco
) {
    public static AtendimentoResponse of(Atendimento atendimento, String pacienteNome, String profissionalNome,
                                         String unidadeNome, String unidadeEndereco) {
        return new AtendimentoResponse(
                atendimento.getId(),
                atendimento.getPacienteId(),
                pacienteNome,
                atendimento.getProfissionalId(),
                profissionalNome,
                atendimento.getUnidadeId(),
                unidadeNome,
                unidadeEndereco,
                atendimento.getData(),
                atendimento.getNotas(),
                atendimento.getClassificacaoRisco());
    }
}
