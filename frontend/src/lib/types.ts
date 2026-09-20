// Espelha os records de com.marcaai.*.*Request/*Response no backend.

export type Papel =
  | "PACIENTE"
  | "MEDICO_UBS"
  | "ESPECIALISTA"
  | "SECRETARIA"
  | "ADMIN";

export type ClassificacaoRisco = "AZUL" | "VERDE" | "AMARELO" | "LARANJA" | "VERMELHO";
export type TipoEncaminhamento = "CONSULTA_ESPECIALISTA" | "EXAME";
export type StatusEncaminhamento =
  | "AGUARDANDO_DOCUMENTOS"
  | "EM_ANALISE"
  | "BLOQUEADO_REVISAO"
  | "NA_FILA"
  | "AGENDADO"
  | "REALIZADO"
  | "CANCELADO";
export type StatusVaga = "DISPONIVEL" | "RESERVADA" | "OCUPADA";
export type StatusAgendamento = "CONFIRMADO" | "CANCELADO" | "REALIZADO";

export interface LoginRequest {
  login: string;
  senha: string;
}

export interface LoginResponse {
  token: string;
  usuarioId: number;
  nome: string;
  papel: Papel;
  pacienteId: number | null;
  profissionalId: number | null;
}

export interface AtendimentoRequest {
  pacienteId: number;
  // Sem profissionalId: o backend usa sempre o médico logado.
  unidadeId: number;
  data: string; // LocalDateTime ISO, ex.: 2026-09-23T10:00:00
  notas?: string;
  classificacaoRisco: ClassificacaoRisco;
}

export interface AtendimentoResponse {
  id: number;
  pacienteId: number;
  pacienteNome: string | null;
  profissionalId: number;
  profissionalNome: string | null;
  unidadeId: number;
  unidadeNome: string | null;
  unidadeEndereco: string | null;
  data: string;
  notas?: string;
  classificacaoRisco: ClassificacaoRisco;
}

export interface EncaminhamentoRequest {
  atendimentoId: number;
  tipo: TipoEncaminhamento;
  especialidadeOuExame: string;
  cidId: number;
  urgente: boolean;
  justificativaUrgencia?: string;
  /** Pelo menos um: o encaminhamento só é criado com documento anexado. */
  documentos: DocumentoRequest[];
}

export interface EncaminhamentoResponse {
  id: number;
  atendimentoId: number;
  tipo: TipoEncaminhamento;
  especialidadeOuExame: string;
  cidId: number;
  status: StatusEncaminhamento;
  urgente: boolean;
  justificativaUrgencia?: string;
}

export interface DocumentoRequest {
  tipo: string;
  referenciaArquivo: string;
  dataEmissao: string; // LocalDate ISO, ex.: 2026-09-23
  validade?: string;
}

export interface DocumentoResponse {
  id: number;
  encaminhamentoId: number;
  tipo: string;
  referenciaArquivo: string;
  dataEmissao: string;
  validade?: string;
}

export interface CidResponse {
  id: number;
  codigo: string;
  descricao: string;
  especialidadesCompativeis: string[];
}

export interface EtapaTimeline {
  nome: string;
  concluida: boolean;
}

export interface TimelineResponse {
  encaminhamentoId: number;
  etapas: EtapaTimeline[];
}

export interface AnaliseIAResponse {
  encaminhamentoId: number;
  scorePrioridade: number;
  bloqueado: boolean;
  fatoresConsiderados: string;
  irregularidades: string;
  justificativaTexto: string;
  dataAnalise: string;
}

export interface FilaItemResponse {
  itemId: number;
  encaminhamentoId: number;
  especialidadeOuExame: string;
  posicao: number;
  scoreAtual: number;
  overrideManual: boolean;
  justificativaOverride?: string;
  // Só preenchidos nas visões da secretaria (GET /fila/todas e /fila?especialidade=).
  pacienteNome: string | null;
  statusEncaminhamento: StatusEncaminhamento | null;
  classificacaoRisco: ClassificacaoRisco | null;
  presencaConfirmadaEm: string | null;
}

export interface OverrideRequest {
  posicao: number;
  justificativa: string;
}

export interface VagaHorarioResponse {
  id: number;
  unidadeId: number;
  unidadeNome: string | null;
  profissionalId: number;
  profissionalNome: string | null;
  especialidadeOuExame: string;
  dataHora: string;
  status: StatusVaga;
}

export interface VagaMarcadaResponse {
  vagaId: number;
  dataHora: string;
  especialidadeOuExame: string;
  profissionalNome: string | null;
  unidadeNome: string | null;
  agendamentoId: number | null;
  statusAgendamento: StatusAgendamento | null;
  agendadoPor: string | null;
  encaminhamentoId: number | null;
  pacienteNome: string | null;
  presencaConfirmadaEm: string | null;
}

/** Agenda do especialista logado (GET /agenda/...): só as vagas dele e o que está agendado nelas. */
export type SituacaoAtendimento = "A_ATENDER" | "ATENDIDO";

export interface AgendaEncaminhamentoResponse {
  agendamentoId: number;
  encaminhamentoId: number;
  dataHora: string;
  pacienteNome: string | null;
  especialidadeOuExame: string;
  unidadeNome: string | null;
  statusEncaminhamento: StatusEncaminhamento;
  urgente: boolean;
  presencaConfirmadaEm: string | null;
}

export interface AgendaVagaResponse {
  vagaId: number;
  dataHora: string;
  especialidadeOuExame: string;
  unidadeNome: string | null;
  status: StatusVaga;
  agendamentoId: number | null;
  encaminhamentoId: number | null;
  pacienteNome: string | null;
  statusEncaminhamento: StatusEncaminhamento | null;
  presencaConfirmadaEm: string | null;
}

export interface AgendamentoRequest {
  encaminhamentoId: number;
  vagaId: number;
}

export interface AgendamentoResponse {
  id: number;
  encaminhamentoId: number;
  vagaId: number;
  dataHora: string;
  status: StatusAgendamento;
  agendadoPor: string;
  profissionalNome: string | null;
  unidadeNome: string | null;
  unidadeEndereco: string | null;
  /** Quando o paciente confirmou que vai comparecer; null = ainda não confirmou. */
  presencaConfirmadaEm: string | null;
  historico: HistoricoAgendamentoResponse[];
}

export type AcaoAgendamento = "CANCELADO" | "REMARCADO" | "ANTECIPADO";

export interface HistoricoAgendamentoResponse {
  acao: AcaoAgendamento;
  dataHoraAnterior: string;
  dataHoraNova: string | null;
  motivo: string;
  feitoPor: string;
  feitoEm: string;
}

export interface ResultadoExameRequest {
  referenciaArquivo: string;
  dataResultado: string;
  observacoes?: string;
}

export interface ResultadoExameResponse {
  id: number;
  encaminhamentoId: number;
  referenciaArquivo: string;
  dataResultado: string;
  observacoes?: string;
}

export interface PainelResumoResponse {
  atendidosHoje: number;
  encaminhamentos: number;
  examesSolicitados: number;
  consultasAgendadas: number;
  vagasDisponiveis: number;
}

export type TipoSugestao = "SUBIR_NA_FILA" | "ANTECIPAR" | "ESPERA_LONGA" | "URGENTE_SEM_VAGA" | "FALTA_DE_VAGAS";
export type PrioridadeSugestao = "ALTA" | "MEDIA";

export interface SugestaoFila {
  tipo: TipoSugestao;
  prioridade: PrioridadeSugestao;
  encaminhamentoId: number | null;
  pacienteNome: string | null;
  posicao: number | null;
  classificacaoRisco: ClassificacaoRisco | null;
  motivo: string;
  vagaSugeridaId: number | null;
  vagaSugeridaDataHora: string | null;
}

export interface RelatorioFilaResponse {
  id: number;
  especialidadeOuExame: string;
  geradoEm: string;
  geradoPor: string;
  totalNaFila: number;
  origemTexto: "IA" | "MODELO";
  texto: string;
  sugestoes: SugestaoFila[];
}

export interface ProfissionalResponse {
  id: number;
  nome: string;
  registroConselho: string;
  tipo: "MEDICO_UBS" | "ESPECIALISTA";
  especialidade: string;
}

export interface ProfissionalRequest {
  nome: string;
  registroConselho: string;
  especialidade: string;
}

export interface PacienteResumoResponse {
  id: number;
  nome: string;
  cpfMascarado: string;
}

export interface UnidadeResponse {
  id: number;
  nome: string;
  tipo: string;
  endereco: string | null;
}

export type DiaDaSemana = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export interface VagaLoteRequest {
  profissionalId: number;
  unidadeId: number;
  dataInicio: string; // AAAA-MM-DD
  dataFim: string;
  diasDaSemana: DiaDaSemana[];
  horaInicio: string; // HH:mm
  horaFim: string;
  duracaoMinutos: number;
}

export interface VagaLoteResponse {
  especialidadeOuExame: string;
  profissionalNome: string;
  criadas: number;
  ignoradasDuplicadas: number;
  primeira: string | null;
  ultima: string | null;
}

export interface PainelIndicadoresResponse {
  numeros: {
    naFila: number;
    vermelhoAguardando: number;
    esperaMediaDias: number | null;
    ocupacaoVagasPercentual: number | null;
    bloqueadosRevisao: number;
  };
  situacaoEncaminhamentos: { status: StatusEncaminhamento; quantidade: number }[];
  filaPorRisco: { especialidadeOuExame: string; porRisco: Record<ClassificacaoRisco, number>; total: number }[];
  demandaCapacidade: { especialidadeOuExame: string; aguardandoAgendamento: number; vagasLivres: number }[];
  movimento30Dias: { data: string; atendimentos: number; encaminhamentos: number }[];
}

