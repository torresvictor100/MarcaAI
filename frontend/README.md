# MarcaAI — frontend de teste

Cliente React de teste manual dos endpoints do backend MarcaAI. **Ferramenta interna** — não faz parte da entrega do hackathon (ver `../README.md`, seção "Frontend de teste (opcional)").

## Rodando

`docker compose up --build` na raiz do projeto já sobe este serviço junto com `db` e `backend` (build de produção, servido por Nginx, sem hot reload) — abre em `http://localhost:5173`.

Para hot reload durante o desenvolvimento, rode isolado (com o backend já em `http://localhost:8080`):

```bash
cp .env.example .env
npm install
npm run dev
```

Logins de seed em `../backend/src/main/resources/db/migration/V013__seed_dados_demo.sql` (senha `Senha123!` para todos).

## Estrutura

- `src/lib/` — `api.ts` (cliente HTTP) e `types.ts` (espelham os DTOs do backend).
- `src/auth/` — sessão (JWT) e guarda de rota por papel.
- `src/components/` — layout com navegação por papel e estados de carregamento/erro/vazio.
- `src/pages/` — uma tela por módulo do backend (Atendimento, Encaminhamento, Fila, Vagas/Agendamento, Resultado de Exame, Painel, Meus Encaminhamentos).
