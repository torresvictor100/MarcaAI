# MarcaAI

Sistema integrado para a rede pública de saúde que liga a UBS, porta de entrada do paciente, aos especialistas e às unidades de exame. O encaminhamento e o agendamento passam a ser digitais, e uma camada de IA confere os documentos e organiza a fila por **risco clínico real**, não por ordem de chegada.

Projeto do Hackathon Fase 5 do Pós-Tech Java (FIAP), de João Victor Torres e Lucas Bejamin.

## O problema

Hoje o encaminhamento da UBS para um especialista ou exame é feito em papel e por telefone, sem visibilidade da fila e sem garantia de que o caso mais grave seja atendido primeiro. São 5,7 milhões de pessoas esperando consulta com especialista no SUS (SISREG, jan/2025), com espera média recorde de 57 dias. O TCU pôs o problema na Lista de Alto Risco da Administração Pública Federal de 2026.

## Como o MarcaAI resolve

1. **Atendimento na UBS:** o médico registra o atendimento, com a classificação de risco (protocolo de Manchester).
2. **Encaminhamento digital:** o médico encaminha para um especialista ou exame, com CID e documentos anexados.
3. **Triagem automática:** quando os documentos exigidos estão completos, um **motor de regras determinístico**:
   - confere irregularidades (CID incompatível, documento vencido, falta de documento);
   - calcula o score de prioridade (risco + tempo de espera + urgência).

   A IA (API da Anthropic) **só redige a justificativa em texto**: nunca decide o score, o bloqueio nem a ordem da fila.
4. **Fila priorizada:** a secretaria de saúde agenda seguindo a ordem da fila e pode fazer ajuste manual, sempre com justificativa registrada para auditoria (LGPD).
5. **Agendamento:** a secretaria marca, remarca, antecipa ou cancela, com histórico de cada mudança. O paciente confirma presença.
6. **Atendimento realizado:** o especialista (ou o laboratório, que também é especialista) registra o resultado, e o paciente sai da fila.
7. **Acompanhamento:** o paciente vê em que etapa está, a posição na fila e o agendamento. A secretaria tem um painel com indicadores e um relatório de sugestões sobre cada fila.

## Papéis

| Papel | O que faz |
|---|---|
| `PACIENTE` | Acompanha os próprios encaminhamentos, posição na fila, agendamento e resultado; confirma presença. |
| `MEDICO_UBS` | Registra atendimento, cria encaminhamento, anexa documentos e acompanha a fila dos pacientes que encaminhou. |
| `ESPECIALISTA` | Vê a própria agenda e registra a consulta ou o exame realizado. O laboratório também usa este papel. |
| `SECRETARIA` | Vê todas as filas, agenda, faz ajuste manual, abre vagas, cadastra especialistas e acessa o painel. |
| `ADMIN` | Tudo o que a secretaria faz, mais a administração geral. |

Cada papel só lê o dado com que tem vínculo. Um paciente não vê o de outro, e um especialista só vê as consultas agendadas com ele.

## Tecnologias

Java 21, Spring Boot 3.3 (Web, Security, Data JPA, Validation), PostgreSQL 16, Flyway, JWT RS256, springdoc-openapi (Swagger), Resilience4j, Micrometer/Prometheus/Grafana, JUnit 5, Mockito, Testcontainers, JaCoCo e Docker Compose.

## Como rodar

Pré-requisitos: Docker e Docker Compose.

```bash
cp .env.example .env
scripts/gerar-segredos.sh >> .env   # chaves RSA do JWT, chave que cifra o CPF e senhas do monitoramento
# opcional: preencha ANTHROPIC_API_KEY no .env (sem ela, a justificativa usa um texto padrão)

docker compose up --build
```

| O quê | Endereço |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Documento OpenAPI | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |
| Frontend de teste | http://localhost:5173 |

Na primeira subida, o banco nasce com a massa de demonstração, e o `DemoDataSeeder` roda a triagem de verdade sobre ela. A API responde em segundos, mas a fila e os agendamentos da massa terminam de aparecer em 1 a 2 minutos. Para recomeçar do zero: `docker compose down -v && docker compose up --build`.

### Sem Docker (desenvolvimento)

Pré-requisitos: Java 21 e PostgreSQL 16 rodando localmente.

```bash
set -a; source .env; set +a      # JWT_PRIVATE_KEY, JWT_PUBLIC_KEY e MARCAAI_CRIPTO_CHAVE são obrigatórias
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Documentação da API (Swagger)

A Swagger UI documenta as 40 operações da API, em seções na ordem do fluxo: Autenticação → Cadastro → Atendimento → CID → Encaminhamento → Paciente → Análise de IA → Fila → Relatório IA da fila → Vagas → Agendamento → Agenda do especialista → Resultado de exame → Painel.

1. Chame `POST /auth/login` (a única rota pública) com um dos [usuários de demonstração](#usuários-de-demonstração).
2. Copie o `token` da resposta, clique em **Authorize** e cole só o token.

Cada operação traz:
- os papéis que podem chamar;
- todos os status de resposta possíveis, com o motivo de cada um;
- descrição e exemplo de cada parâmetro e de cada campo de requisição e resposta.

O teste `DocumentacaoOpenApiTest` quebra o build se uma rota nova chegar sem documentação. Para desligar o Swagger em produção: `MARCAAI_SWAGGER_HABILITADO=false`.

## Coleção Postman

Importe [`postman/marcaai.postman_collection.json`](postman/marcaai.postman_collection.json) e o ambiente [`postman/local.postman_environment.json`](postman/local.postman_environment.json) e rode as pastas em ordem, começando por **Auth**, que guarda os tokens no ambiente.

A coleção chama as 40 operações da API (o `DocumentacaoOpenApiTest` confere isso) e tem teste de status e de conteúdo em todas as requisições, inclusive nas recusas de acesso (401/403) e de regra (400/409/422). Ela traz três cenários de demonstração:
- **Urgente:** Marta, risco vermelho, passa à frente na fila.
- **Irregular:** Carlos, com CID incompatível, fica bloqueado para revisão.
- **Normal:** João segue o fluxo completo, do atendimento ao resultado.

Pela linha de comando, com o sistema no ar:

```bash
npx newman run postman/marcaai.postman_collection.json -e postman/local.postman_environment.json
```

Cada rodada ocupa vagas livres de Cardiologia. Quando elas acabam, os passos de agendamento falham com uma mensagem clara; recrie o banco para continuar.

## Usuários de demonstração

Senha de todos: `Senha123!`.

| Login | Papel | Observação |
|---|---|---|
| `admin` | ADMIN | Acesso administrativo geral |
| `secretaria` | SECRETARIA | Fila, agendamento, vagas e painel |
| `bruno.ubs` | MEDICO_UBS | Registra atendimento e encaminha; vê só o que ele registrou |
| `elisa.cardio` | ESPECIALISTA | Cardiologia |
| `ricardo.orto` | ESPECIALISTA | Ortopedia |
| `fernanda.derma` | ESPECIALISTA | Dermatologia |
| `lab.central` | ESPECIALISTA | Laboratório: exames de Hemograma Completo |
| `marta.paciente`, `joao.paciente`, `carlos.paciente` | PACIENTE | Pacientes dos três cenários do Postman |
| `paciente.demo001` a `paciente.demo060` | PACIENTE | Massa de demonstração, espalhada por todas as situações |
| `paciente.risco01` a `paciente.risco24` | PACIENTE | Cenários de risco para o relatório da fila; nunca são agendados automaticamente |

Alguns pacientes da massa para ver cada situação logo depois de subir um banco novo:

| Login | Especialidade | O que aparece |
|---|---|---|
| `paciente.demo001` | Ortopedia | Agendado, com data e local |
| `paciente.demo004` | Cardiologia | Na fila, com a posição |
| `paciente.demo005` | Ortopedia | Aguardando documentos |
| `paciente.demo007` | Hemograma Completo | Já atendido, com resultado |
| `paciente.demo011` | Hemograma Completo | Bloqueado para revisão (documento vencido) |

## Massa de dados de demonstração

As migrations de seed criam a rede (UBS, centros especializados e laboratório), os profissionais, as vagas e 87 pacientes:
- 3 dos cenários do Postman;
- 60 da massa, cada um com atendimento e encaminhamento em Cardiologia, Ortopedia, Dermatologia ou Hemograma Completo;
- 24 de cenários de risco.

Nenhuma migration grava score ou fila direto no banco: quem calcula é sempre o `TriagemIAService`.

Na subida, o `DemoDataSeeder` usa os serviços reais para:
- rodar a triagem da massa;
- agendar o que cabe nas vagas de cada especialidade;
- registrar alguns resultados;
- aplicar um ajuste manual de exemplo;
- confirmar a presença de metade dos agendados.

Ele é idempotente: reiniciar o backend não duplica nada. Para desligar: `MARCAAI_SEED_DEMO_AUTOMATICO=false`.

## Testes

```bash
cd backend
./mvnw verify
```

São 280 testes, unitários e de integração com PostgreSQL real via Testcontainers, com gate de cobertura de 80% (JaCoCo). A cobertura atual passa de 95% das linhas. Os testes cobrem:
- as regras de triagem, fila e agendamento;
- a matriz de acesso por papel;
- o formato de erro;
- a concorrência no agendamento;
- a documentação da API e a cobertura da coleção Postman.

O CI (`.github/workflows/ci.yml`) roda o mesmo `verify` e o build da imagem.

## Segurança

- **Autenticação:** JWT RS256 (par de chaves RSA, emissor conferido) e senhas com BCrypt de custo 12.
- **LGPD:**
  - CPF cifrado no banco (AES-256-GCM, com índice cego HMAC);
  - CPF mascarado nas buscas;
  - dados sem identificação na chamada à IA;
  - ajustes manuais e mudanças de agendamento auditados.
- **Proteções:**
  - headers de segurança (CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, COOP/COEP/CORP);
  - rate limiting (429);
  - bloqueio de login após 5 senhas erradas;
  - log de eventos de segurança.
- **Scan OWASP ZAP:** roda em ambiente descartável e gera os relatórios em `relatorios-zap/` (fora do git).

  ```bash
  docker compose build
  scripts/zap-scan.sh
  ```

## Erros da API

Todo erro volta no mesmo formato. Decida pelo campo `erro`, que é estável:

```json
{"timestamp": "...", "status": 409, "erro": "CONFLITO", "mensagem": "Vaga 7 não está disponível", "caminho": "/agendamentos"}
```

| Status | Quando |
|---|---|
| 400 | Formato ou parâmetro inválido (em `campos`, o que falhou) |
| 401 | Sem login, ou token inválido ou expirado |
| 403 | Papel sem permissão, ou dado sem vínculo com o usuário |
| 404 | Não existe |
| 409 | Conflito com o estado atual (vaga ocupada, resultado já registrado) |
| 422 | Regra de negócio violada |
| 429 | Limite de requisições ou login bloqueado |
| 503 | Banco fora do ar |

## Monitoramento (opcional)

```bash
docker compose --profile monitoramento up -d
```

- **Grafana:** http://localhost:3000, usuário `admin` e senha `GRAFANA_SENHA_ADMIN` do `.env`. O painel "MarcaAI · Backend" mostra:
  - requisições por segundo, latência p95 e erros por código;
  - estado dos Circuit Breakers da IA;
  - conexões do banco e memória.
- **Prometheus:** http://localhost:9090.

## Frontend de teste (opcional)

O `frontend/` (React, Vite e TypeScript) é uma ferramenta interna para testar a API manualmente, com uma tela por papel. **Não faz parte da entrega**, que é feita via Swagger e Postman. Ele sobe junto com o `docker compose up` em http://localhost:5173.

Para desenvolver com hot reload:

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `backend/` | API Spring Boot, organizada por módulo de domínio (controller → service → repository em cada módulo) |
| `frontend/` | Cliente de teste manual (ferramenta interna) |
| `postman/` | Coleção e ambiente Postman |
| `monitoramento/` | Configuração do Prometheus e do Grafana |
| `scripts/` | Geração de segredos e scan OWASP ZAP |
