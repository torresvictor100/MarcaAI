package com.marcaai.encaminhamento;

/**
 * Publicado quando o encaminhamento precisa de (nova) triagem: ao ser criado com documentos e a cada
 * documento anexado antes do agendamento (ADR-009). Consumido pelo módulo triagemia para disparar a
 * análise — mantém encaminhamento e triagemia desacoplados (nenhum dos dois injeta o Service do outro na
 * direção encaminhamento → triagemia, evitando dependência circular entre módulos).
 */
public record TriagemSolicitadaEvent(Long encaminhamentoId) {
}
