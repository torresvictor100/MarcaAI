package com.marcaai.cadastro;

import com.marcaai.shared.Paciente;
import io.swagger.v3.oas.annotations.media.Schema;

/** Resumo para escolher um paciente na busca: o CPF vai mascarado, só para diferenciar homônimos. */
@Schema(description = "Paciente encontrado na busca por nome. O CPF vai mascarado, só para diferenciar homônimos (LGPD).")
public record PacienteResumoResponse(
        @Schema(description = "Id do paciente", example = "2")
        Long id,
        @Schema(description = "Nome do paciente", example = "João Normal")
        String nome,
        @Schema(description = "CPF com os 3 primeiros e os 2 últimos dígitos ocultos", example = "***.456.789-**")
        String cpfMascarado
) {

    public static PacienteResumoResponse of(Paciente p) {
        return new PacienteResumoResponse(p.getId(), p.getNome(), mascararCpf(p.getCpf()));
    }

    static String mascararCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return "***.***.***-**";
        }
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }
}
