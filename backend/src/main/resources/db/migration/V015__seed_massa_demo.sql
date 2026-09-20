-- Massa de dados adicional para demonstrar o sistema em escala realista: mais pacientes, mais
-- encaminhamentos e (para 3 das 4 especialidades/exames abaixo) poucas vagas de horário frente a uma
-- fila bem maior — o mesmo padrão de "10 vagas, resto em fila" pedido para a demonstração.
--
-- Segue o mesmo princípio já registrado na V013: esta migration NÃO insere itens_fila nem analises_ia
-- diretamente. O TriagemIAService é sempre a fonte da verdade do score e do bloqueio (ADR-004) — ele só
-- roda de verdade quando o evento de "documentos completos" é publicado pela aplicação, o que uma
-- migration SQL não consegue disparar. Os encaminhamentos abaixo já nascem com status EM_ANALISE (documentos
-- obrigatórios já anexados) ou AGUARDANDO_DOCUMENTOS (faltando anexar) — estado consistente com o que existe
-- nas tabelas. Para popular a fila de verdade a partir desta massa, rode a triagem real via API depois que a
-- aplicação subir (ver instrução no README, seção "Massa de dados de demonstração").
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------------------------
-- Novas especialidades/exame além de Cardiologia (que já tem CID, documentos exigidos e vagas de sobra
-- desde V013/V014): Ortopedia ganha profissional/vagas pela primeira vez; Dermatologia e Hemograma
-- Completo são inteiramente novos.
-- ---------------------------------------------------------------------------------------------

INSERT INTO unidades_saude (nome, tipo, endereco) VALUES
    ('Centro Especializado Multidisciplinar', 'ESPECIALIZADA', 'Av. da Saúde, 400');

INSERT INTO usuarios (nome, papel, login, senha_hash) VALUES
    ('Dr. Ricardo (Ortopedia)', 'ESPECIALISTA', 'ricardo.orto', crypt('Senha123!', gen_salt('bf', 10))),
    ('Dra. Fernanda (Dermatologia)', 'ESPECIALISTA', 'fernanda.derma', crypt('Senha123!', gen_salt('bf', 10)));

INSERT INTO profissionais (nome, registro_conselho, tipo, especialidade, usuario_id) VALUES
    ('Dr. Ricardo (Ortopedia)', 'CRM-SP 333333', 'ESPECIALISTA', 'Ortopedia',
        (SELECT id FROM usuarios WHERE login = 'ricardo.orto')),
    ('Dra. Fernanda (Dermatologia)', 'CRM-SP 444444', 'ESPECIALISTA', 'Dermatologia',
        (SELECT id FROM usuarios WHERE login = 'fernanda.derma')),
    ('Equipe Laboratório Central (Hemograma)', 'CRBM-SP 555555', 'ESPECIALISTA', 'Hemograma Completo', NULL);

INSERT INTO cids (codigo, descricao) VALUES
    ('L40', 'Psoríase'),
    ('D64', 'Outras anemias');

INSERT INTO cid_especialidades (cid_id, especialidade) VALUES
    ((SELECT id FROM cids WHERE codigo = 'L40'), 'Dermatologia'),
    ((SELECT id FROM cids WHERE codigo = 'D64'), 'Hemograma Completo');

INSERT INTO tipos_documento_exigido (especialidade_ou_exame, tipo_documento) VALUES
    ('Ortopedia', 'GUIA_ENCAMINHAMENTO'),
    ('Ortopedia', 'LAUDO'),
    ('Dermatologia', 'GUIA_ENCAMINHAMENTO'),
    ('Dermatologia', 'LAUDO'),
    ('Hemograma Completo', 'GUIA_ENCAMINHAMENTO');

-- 10 vagas disponíveis para cada uma das 3 especialidades/exame novas — poucas vagas de propósito,
-- para contrastar com a fila maior criada abaixo (Cardiologia mantém as ~102 vagas já existentes,
-- preservadas para permitir repetir a coleção Postman sem esgotar).
INSERT INTO vagas_horario (unidade_id, profissional_id, especialidade_ou_exame, data_hora, status)
SELECT
    (SELECT id FROM unidades_saude WHERE nome = 'Centro Especializado Multidisciplinar'),
    (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 333333'),
    'Ortopedia',
    CURRENT_TIMESTAMP + (numero || ' days')::interval,
    'DISPONIVEL'
FROM generate_series(2, 11) AS numero;

INSERT INTO vagas_horario (unidade_id, profissional_id, especialidade_ou_exame, data_hora, status)
SELECT
    (SELECT id FROM unidades_saude WHERE nome = 'Centro Especializado Multidisciplinar'),
    (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 444444'),
    'Dermatologia',
    CURRENT_TIMESTAMP + (numero || ' days')::interval,
    'DISPONIVEL'
FROM generate_series(2, 11) AS numero;

INSERT INTO vagas_horario (unidade_id, profissional_id, especialidade_ou_exame, data_hora, status)
SELECT
    (SELECT id FROM unidades_saude WHERE nome = 'Laboratório Central'),
    (SELECT id FROM profissionais WHERE registro_conselho = 'CRBM-SP 555555'),
    'Hemograma Completo',
    CURRENT_TIMESTAMP + (numero || ' days')::interval,
    'DISPONIVEL'
FROM generate_series(2, 11) AS numero;

-- ---------------------------------------------------------------------------------------------
-- 60 pacientes de demonstração, cada um com 1 atendimento e 1 encaminhamento, distribuídos igualmente
-- entre as 4 especialidades/exame (15 cada): Cardiologia, Ortopedia, Dermatologia, Hemograma Completo.
-- Por especialidade: 12 já com todos os documentos obrigatórios anexados (status EM_ANALISE, prontos
-- para a triagem real rodar) e 3 ainda aguardando documento (status AGUARDANDO_DOCUMENTOS) — refletindo
-- um funil de entrada realista, não só uma fila já pronta.
-- ---------------------------------------------------------------------------------------------

INSERT INTO usuarios (nome, papel, login, senha_hash)
SELECT
    'Paciente Demo ' || lpad(numero::text, 3, '0'),
    'PACIENTE',
    'paciente.demo' || lpad(numero::text, 3, '0'),
    crypt('Senha123!', gen_salt('bf', 10))
FROM generate_series(1, 60) AS numero;

INSERT INTO pacientes (nome, cpf, data_nascimento, contato, usuario_id)
SELECT
    u.nome,
    lpad((90000000000 + ROW_NUMBER() OVER (ORDER BY u.id))::text, 11, '0'),
    DATE '1960-01-01' + ((ROW_NUMBER() OVER (ORDER BY u.id) * 137) % 20000)::integer,
    '(11) 98' || lpad((ROW_NUMBER() OVER (ORDER BY u.id))::text, 6, '0'),
    u.id
FROM usuarios u
WHERE u.login LIKE 'paciente.demo%';

INSERT INTO atendimentos (paciente_id, profissional_id, unidade_id, data, notas, classificacao_risco)
SELECT
    p.id,
    (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 111111'),
    (SELECT id FROM unidades_saude WHERE nome = 'UBS Jardim das Flores'),
    CURRENT_TIMESTAMP - ((rn % 20) || ' days')::interval - ((rn % 24) || ' hours')::interval,
    'Atendimento de demonstração (massa de dados) #' || rn,
    (ARRAY['VERMELHO', 'LARANJA', 'AMARELO', 'VERDE', 'AZUL'])[((rn - 1) % 5) + 1]
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM pacientes
    WHERE usuario_id IN (SELECT id FROM usuarios WHERE login LIKE 'paciente.demo%')
) p;

INSERT INTO encaminhamentos (atendimento_id, tipo, especialidade_ou_exame, cid_id, status, urgente, justificativa_urgencia)
SELECT
    ad.id,
    CASE WHEN (ad.rn % 4) = 3 THEN 'EXAME' ELSE 'CONSULTA_ESPECIALISTA' END,
    CASE (ad.rn % 4)
        WHEN 0 THEN 'Cardiologia'
        WHEN 1 THEN 'Ortopedia'
        WHEN 2 THEN 'Dermatologia'
        WHEN 3 THEN 'Hemograma Completo'
    END,
    CASE (ad.rn % 4)
        WHEN 0 THEN (SELECT id FROM cids WHERE codigo = 'I20')
        WHEN 1 THEN (SELECT id FROM cids WHERE codigo = 'M54')
        WHEN 2 THEN (SELECT id FROM cids WHERE codigo = 'L40')
        WHEN 3 THEN (SELECT id FROM cids WHERE codigo = 'D64')
    END,
    CASE WHEN (ad.rn % 5) = 0 THEN 'AGUARDANDO_DOCUMENTOS' ELSE 'EM_ANALISE' END,
    (ad.rn % 7) = 0,
    CASE WHEN (ad.rn % 7) = 0 THEN 'Paciente relatou piora rápida dos sintomas na UBS; encaminhamento prioritário' END
FROM (
    SELECT a.id, ROW_NUMBER() OVER (ORDER BY a.id) AS rn
    FROM atendimentos a
    WHERE a.notas LIKE 'Atendimento de demonstração (massa de dados) #%'
) ad;

-- Documentos obrigatórios já anexados para os encaminhamentos EM_ANALISE. Em ~1 a cada 11, o primeiro
-- documento exigido nasce vencido de propósito — irregularidade real que só aparece quando a triagem
-- rodar de verdade, tal como aconteceria em produção (não é um dos 3 cenários roteirizados do Postman).
WITH encs_demo AS (
    SELECT e.id AS encaminhamento_id, e.especialidade_ou_exame, e.status, ROW_NUMBER() OVER (ORDER BY e.id) AS rn
    FROM encaminhamentos e
    JOIN atendimentos a ON a.id = e.atendimento_id
    WHERE a.notas LIKE 'Atendimento de demonstração (massa de dados) #%'
)
INSERT INTO documentos (encaminhamento_id, tipo, referencia_arquivo, data_emissao, validade)
SELECT
    ed.encaminhamento_id,
    tde.tipo_documento,
    'demo/encaminhamento-' || ed.encaminhamento_id || '/' || lower(tde.tipo_documento) || '.pdf',
    CURRENT_DATE - 15,
    CASE
        WHEN ed.rn % 11 = 0 AND tde.tipo_documento = (
            SELECT MIN(tipo_documento) FROM tipos_documento_exigido WHERE especialidade_ou_exame = ed.especialidade_ou_exame
        ) THEN CURRENT_DATE - 10
        ELSE CURRENT_DATE + 300
    END
FROM encs_demo ed
JOIN tipos_documento_exigido tde ON tde.especialidade_ou_exame = ed.especialidade_ou_exame
WHERE ed.status = 'EM_ANALISE';
