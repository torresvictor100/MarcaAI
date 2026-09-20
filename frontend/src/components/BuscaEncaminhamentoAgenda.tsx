import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import type { AgendaEncaminhamentoResponse, SituacaoAtendimento } from "../lib/types";
import { formatarDataHora, nomeOuId } from "../lib/formatacao";

const ESPERA_DIGITACAO_MS = 300;

interface Props {
  /** Só os agendados (a atender) ou só os já atendidos. */
  situacao: SituacaoAtendimento;
  selecionado: AgendaEncaminhamentoResponse | null;
  onSelecionar: (item: AgendaEncaminhamentoResponse | null) => void;
  /** Mude o valor para recarregar a lista (ex.: depois de registrar um resultado). */
  versao?: number;
}

/**
 * Busca, pelo nome do paciente, entre os encaminhamentos da agenda do especialista logado
 * (GET /agenda/encaminhamentos) — ele só enxerga o que está agendado nas vagas dele.
 */
export function BuscaEncaminhamentoAgenda({ situacao, selecionado, onSelecionar, versao = 0 }: Props) {
  const [termo, setTermo] = useState("");
  const [aberto, setAberto] = useState(false);
  const [resultados, setResultados] = useState<AgendaEncaminhamentoResponse[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (selecionado || !aberto) return;
    let cancelado = false;
    const timer = setTimeout(async () => {
      setBuscando(true);
      setErro(null);
      try {
        const encontrados = await api.agendaEncaminhamentos({ situacao, paciente: termo.trim() || undefined });
        if (!cancelado) setResultados(encontrados);
      } catch (e) {
        if (!cancelado) setErro(e instanceof ApiError ? e.message : "Falha ao buscar encaminhamentos");
      } finally {
        if (!cancelado) setBuscando(false);
      }
    }, ESPERA_DIGITACAO_MS);
    return () => {
      cancelado = true;
      clearTimeout(timer);
    };
  }, [termo, situacao, selecionado, aberto, versao]);

  if (selecionado) {
    return (
      <div className="paciente-selecionado">
        <span>
          <strong>{nomeOuId(selecionado.pacienteNome, selecionado.encaminhamentoId)}</strong>{" "}
          <span className="hint">
            Enca. #{selecionado.encaminhamentoId} · {formatarDataHora(selecionado.dataHora)}
          </span>
        </span>
        <button type="button" className="botao-link" onClick={() => onSelecionar(null)}>
          trocar
        </button>
      </div>
    );
  }

  return (
    <div className="busca-paciente">
      <input
        value={termo}
        onChange={(e) => setTermo(e.target.value)}
        onFocus={() => setAberto(true)}
        // Adia o fechamento para o clique numa sugestão ainda chegar ao botão.
        onBlur={() => setTimeout(() => setAberto(false), 150)}
        placeholder="Digite o nome do paciente"
        autoComplete="off"
      />
      {aberto && (
        <ul className="resultados-busca" role="listbox">
          {buscando && <li className="hint">Buscando...</li>}
          {!buscando && erro && <li className="erro-busca">{erro}</li>}
          {!buscando && !erro && resultados.length === 0 && (
            <li className="hint">
              {situacao === "A_ATENDER"
                ? "Nenhum paciente agendado com você encontrado"
                : "Nenhum paciente atendido por você encontrado"}
            </li>
          )}
          {!buscando &&
            !erro &&
            resultados.map((item) => (
              <li key={item.encaminhamentoId} role="option" aria-selected={false}>
                <button
                  type="button"
                  onClick={() => {
                    onSelecionar(item);
                    setTermo("");
                    setAberto(false);
                  }}
                >
                  <span>{nomeOuId(item.pacienteNome, item.encaminhamentoId)}</span>
                  <span className="hint">
                    #{item.encaminhamentoId} · {formatarDataHora(item.dataHora)}
                  </span>
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
