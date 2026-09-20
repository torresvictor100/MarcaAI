import { formatarDataHora, RISCO_LABELS } from "../lib/formatacao";
import type { ClassificacaoRisco, StatusEncaminhamento } from "../lib/types";

const STATUS_LABELS: Record<StatusEncaminhamento, string> = {
  AGUARDANDO_DOCUMENTOS: "Aguardando documentos",
  EM_ANALISE: "Em análise",
  BLOQUEADO_REVISAO: "Bloqueado p/ revisão",
  NA_FILA: "Na fila",
  AGENDADO: "Agendado",
  REALIZADO: "Realizado",
  CANCELADO: "Cancelado",
};

export function BadgeStatus({ status }: { status: StatusEncaminhamento }) {
  return <span className={`badge badge-status-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>;
}

/** Se o paciente já confirmou que vai comparecer. Texto curto para caber na tabela; o completo fica no tooltip. */
export function BadgePresenca({ confirmadaEm }: { confirmadaEm: string | null }) {
  const descricao = confirmadaEm
    ? `Presença confirmada pelo paciente em ${formatarDataHora(confirmadaEm)}`
    : "Presença ainda não confirmada pelo paciente";
  return (
    <span
      className={`badge ${confirmadaEm ? "badge-presenca-confirmada" : "badge-presenca-pendente"}`}
      title={descricao}
      aria-label={descricao}
    >
      {confirmadaEm ? "✓ Confirmada" : "A confirmar"}
    </span>
  );
}

export function BadgeRisco({ risco }: { risco: ClassificacaoRisco }) {
  return <span className={`badge badge-risco-${risco.toLowerCase()}`}>{RISCO_LABELS[risco]}</span>;
}
