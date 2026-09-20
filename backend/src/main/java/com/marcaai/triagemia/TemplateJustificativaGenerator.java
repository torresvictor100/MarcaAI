package com.marcaai.triagemia;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Fallback determinístico — sempre disponível, usado quando a chamada à API da Anthropic falha ou está desabilitada. */
@Component
public class TemplateJustificativaGenerator implements JustificativaGenerator {

    @Override
    public String gerar(ContextoJustificativa contexto) {
        if (contexto.bloqueado()) {
            String motivos = contexto.irregularidades().stream()
                    .filter(i -> i.severidade() == Severidade.BLOQUEANTE)
                    .map(Irregularidade::descricao)
                    .collect(Collectors.joining("; "));
            return "Encaminhamento bloqueado para revisão humana. Motivo(s): %s.".formatted(motivos);
        }

        StringBuilder texto = new StringBuilder();
        texto.append("Score de prioridade %.1f para %s. Fatores considerados: "
                .formatted(contexto.score(), contexto.especialidadeOuExame()));
        texto.append(contexto.fatoresConsiderados().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")));
        if (!contexto.irregularidades().isEmpty()) {
            String alertas = contexto.irregularidades().stream()
                    .map(Irregularidade::descricao)
                    .collect(Collectors.joining("; "));
            texto.append(". Alertas (não bloqueantes): ").append(alertas);
        }
        texto.append(".");
        return texto.toString();
    }
}
