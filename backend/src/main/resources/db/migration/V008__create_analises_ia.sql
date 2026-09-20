CREATE TABLE analises_ia (
    id BIGSERIAL PRIMARY KEY,
    encaminhamento_id BIGINT NOT NULL UNIQUE REFERENCES encaminhamentos (id),
    score_prioridade DOUBLE PRECISION NOT NULL,
    fatores_considerados TEXT NOT NULL,
    irregularidades TEXT NOT NULL,
    justificativa_texto TEXT NOT NULL,
    bloqueado BOOLEAN NOT NULL,
    data_analise TIMESTAMP NOT NULL
);
