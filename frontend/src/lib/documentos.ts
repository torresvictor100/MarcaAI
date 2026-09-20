import type { DocumentoRequest } from "./types";
import { rotuloTipoDocumento, TIPOS_DOCUMENTO } from "./formatacao";

export function documentoVazio(tipo: string = TIPOS_DOCUMENTO[0].valor): DocumentoRequest {
  return { tipo, referenciaArquivo: "", dataEmissao: "", validade: "" };
}

/** Tipos exigidos que ainda não estão entre os anexados (comparação sem diferenciar caixa, como no backend). */
export function tiposFaltantes(exigidos: string[], anexados: { tipo: string }[]): string[] {
  const tiposAnexados = new Set(anexados.map((d) => d.tipo.toUpperCase()));
  return exigidos.filter((tipo) => !tiposAnexados.has(tipo.toUpperCase()));
}

export function listarRotulos(tipos: string[]): string {
  return tipos.map((t) => rotuloTipoDocumento(t).toLowerCase()).join(", ");
}

/** Tira a validade vazia antes de mandar para a API (o backend espera ausente, não ""). */
export function paraEnvio(documento: DocumentoRequest): DocumentoRequest {
  return { ...documento, validade: documento.validade || undefined };
}

export function documentoPreenchido(documento: DocumentoRequest): boolean {
  return documento.referenciaArquivo.trim() !== "" && documento.dataEmissao !== "";
}
