-- ADR-010: depois que a V026 cifrou os CPFs e preencheu o índice cego de todas as linhas, ele passa a ser obrigatório.
ALTER TABLE pacientes ALTER COLUMN cpf_hash SET NOT NULL;
