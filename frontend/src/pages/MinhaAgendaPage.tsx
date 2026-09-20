import { useEffect, useState, type KeyboardEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { AgendaEncaminhamentoResponse, SituacaoAtendimento } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { BadgePresenca, BadgeStatus } from "../components/Badge";
import { PainelEncaminhamento } from "../components/DetalhesEncaminhamento";
import { Modal } from "../components/Modal";
import { formatarDataHora, nomeOuId } from "../lib/formatacao";

const ESPERA_DIGITACAO_MS = 300;

const OPCOES_SITUACAO: { valor: SituacaoAtendimento | ""; rotulo: string }[] = [
  { valor: "A_ATENDER", rotulo: "A atender" },
  { valor: "ATENDIDO", rotulo: "Atendidos" },
  { valor: "", rotulo: "Todos" },
];

/** Encaminhamentos agendados nas vagas do especialista logado: os que ele vai atender e os que já atendeu. */
export function MinhaAgendaPage() {
  const [situacao, setSituacao] = useState<SituacaoAtendimento | "">("A_ATENDER");
  const [paciente, setPaciente] = useState("");
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [itens, setItens] = useState<AgendaEncaminhamentoResponse[]>([]);
  const [versao, setVersao] = useState(0);
  const [detalhe, setDetalhe] = useState<AgendaEncaminhamentoResponse | null>(null);

  useEffect(() => {
    let cancelado = false;
    const timer = setTimeout(async () => {
      setCarregando(true);
      setErro(null);
      try {
        const lista = await api.agendaEncaminhamentos({
          situacao: situacao || undefined,
          paciente: paciente.trim() || undefined,
          de: de || undefined,
          ate: ate || undefined,
        });
        if (!cancelado) setItens(lista);
      } catch (e) {
        if (!cancelado) setErro(e instanceof ApiError ? e.message : "Falha ao carregar sua agenda");
      } finally {
        if (!cancelado) setCarregando(false);
      }
    }, ESPERA_DIGITACAO_MS);
    return () => {
      cancelado = true;
      clearTimeout(timer);
    };
  }, [situacao, paciente, de, ate, versao]);

  function limparFiltros() {
    setSituacao("A_ATENDER");
    setPaciente("");
    setDe("");
    setAte("");
  }

  return (
    <div className="card">
      <h2>Meus atendimentos</h2>
      <p className="hint">
        Pacientes agendados nas suas vagas: os que você vai atender e os que já atendeu. Clique numa linha para ver o
        encaminhamento.
      </p>

      <div className="filtro-periodo">
        <label>
          Situação
          <select value={situacao} onChange={(e) => setSituacao(e.target.value as SituacaoAtendimento | "")}>
            {OPCOES_SITUACAO.map((opcao) => (
              <option key={opcao.valor} value={opcao.valor}>
                {opcao.rotulo}
              </option>
            ))}
          </select>
        </label>
        <label>
          Paciente
          <input value={paciente} onChange={(e) => setPaciente(e.target.value)} placeholder="Nome do paciente" />
        </label>
        <label>
          De
          <input type="date" value={de} onChange={(e) => setDe(e.target.value)} />
        </label>
        <label>
          Até
          <input type="date" value={ate} min={de || undefined} onChange={(e) => setAte(e.target.value)} />
        </label>
        <button type="button" onClick={limparFiltros}>
          Limpar filtros
        </button>
      </div>

      {carregando && <Carregando label="Carregando sua agenda..." />}
      {!carregando && erro && <ErroMensagem mensagem={erro} />}
      {!carregando && !erro && itens.length === 0 && <SemDados label="Nenhum encaminhamento com esses filtros." />}
      {!carregando && !erro && itens.length > 0 && (
        <>
          <p className="hint">{itens.length} encaminhamento(s).</p>
          <table className="tabela tabela-compacta">
            <thead>
              <tr>
                <th>Data e hora</th>
                <th>Paciente</th>
                <th>
                  <abbr title="Encaminhamento">Enca.</abbr>
                </th>
                <th>Unidade</th>
                <th>Situação</th>
                <th>Presença</th>
              </tr>
            </thead>
            <tbody>
              {itens.map((item) => (
                <tr
                  key={item.agendamentoId}
                  className="linha-clicavel"
                  title="Ver o encaminhamento"
                  tabIndex={0}
                  onClick={() => setDetalhe(item)}
                  onKeyDown={(e: KeyboardEvent) => {
                    if (e.key === "Enter") setDetalhe(item);
                  }}
                >
                  <td>{formatarDataHora(item.dataHora)}</td>
                  <td>
                    {nomeOuId(item.pacienteNome, item.encaminhamentoId)}
                    {item.urgente && (
                      <span className="hint" title="Marcado como urgente pelo médico da UBS">
                        {" "}
                        ⚠ urgente
                      </span>
                    )}
                  </td>
                  <td>{item.encaminhamentoId}</td>
                  <td>{item.unidadeNome ?? "—"}</td>
                  <td>
                    <BadgeStatus status={item.statusEncaminhamento} />
                  </td>
                  <td>
                    {item.statusEncaminhamento === "AGENDADO" ? <BadgePresenca confirmadaEm={item.presencaConfirmadaEm} /> : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <Modal
        titulo={detalhe ? `Encaminhamento #${detalhe.encaminhamentoId}` : ""}
        aberto={detalhe !== null}
        onFechar={() => setDetalhe(null)}
      >
        {detalhe && (
          <PainelEncaminhamento
            key={detalhe.encaminhamentoId}
            encaminhamentoId={detalhe.encaminhamentoId}
            onAlterado={() => setVersao((v) => v + 1)}
          />
        )}
      </Modal>
    </div>
  );
}
