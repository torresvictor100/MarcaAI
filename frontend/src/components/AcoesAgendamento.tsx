import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import { formatarDataHora, nomeOuId } from "../lib/formatacao";
import type { AgendamentoResponse, VagaHorarioResponse } from "../lib/types";
import { Carregando, ErroMensagem } from "./AsyncState";

type Acao = "cancelar" | "remarcar" | "antecipar";

const TITULOS: Record<Acao, string> = {
  cancelar: "Cancelar agendamento",
  remarcar: "Remarcar para outra vaga",
  antecipar: "Antecipar para uma vaga mais cedo",
};

/**
 * Cancelar, remarcar ou antecipar um agendamento confirmado (secretaria). Motivo sempre obrigatório — fica
 * no histórico do agendamento para auditoria. Remarcar/antecipar só oferecem vagas livres da mesma especialidade.
 */
export function AcoesAgendamento({
  agendamento,
  especialidade,
  onAlterado,
}: {
  agendamento: AgendamentoResponse;
  especialidade: string;
  onAlterado: () => void;
}) {
  const [acao, setAcao] = useState<Acao | null>(null);
  const [motivo, setMotivo] = useState("");
  const [vagaId, setVagaId] = useState("");
  const [vagas, setVagas] = useState<VagaHorarioResponse[] | null>(null);
  const [carregandoVagas, setCarregandoVagas] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  // Antecipar só lista vagas antes da data atual; as vagas já vêm da mais próxima para a mais distante.
  const vagasOferecidas =
    vagas && acao === "antecipar"
      ? vagas.filter((v) => new Date(v.dataHora).getTime() < new Date(agendamento.dataHora).getTime())
      : vagas;

  useEffect(() => {
    if (acao !== "remarcar" && acao !== "antecipar") return;
    let ativo = true;
    api
      .listarVagas(especialidade)
      .then((lista) => ativo && setVagas(lista))
      .catch((e) => ativo && setErro(e instanceof ApiError ? e.message : "Falha ao carregar as vagas livres"))
      .finally(() => ativo && setCarregandoVagas(false));
    return () => {
      ativo = false;
    };
  }, [acao, especialidade]);

  // Sem escolha válida, sugere a primeira vaga oferecida (para antecipar, a mais cedo livre).
  const vagaEscolhida = vagasOferecidas?.some((v) => String(v.id) === vagaId)
    ? vagaId
    : vagasOferecidas?.[0]
      ? String(vagasOferecidas[0].id)
      : "";

  function abrir(nova: Acao) {
    setAcao(nova);
    setMotivo("");
    setVagaId("");
    setVagas(null);
    setErro(null);
    setCarregandoVagas(nova !== "cancelar");
  }

  async function confirmar(event: FormEvent) {
    event.preventDefault();
    if (!acao) return;
    setEnviando(true);
    setErro(null);
    try {
      if (acao === "cancelar") {
        await api.cancelarAgendamento(agendamento.id, motivo);
      } else if (acao === "remarcar") {
        await api.remarcarAgendamento(agendamento.id, Number(vagaEscolhida), motivo);
      } else {
        await api.anteciparAgendamento(agendamento.id, Number(vagaEscolhida), motivo);
      }
      setAcao(null);
      onAlterado();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao alterar o agendamento");
    } finally {
      setEnviando(false);
    }
  }

  if (!acao) {
    return (
      <div className="acoes-agendamento">
        <button type="button" className="botao-secundario" onClick={() => abrir("remarcar")}>
          Remarcar
        </button>
        <button type="button" className="botao-secundario" onClick={() => abrir("antecipar")}>
          Antecipar
        </button>
        <button type="button" className="botao-perigo" onClick={() => abrir("cancelar")}>
          Cancelar
        </button>
      </div>
    );
  }

  const precisaDeVaga = acao !== "cancelar";
  return (
    <form onSubmit={confirmar} className="form-acao-agendamento">
      <strong>{TITULOS[acao]}</strong>
      {acao === "cancelar" && (
        <p className="hint">A vaga fica livre e o paciente volta para a fila, aguardando novo agendamento.</p>
      )}
      {precisaDeVaga && carregandoVagas && <Carregando label="Carregando vagas livres..." />}
      {precisaDeVaga && vagasOferecidas && vagasOferecidas.length === 0 && (
        <p className="estado estado-vazio">
          {acao === "antecipar"
            ? "Não há vaga livre antes da data atual deste agendamento."
            : `Não há vaga livre de ${especialidade}.`}
        </p>
      )}
      {precisaDeVaga && vagasOferecidas && vagasOferecidas.length > 0 && (
        <label>
          Nova vaga
          <select value={vagaEscolhida} onChange={(e) => setVagaId(e.target.value)} required>
            {vagasOferecidas.map((v) => (
              <option key={v.id} value={v.id}>
                {formatarDataHora(v.dataHora)} — {nomeOuId(v.profissionalNome, v.profissionalId)} (vaga #{v.id})
              </option>
            ))}
          </select>
        </label>
      )}
      <label>
        Motivo (fica registrado)
        <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} required />
      </label>
      {erro && <ErroMensagem mensagem={erro} />}
      <div className="acoes-agendamento">
        <button
          type="submit"
          className={acao === "cancelar" ? "botao-perigo" : undefined}
          disabled={enviando || !motivo.trim() || (precisaDeVaga && !vagaEscolhida)}
        >
          {enviando ? "Salvando..." : acao === "cancelar" ? "Confirmar cancelamento" : "Confirmar nova data"}
        </button>
        <button type="button" className="botao-secundario" onClick={() => setAcao(null)} disabled={enviando}>
          Voltar
        </button>
      </div>
    </form>
  );
}

/** Marca o primeiro agendamento de um encaminhamento que está na fila: lista as vagas livres da especialidade. */
export function MarcarAgendamento({
  encaminhamentoId,
  especialidade,
  onMarcado,
}: {
  encaminhamentoId: number;
  especialidade: string;
  onMarcado: () => void;
}) {
  const [aberto, setAberto] = useState(false);
  const [vagas, setVagas] = useState<VagaHorarioResponse[] | null>(null);
  const [vagaId, setVagaId] = useState<number | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!aberto) return;
    let ativo = true;
    api
      .listarVagas(especialidade)
      .then((lista) => ativo && setVagas(lista))
      .catch((e) => ativo && setErro(e instanceof ApiError ? e.message : "Falha ao carregar as vagas livres"));
    return () => {
      ativo = false;
    };
  }, [aberto, especialidade]);

  function abrir() {
    setAberto(true);
    setVagas(null);
    setVagaId(null);
    setErro(null);
  }

  async function confirmar() {
    if (vagaId === null) return;
    setEnviando(true);
    setErro(null);
    try {
      await api.criarAgendamento({ encaminhamentoId, vagaId });
      setAberto(false);
      onMarcado();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao marcar o agendamento");
    } finally {
      setEnviando(false);
    }
  }

  if (!aberto) {
    return (
      <div className="acoes-agendamento">
        <button type="button" onClick={abrir}>
          Marcar
        </button>
      </div>
    );
  }

  return (
    <div className="form-acao-agendamento">
      <strong>Escolha a vaga de {especialidade}</strong>
      {!vagas && !erro && <Carregando label="Carregando vagas livres..." />}
      {vagas && vagas.length === 0 && <p className="estado estado-vazio">Não há vaga livre de {especialidade}.</p>}
      {vagas && vagas.length > 0 && (
        <div className="tabela-rolagem">
          <table className="tabela tabela-compacta">
            <thead>
              <tr>
                <th>Data/hora</th>
                <th>Profissional</th>
                <th>Unidade</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {vagas.map((v) => (
                <tr
                  key={v.id}
                  className={v.id === vagaId ? "linha-clicavel linha-selecionada" : "linha-clicavel"}
                  title="Escolher esta vaga"
                  tabIndex={0}
                  onClick={() => setVagaId(v.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") setVagaId(v.id);
                  }}
                >
                  <td>{formatarDataHora(v.dataHora)}</td>
                  <td>{nomeOuId(v.profissionalNome, v.profissionalId)}</td>
                  <td>{nomeOuId(v.unidadeNome, v.unidadeId)}</td>
                  <td>
                    <span className="caixa-selecao" aria-hidden="true" data-marcada={v.id === vagaId}>
                      {v.id === vagaId ? "✓" : ""}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {erro && <ErroMensagem mensagem={erro} />}
      <div className="acoes-agendamento">
        <button type="button" onClick={confirmar} disabled={enviando || vagaId === null}>
          {enviando ? "Marcando..." : "Confirmar agendamento"}
        </button>
        <button type="button" className="botao-secundario" onClick={() => setAberto(false)} disabled={enviando}>
          Voltar
        </button>
      </div>
    </div>
  );
}
