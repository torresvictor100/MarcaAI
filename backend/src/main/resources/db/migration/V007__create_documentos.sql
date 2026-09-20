CREATE TABLE documentos (
    id BIGSERIAL PRIMARY KEY,
    encaminhamento_id BIGINT NOT NULL REFERENCES encaminhamentos (id),
    tipo VARCHAR(100) NOT NULL,
    referencia_arquivo VARCHAR(300) NOT NULL,
    data_emissao DATE NOT NULL,
    validade DATE
);
