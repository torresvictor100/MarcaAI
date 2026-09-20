CREATE TABLE resultados_exame (
    id BIGSERIAL PRIMARY KEY,
    encaminhamento_id BIGINT NOT NULL UNIQUE REFERENCES encaminhamentos (id),
    referencia_arquivo VARCHAR(300) NOT NULL,
    data_resultado DATE NOT NULL,
    observacoes TEXT
);
