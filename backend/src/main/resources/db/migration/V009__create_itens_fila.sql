CREATE TABLE itens_fila (
    id BIGSERIAL PRIMARY KEY,
    encaminhamento_id BIGINT NOT NULL UNIQUE REFERENCES encaminhamentos (id),
    especialidade_ou_exame VARCHAR(100) NOT NULL,
    profissional_id BIGINT NOT NULL REFERENCES profissionais (id),
    score_atual DOUBLE PRECISION NOT NULL,
    override_manual BOOLEAN NOT NULL DEFAULT FALSE,
    posicao_override INTEGER,
    justificativa_override TEXT
);
