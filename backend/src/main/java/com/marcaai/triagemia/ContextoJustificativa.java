package com.marcaai.triagemia;

import java.util.List;
import java.util.Map;

public record ContextoJustificativa(
        Long encaminhamentoId,
        String especialidadeOuExame,
        double score,
        Map<String, Object> fatoresConsiderados,
        List<Irregularidade> irregularidades,
        boolean bloqueado
) {
}
