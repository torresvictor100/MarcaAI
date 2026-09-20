package com.marcaai.triagemia;

/**
 * Redige a justificativa em texto da posição na fila. Nunca decide score nem bloqueio — isso é sempre
 * do motor de regras determinístico em {@link TriagemIAService} (ADR-004). Ponto de extensão via
 * Strategy: {@link AnthropicJustificativaGenerator} (chamada real à IA) e
 * {@link TemplateJustificativaGenerator} (fallback determinístico), combinados por
 * {@link CompositeJustificativaGenerator}.
 */
public interface JustificativaGenerator {
    String gerar(ContextoJustificativa contexto);
}
