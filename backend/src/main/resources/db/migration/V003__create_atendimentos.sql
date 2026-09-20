CREATE TABLE atendimentos (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES pacientes (id),
    profissional_id BIGINT NOT NULL REFERENCES profissionais (id),
    unidade_id BIGINT NOT NULL REFERENCES unidades_saude (id),
    data TIMESTAMP NOT NULL,
    notas TEXT,
    classificacao_risco VARCHAR(20) NOT NULL
);
