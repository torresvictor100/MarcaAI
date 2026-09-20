import { useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { DocumentoRequest } from "../lib/types";
import { rotuloTipoDocumento, TIPOS_DOCUMENTO } from "../lib/formatacao";
import { documentoVazio, paraEnvio } from "../lib/documentos";
import { ErroMensagem } from "./AsyncState";

/** Campos de um documento (tipo, referência, emissão e validade), usados ao criar o encaminhamento e ao anexar depois. */
export function CamposDocumento({
  documento,
  onAlterar,
}: {
  documento: DocumentoRequest;
  onAlterar: (documento: DocumentoRequest) => void;
}) {
  return (
    <>
      <label>
        Tipo do documento
        <select value={documento.tipo} onChange={(e) => onAlterar({ ...documento, tipo: e.target.value })}>
          {TIPOS_DOCUMENTO.map((t) => (
            <option key={t.valor} value={t.valor}>
              {t.rotulo}
            </option>
          ))}
        </select>
      </label>
      <label>
        Referência do arquivo
        <input
          value={documento.referenciaArquivo}
          onChange={(e) => onAlterar({ ...documento, referenciaArquivo: e.target.value })}
          placeholder="ex.: guia-encaminhamento.pdf"
          required
        />
      </label>
      <div className="linha-datas">
        <label>
          Data de emissão
          <input
            type="date"
            value={documento.dataEmissao}
            onChange={(e) => onAlterar({ ...documento, dataEmissao: e.target.value })}
            required
          />
        </label>
        <label>
          Validade (opcional)
          <input
            type="date"
            value={documento.validade ?? ""}
            onChange={(e) => onAlterar({ ...documento, validade: e.target.value })}
          />
        </label>
      </div>
    </>
  );
}

/** Anexa mais um documento a um encaminhamento que já existe (usado no detalhe do encaminhamento). */
export function AnexarDocumento({
  encaminhamentoId,
  tipoSugerido,
  onAnexado,
}: {
  encaminhamentoId: number;
  /** Tipo que já vem selecionado ao abrir (o primeiro exigido que falta). */
  tipoSugerido?: string;
  onAnexado: () => void;
}) {
  const [aberto, setAberto] = useState(false);
  const [documento, setDocumento] = useState<DocumentoRequest>(() => documentoVazio(tipoSugerido));
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [anexado, setAnexado] = useState<string | null>(null);

  async function enviar(event: FormEvent) {
    event.preventDefault();
    setEnviando(true);
    setErro(null);
    try {
      const criado = await api.adicionarDocumento(encaminhamentoId, paraEnvio(documento));
      setAnexado(rotuloTipoDocumento(criado.tipo));
      setAberto(false);
      onAnexado();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao anexar documento");
    } finally {
      setEnviando(false);
    }
  }

  if (!aberto) {
    return (
      <>
        {anexado && <p className="destaque destaque-agendamento">{anexado} anexado.</p>}
        <button
          type="button"
          className="botao-secundario"
          onClick={() => {
            setDocumento(documentoVazio(tipoSugerido));
            setAberto(true);
          }}
        >
          + Anexar documento
        </button>
      </>
    );
  }

  return (
    <form onSubmit={enviar} className="bloco-documento">
      <CamposDocumento documento={documento} onAlterar={setDocumento} />
      <div className="acoes-agendamento acoes-topo">
        <button type="submit" disabled={enviando}>
          {enviando ? "Anexando..." : "Anexar"}
        </button>
        <button type="button" className="botao-secundario" onClick={() => setAberto(false)} disabled={enviando}>
          Cancelar
        </button>
      </div>
      {erro && <ErroMensagem mensagem={erro} />}
    </form>
  );
}
