import type {
  AgendamentoRequest,
  AgendamentoResponse,
  AnaliseIAResponse,
  AtendimentoRequest,
  AtendimentoResponse,
  CidResponse,
  DocumentoRequest,
  DocumentoResponse,
  EncaminhamentoRequest,
  EncaminhamentoResponse,
  FilaItemResponse,
  LoginRequest,
  LoginResponse,
  OverrideRequest,
  PainelResumoResponse,
  ResultadoExameRequest,
  ResultadoExameResponse,
  TimelineResponse,
  VagaHorarioResponse,
  VagaMarcadaResponse,
  RelatorioFilaResponse,
  ProfissionalRequest,
  ProfissionalResponse,
  UnidadeResponse,
  PacienteResumoResponse,
  VagaLoteRequest,
  VagaLoteResponse,
  PainelIndicadoresResponse,
  AgendaEncaminhamentoResponse,
  AgendaVagaResponse,
  SituacaoAtendimento,
  StatusVaga,
} from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

let token: string | null = null;

export function setToken(value: string | null) {
  token = value;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const body = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const mensagem = body?.mensagem ?? `Erro ${response.status} ao chamar ${path}`;
    throw new ApiError(response.status, mensagem);
  }

  return body as T;
}

/** Query string só com os filtros preenchidos. */
function parametros(filtros: Record<string, string | undefined>): URLSearchParams {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor) params.set(chave, valor);
  }
  return params;
}

export const api = {
  login: (data: LoginRequest) =>
    request<LoginResponse>("/auth/login", { method: "POST", body: JSON.stringify(data) }),

  criarAtendimento: (data: AtendimentoRequest) =>
    request<AtendimentoResponse>("/atendimentos", { method: "POST", body: JSON.stringify(data) }),
  buscarAtendimento: (id: number) => request<AtendimentoResponse>(`/atendimentos/${id}`),
  listarDocumentosExigidos: (especialidade: string) =>
    request<string[]>(`/especialidades/${encodeURIComponent(especialidade)}/documentos-exigidos`),
  listarAtendimentosDoPaciente: (pacienteId: number) =>
    request<AtendimentoResponse[]>(`/pacientes/${pacienteId}/atendimentos`),

  listarCids: (especialidade: string) =>
    request<CidResponse[]>(`/cids?especialidade=${encodeURIComponent(especialidade)}`),
  listarEspecialidades: () => request<string[]>("/especialidades"),

  criarEncaminhamento: (data: EncaminhamentoRequest) =>
    request<EncaminhamentoResponse>("/encaminhamentos", { method: "POST", body: JSON.stringify(data) }),
  buscarEncaminhamento: (id: number) => request<EncaminhamentoResponse>(`/encaminhamentos/${id}`),
  timelineEncaminhamento: (id: number) => request<TimelineResponse>(`/encaminhamentos/${id}/timeline`),
  adicionarDocumento: (id: number, data: DocumentoRequest) =>
    request<DocumentoResponse>(`/encaminhamentos/${id}/documentos`, {
      method: "POST",
      body: JSON.stringify(data),
    }),
  listarDocumentos: (id: number) => request<DocumentoResponse[]>(`/encaminhamentos/${id}/documentos`),
  analiseIA: (id: number) => request<AnaliseIAResponse>(`/encaminhamentos/${id}/analise-ia`),
  posicaoFila: (id: number) => request<FilaItemResponse>(`/encaminhamentos/${id}/posicao-fila`),
  agendamentoDoEncaminhamento: (id: number) =>
    request<AgendamentoResponse>(`/encaminhamentos/${id}/agendamento`),

  resultadoExame: (id: number) =>
    request<ResultadoExameResponse>(`/encaminhamentos/${id}/resultado-exame`),
  registrarResultadoExame: (id: number, data: ResultadoExameRequest) =>
    request<ResultadoExameResponse>(`/encaminhamentos/${id}/resultado-exame`, {
      method: "POST",
      body: JSON.stringify(data),
    }),

  filaTodas: () => request<Record<string, FilaItemResponse[]>>("/fila/todas"),
  filaPorEspecialidade: (especialidade: string) =>
    request<FilaItemResponse[]>(`/fila?especialidade=${encodeURIComponent(especialidade)}`),
  filaPorMedico: (encaminhadoPor: number) =>
    request<FilaItemResponse[]>(`/fila?encaminhadoPor=${encaminhadoPor}`),
  overrideFila: (itemId: number, data: OverrideRequest) =>
    request<FilaItemResponse>(`/fila/${itemId}/override`, {
      method: "PATCH",
      body: JSON.stringify(data),
    }),

  listarVagas: (especialidade: string) =>
    request<VagaHorarioResponse[]>(`/vagas?especialidade=${encodeURIComponent(especialidade)}`),
  /** `de`/`ate` no formato AAAA-MM-DD, inclusivos; sem `ate`, sem limite final. */
  listarVagasMarcadas: (especialidade: string, de: string, ate?: string) => {
    const params = new URLSearchParams({ especialidade, de });
    if (ate) params.set("ate", ate);
    return request<VagaMarcadaResponse[]>(`/vagas/marcadas?${params}`);
  },
  criarAgendamento: (data: AgendamentoRequest) =>
    request<AgendamentoResponse>("/agendamentos", { method: "POST", body: JSON.stringify(data) }),
  confirmarPresenca: (id: number) =>
    request<AgendamentoResponse>(`/agendamentos/${id}/confirmar-presenca`, { method: "POST" }),
  cancelarAgendamento: (id: number, motivo: string) =>
    request<AgendamentoResponse>(`/agendamentos/${id}/cancelar`, { method: "POST", body: JSON.stringify({ motivo }) }),
  remarcarAgendamento: (id: number, vagaId: number, motivo: string) =>
    request<AgendamentoResponse>(`/agendamentos/${id}/remarcar`, {
      method: "POST",
      body: JSON.stringify({ vagaId, motivo }),
    }),
  anteciparAgendamento: (id: number, vagaId: number, motivo: string) =>
    request<AgendamentoResponse>(`/agendamentos/${id}/antecipar`, {
      method: "POST",
      body: JSON.stringify({ vagaId, motivo }),
    }),

  /** Filtros opcionais; datas AAAA-MM-DD, inclusivas. */
  agendaEncaminhamentos: (filtros: { situacao?: SituacaoAtendimento; paciente?: string; de?: string; ate?: string } = {}) =>
    request<AgendaEncaminhamentoResponse[]>(`/agenda/encaminhamentos?${parametros(filtros)}`),
  agendaVagas: (filtros: { status?: StatusVaga; de?: string; ate?: string } = {}) =>
    request<AgendaVagaResponse[]>(`/agenda/vagas?${parametros(filtros)}`),

  meusEncaminhamentos: (pacienteId: number) =>
    request<EncaminhamentoResponse[]>(`/pacientes/${pacienteId}/encaminhamentos`),

  relatorioFila: (especialidade: string) =>
    request<RelatorioFilaResponse>(`/fila/relatorio-ia?especialidade=${encodeURIComponent(especialidade)}`),
  gerarRelatorioFila: (especialidade: string) =>
    request<RelatorioFilaResponse>(`/fila/relatorio-ia?especialidade=${encodeURIComponent(especialidade)}`, {
      method: "POST",
    }),

  listarProfissionais: (especialidade?: string) =>
    request<ProfissionalResponse[]>(
      especialidade ? `/profissionais?especialidade=${encodeURIComponent(especialidade)}` : "/profissionais",
    ),
  cadastrarProfissional: (data: ProfissionalRequest) =>
    request<ProfissionalResponse>("/profissionais", { method: "POST", body: JSON.stringify(data) }),
  listarUnidades: () => request<UnidadeResponse[]>("/unidades"),
  buscarPacientes: (nome: string) =>
    request<PacienteResumoResponse[]>(`/pacientes?nome=${encodeURIComponent(nome)}`),
  criarVagasEmLote: (data: VagaLoteRequest) =>
    request<VagaLoteResponse>("/vagas/lote", { method: "POST", body: JSON.stringify(data) }),

  painelResumo: () => request<PainelResumoResponse>("/painel/resumo"),
  painelIndicadores: () => request<PainelIndicadoresResponse>("/painel/indicadores"),
  painelDemandaPorEspecialidade: () => request<Record<string, number>>("/painel/demanda-por-especialidade"),
};
