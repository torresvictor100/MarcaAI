CREATE TABLE tipos_documento_exigido (
    id BIGSERIAL PRIMARY KEY,
    especialidade_ou_exame VARCHAR(100) NOT NULL,
    tipo_documento VARCHAR(100) NOT NULL
);
