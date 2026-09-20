-- Relatórios de sugestão sobre uma fila (ADR-008). As sugestões são calculadas por regras determinísticas;
-- a IA só redige o texto. Nada aqui altera a fila: fica o histórico de quem gerou e quando (auditoria).
CREATE TABLE relatorios_fila (
    id BIGSERIAL PRIMARY KEY,
    especialidade_ou_exame VARCHAR(100) NOT NULL,
    gerado_em TIMESTAMP NOT NULL,
    gerado_por VARCHAR(100) NOT NULL,
    total_na_fila INTEGER NOT NULL,
    sugestoes_json TEXT NOT NULL,
    texto TEXT NOT NULL,
    origem_texto VARCHAR(20) NOT NULL
);

CREATE INDEX idx_relatorios_fila_especialidade ON relatorios_fila (lower(especialidade_ou_exame), gerado_em DESC);
