-- Cadastro de profissionais e de vagas em lote pela secretaria: o banco garante o que a tela também valida.
-- Um registro de conselho (CRM, CRBM...) identifica um único profissional.
CREATE UNIQUE INDEX uq_profissionais_registro_conselho ON profissionais (lower(registro_conselho));
-- Um profissional não tem duas vagas no mesmo horário (lote repetido é ignorado, não duplicado).
CREATE UNIQUE INDEX uq_vagas_horario_profissional_data_hora ON vagas_horario (profissional_id, data_hora);
