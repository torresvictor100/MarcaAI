-- ADR-010: o CPF do paciente passa a ser gravado cifrado (AES-GCM), então a coluna precisa caber o texto
-- cifrado em Base64, e a unicidade sai do CPF (que muda a cada gravação) para o índice cego cpf_hash (HMAC).
-- Quem cifra as linhas existentes é a migração Java V026 (precisa da chave, que só existe no ambiente).
ALTER TABLE pacientes ALTER COLUMN cpf TYPE VARCHAR(255);
ALTER TABLE pacientes DROP CONSTRAINT pacientes_cpf_key;
ALTER TABLE pacientes ADD COLUMN cpf_hash VARCHAR(64);
ALTER TABLE pacientes ADD CONSTRAINT pacientes_cpf_hash_key UNIQUE (cpf_hash);
