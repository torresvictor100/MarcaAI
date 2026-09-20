-- Seed de dados "mestre" para a demonstração do MVP (vídeo do hackathon).
-- Os 3 cenários de demonstração (caso urgente furando fila, documento irregular bloqueado, caso normal
-- seguindo o fluxo completo) são criados AO VIVO via a coleção Postman em cima destes dados-base — assim
-- o TriagemIAService, o evento de documentos completos e a chamada (ou fallback) à Anthropic rodam de
-- verdade na demonstração, em vez de dados de análise pré-calculados artificialmente.
--
-- Senha de todos os usuários de demonstração: Senha123!  (hash gerado com pgcrypto/bcrypt, compatível
-- com o BCryptPasswordEncoder do Spring Security).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuarios (nome, papel, login, senha_hash) VALUES
    ('Dr. Bruno (UBS)', 'MEDICO_UBS', 'bruno.ubs', crypt('Senha123!', gen_salt('bf', 10))),
    ('Dra. Elisa (Cardiologia)', 'ESPECIALISTA', 'elisa.cardio', crypt('Senha123!', gen_salt('bf', 10))),
    ('Laboratório Central', 'UNIDADE_EXAME', 'lab.central', crypt('Senha123!', gen_salt('bf', 10))),
    ('Secretaria de Saúde', 'SECRETARIA', 'secretaria', crypt('Senha123!', gen_salt('bf', 10))),
    ('Administrador MarcaAI', 'ADMIN', 'admin', crypt('Senha123!', gen_salt('bf', 10))),
    ('Marta Aparecida', 'PACIENTE', 'marta.paciente', crypt('Senha123!', gen_salt('bf', 10))),
    ('João Normal', 'PACIENTE', 'joao.paciente', crypt('Senha123!', gen_salt('bf', 10))),
    ('Carlos Irregular', 'PACIENTE', 'carlos.paciente', crypt('Senha123!', gen_salt('bf', 10)));

INSERT INTO unidades_saude (nome, tipo, endereco) VALUES
    ('UBS Jardim das Flores', 'UBS', 'Rua das Flores, 100'),
    ('Centro Especializado Cardio', 'ESPECIALIZADA', 'Av. dos Especialistas, 200'),
    ('Laboratório Central', 'UNIDADE_EXAME', 'Rua dos Exames, 300');

INSERT INTO profissionais (nome, registro_conselho, tipo, especialidade, usuario_id) VALUES
    ('Dr. Bruno (UBS)', 'CRM-SP 111111', 'MEDICO_UBS', 'Clínica Geral',
        (SELECT id FROM usuarios WHERE login = 'bruno.ubs')),
    ('Dra. Elisa (Cardiologia)', 'CRM-SP 222222', 'ESPECIALISTA', 'Cardiologia',
        (SELECT id FROM usuarios WHERE login = 'elisa.cardio'));

INSERT INTO pacientes (nome, cpf, data_nascimento, contato, usuario_id) VALUES
    ('Marta Aparecida', '11111111111', '1978-03-14', '(11) 90000-0001',
        (SELECT id FROM usuarios WHERE login = 'marta.paciente')),
    ('João Normal', '22222222222', '1990-06-20', '(11) 90000-0002',
        (SELECT id FROM usuarios WHERE login = 'joao.paciente')),
    ('Carlos Irregular', '33333333333', '1985-11-02', '(11) 90000-0003',
        (SELECT id FROM usuarios WHERE login = 'carlos.paciente'));

-- Lista fechada de CIDs para a demonstração (não é a tabela oficial completa de CID-10).
INSERT INTO cids (codigo, descricao) VALUES
    ('I20', 'Angina pectoris'),
    ('I10', 'Hipertensão essencial'),
    ('M54', 'Dorsalgia (dor nas costas)');

INSERT INTO cid_especialidades (cid_id, especialidade) VALUES
    ((SELECT id FROM cids WHERE codigo = 'I20'), 'Cardiologia'),
    ((SELECT id FROM cids WHERE codigo = 'I10'), 'Cardiologia'),
    ((SELECT id FROM cids WHERE codigo = 'M54'), 'Ortopedia');

-- Documentos exigidos configurados por especialidade/exame (TipoDocumentoExigido).
INSERT INTO tipos_documento_exigido (especialidade_ou_exame, tipo_documento) VALUES
    ('Cardiologia', 'GUIA_ENCAMINHAMENTO'),
    ('Cardiologia', 'EXAME_ANTERIOR'),
    ('Cardiologia', 'LAUDO');

-- Vagas disponíveis para o passo de agendamento da demonstração.
INSERT INTO vagas_horario (unidade_id, profissional_id, especialidade_ou_exame, data_hora, status) VALUES
    ((SELECT id FROM unidades_saude WHERE nome = 'Centro Especializado Cardio'),
     (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 222222'),
     'Cardiologia', CURRENT_TIMESTAMP + INTERVAL '3 days', 'DISPONIVEL'),
    ((SELECT id FROM unidades_saude WHERE nome = 'Centro Especializado Cardio'),
     (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 222222'),
     'Cardiologia', CURRENT_TIMESTAMP + INTERVAL '5 days', 'DISPONIVEL');
