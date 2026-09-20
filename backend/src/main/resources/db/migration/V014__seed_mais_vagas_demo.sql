-- A V013 seedou só 2 vagas de Cardiologia — suficiente para 1 demonstração, mas insuficiente para rodar
-- a coleção Postman ("Run collection") várias vezes seguidas sem reiniciar o banco (cada rodada completa
-- consome 1 vaga no passo de agendamento). Esta migration adiciona muito mais vagas para que repetir o
-- "Run" no Postman continue passando sem precisar derrubar o `docker compose` a cada tentativa.
INSERT INTO vagas_horario (unidade_id, profissional_id, especialidade_ou_exame, data_hora, status)
SELECT
    (SELECT id FROM unidades_saude WHERE nome = 'Centro Especializado Cardio'),
    (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 222222'),
    'Cardiologia',
    CURRENT_TIMESTAMP + (numero || ' days')::interval,
    'DISPONIVEL'
FROM generate_series(7, 106) AS numero;
