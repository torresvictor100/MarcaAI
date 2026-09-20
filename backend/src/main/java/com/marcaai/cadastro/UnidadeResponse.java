package com.marcaai.cadastro;

import com.marcaai.shared.TipoUnidade;
import com.marcaai.shared.UnidadeSaude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Unidade de saúde da rede")
public record UnidadeResponse(
        @Schema(description = "Id da unidade", example = "1")
        Long id,
        @Schema(description = "Nome da unidade", example = "UBS Jardim das Flores")
        String nome,
        @Schema(description = "Tipo de unidade: UBS (porta de entrada), especializada ou unidade de exame")
        TipoUnidade tipo,
        @Schema(description = "Endereço", example = "Rua das Flores, 100")
        String endereco
) {
    public static UnidadeResponse of(UnidadeSaude u) {
        return new UnidadeResponse(u.getId(), u.getNome(), u.getTipo(), u.getEndereco());
    }
}
