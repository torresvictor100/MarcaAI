-- Deixa a demonstração com 8 vagas DISPONÍVEIS por especialidade/exame, em dias úteis e horários
-- redondos (08:00–11:00 e 14:00–17:00), em vez das ~88 de Cardiologia e nenhuma nas outras 3.
--
-- Esta migration roda ANTES do DemoDataSeeder, que em seguida agenda (consome vagas) até completar
-- uma cota total de agendamentos por especialidade. Por isso a quantidade inserida desconta o que o
-- seeder ainda vai agendar: 8 + (cota - agendamentos ativos já existentes) - vagas livres que sobraram.
--   * banco novo: o seeder ainda vai agendar a cota inteira → sobra 8 depois dele;
--   * banco já semeado: a cota já foi atingida, o seeder não agenda mais nada → ficam as 8.
-- As cotas abaixo espelham as de DemoDataSeeder.run() — mudar uma exige mudar a outra.

-- 1) Remove vagas livres que nunca foram usadas por um agendamento. As 2 primeiras vagas de Cardiologia
--    (V013) ficam: o teste de integração usa a vaga de id 1 diretamente.
DELETE FROM vagas_horario v
WHERE v.status = 'DISPONIVEL'
  AND v.especialidade_ou_exame IN ('Cardiologia', 'Ortopedia', 'Dermatologia', 'Hemograma Completo')
  AND NOT EXISTS (SELECT 1 FROM agendamentos a WHERE a.vaga_id = v.id)
  AND v.id NOT IN (
      SELECT id FROM vagas_horario WHERE especialidade_ou_exame = 'Cardiologia' ORDER BY id LIMIT 2
  );

-- 2) Cria as vagas que faltam para cada especialidade/exame.
WITH config (especialidade, unidade, registro_conselho, cota_seeder) AS (
    VALUES
        ('Cardiologia',        'Centro Especializado Cardio',           'CRM-SP 222222',  6),
        ('Ortopedia',          'Centro Especializado Multidisciplinar', 'CRM-SP 333333',  10),
        ('Dermatologia',       'Centro Especializado Multidisciplinar', 'CRM-SP 444444',  10),
        ('Hemograma Completo', 'Laboratório Central',                   'CRBM-SP 555555', 10)
),
quantidades AS (
    SELECT
        c.especialidade,
        (SELECT id FROM unidades_saude WHERE nome = c.unidade) AS unidade_id,
        (SELECT id FROM profissionais WHERE registro_conselho = c.registro_conselho) AS profissional_id,
        GREATEST(0,
            8
            + GREATEST(0, c.cota_seeder - (
                SELECT count(*) FROM agendamentos a
                JOIN vagas_horario v ON v.id = a.vaga_id
                WHERE v.especialidade_ou_exame = c.especialidade AND a.status <> 'CANCELADO'))
            - (SELECT count(*) FROM vagas_horario v
               WHERE v.especialidade_ou_exame = c.especialidade AND v.status = 'DISPONIVEL')
        ) AS quantidade
    FROM config c
),
horarios AS (
    SELECT q.especialidade, q.unidade_id, q.profissional_id, q.quantidade,
           (CURRENT_DATE + dia) + make_interval(hours => hora) AS data_hora,
           ROW_NUMBER() OVER (PARTITION BY q.especialidade ORDER BY dia, hora) AS ordem
    FROM quantidades q
    CROSS JOIN generate_series(1, 30) AS dia
    CROSS JOIN unnest(ARRAY[8, 9, 10, 11, 14, 15, 16, 17]) AS hora
    WHERE EXTRACT(ISODOW FROM CURRENT_DATE + dia) < 6
)
INSERT INTO vagas_horario (unidade_id, profissional_id, especialidade_ou_exame, data_hora, status)
SELECT unidade_id, profissional_id, especialidade, data_hora, 'DISPONIVEL'
FROM horarios
WHERE ordem <= quantidade;
