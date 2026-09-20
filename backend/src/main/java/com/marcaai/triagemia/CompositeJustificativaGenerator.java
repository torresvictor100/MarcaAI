package com.marcaai.triagemia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Bean efetivamente injetado onde {@link JustificativaGenerator} é usado. Tenta a chamada real à
 * Anthropic primeiro; se falhar por qualquer motivo (chave ausente, timeout, erro HTTP), cai para o
 * template determinístico — o fluxo de priorização nunca trava por indisponibilidade externa (ADR-004).
 */
@Component
@Primary
public class CompositeJustificativaGenerator implements JustificativaGenerator {

    private static final Logger log = LoggerFactory.getLogger(CompositeJustificativaGenerator.class);

    private final AnthropicJustificativaGenerator anthropicGenerator;
    private final TemplateJustificativaGenerator templateGenerator;

    public CompositeJustificativaGenerator(
            AnthropicJustificativaGenerator anthropicGenerator,
            TemplateJustificativaGenerator templateGenerator) {
        this.anthropicGenerator = anthropicGenerator;
        this.templateGenerator = templateGenerator;
    }

    @Override
    public String gerar(ContextoJustificativa contexto) {
        try {
            return anthropicGenerator.gerar(contexto);
        } catch (AnthropicIndisponivelException ex) {
            log.warn("Justificativa via Anthropic indisponível ({}); usando fallback por template para o encaminhamento {}",
                    ex.getMessage(), contexto.encaminhamentoId());
            return templateGenerator.gerar(contexto);
        }
    }
}
