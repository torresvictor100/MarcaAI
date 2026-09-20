import type { ClassificacaoRisco, StatusAgendamento, StatusVaga, TipoEncaminhamento } from "./types";

// O backend manda LocalDate ("2026-09-23") e LocalDateTime ("2026-10-08T12:47:46.21882") sem fuso.
// As datas são montadas direto dos componentes do texto, sem passar por `new Date(...)`: um LocalDate
// convertido por `new Date` é lido como meia-noite UTC e, no fuso do Brasil, apareceria um dia antes.
const PADRAO_DATA = /^(\d{4})-(\d{2})-(\d{2})/;
const PADRAO_HORA = /T(\d{2}):(\d{2})/;

/** Data de hoje no fuso do navegador, em AAAA-MM-DD. (toISOString() usaria UTC e viraria o dia às 21h no Brasil.) */
export function hojeLocal(): string {
  const agora = new Date();
  const mes = String(agora.getMonth() + 1).padStart(2, "0");
  const dia = String(agora.getDate()).padStart(2, "0");
  return `${agora.getFullYear()}-${mes}-${dia}`;
}

/** "2026-09-23" (ou o início de um LocalDateTime) → "23/09/2026". Texto fora do padrão volta como veio. */
export function formatarData(valor: string): string {
  const data = PADRAO_DATA.exec(valor);
  if (!data) return valor;
  const [, ano, mes, dia] = data;
  return `${dia}/${mes}/${ano}`;
}

/** "2026-10-08T12:47:46.21882" → "08/10/2026 às 12:47". Sem hora no texto, mostra só a data. */
export function formatarDataHora(valor: string): string {
  const hora = PADRAO_HORA.exec(valor);
  if (!hora) return formatarData(valor);
  return `${formatarData(valor)} às ${hora[1]}:${hora[2]}`;
}

const DIAS_SEMANA = ["domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado"];
const MESES = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro",
  "novembro", "dezembro"];

/**
 * "2026-09-25T09:00:00" → "quinta-feira, 25 de setembro de 2026, às 9h" (com minutos: "às 9h30").
 * O dia da semana sai de `new Date(ano, mes, dia)`, que usa o fuso local e não desloca a data.
 */
export function formatarDataPorExtenso(valor: string): string {
  const data = PADRAO_DATA.exec(valor);
  if (!data) return valor;
  const [, ano, mes, dia] = data;
  const diaSemana = DIAS_SEMANA[new Date(Number(ano), Number(mes) - 1, Number(dia)).getDay()];
  const texto = `${diaSemana}, ${Number(dia)} de ${MESES[Number(mes) - 1]} de ${ano}`;
  const hora = PADRAO_HORA.exec(valor);
  if (!hora) return texto;
  return `${texto}, às ${Number(hora[1])}h${hora[2] === "00" ? "" : hora[2]}`;
}

/** Nome do cadastro quando o backend o resolveu; senão, o id — "Fábio Almeida" ou "#59". */
export function nomeOuId(nome: string | null | undefined, id: number): string {
  return nome || `#${id}`;
}

export const RISCO_LABELS: Record<ClassificacaoRisco, string> = {
  AZUL: "Azul (não urgente)",
  VERDE: "Verde (pouco urgente)",
  AMARELO: "Amarelo (urgente)",
  LARANJA: "Laranja (muito urgente)",
  VERMELHO: "Vermelho (emergência)",
};

export const STATUS_VAGA_LABELS: Record<StatusVaga, string> = {
  DISPONIVEL: "Disponível",
  RESERVADA: "Reservada",
  OCUPADA: "Ocupada",
};

export const STATUS_AGENDAMENTO_LABELS: Record<StatusAgendamento, string> = {
  CONFIRMADO: "Confirmado",
  CANCELADO: "Cancelado",
  REALIZADO: "Realizado",
};

export const TIPO_ENCAMINHAMENTO_LABELS: Record<TipoEncaminhamento, string> = {
  CONSULTA_ESPECIALISTA: "Consulta com especialista",
  EXAME: "Exame",
};

export const TIPOS_DOCUMENTO = [
  { valor: "GUIA_ENCAMINHAMENTO", rotulo: "Guia de encaminhamento" },
  { valor: "EXAME_ANTERIOR", rotulo: "Exame anterior" },
  { valor: "LAUDO", rotulo: "Laudo médico" },
];

export function rotuloTipoDocumento(tipo: string): string {
  return TIPOS_DOCUMENTO.find((t) => t.valor === tipo)?.rotulo ?? tipo;
}
