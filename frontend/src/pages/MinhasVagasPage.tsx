import { useEffect, useState, type KeyboardEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { AgendaVagaResponse, StatusVaga } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { BadgePresenca, BadgeStatus } from "../components/Badge";
import { PainelEncaminhamento } from "../components/DetalhesEncaminhamento";
import { Modal } from "../components/Modal";
import { formatarDataHora, hojeLocal, nomeOuId } from "../lib/formatacao";

const OPCOES_STATUS: { valor: StatusVaga | ""; rotulo: string }[] = [
  { valor: "", rotulo: "Todas" },
  { valor: "DISPONIVEL", rotulo: "Livres" },
  { valor: "OCUPADA", rotulo: "Com paciente" },
];

/** Todas as vagas do especialista logado, livres e com paciente. */
export function MinhasVagasPage() {
  const [status, setStatus] = useState<StatusVaga | "">("");
  const [de, setDe] = useState(hojeLocal());
  const [ate, setAte] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [vagas, setVagas] = useState<AgendaVagaResponse[]>([]);
  const [detalhe, setDetalhe] = useState<AgendaVagaResponse | null>(null);

  useEffect(() => {
    let cancelado = false;
    const timer = setTimeout(async () => {
      setCarregando(true);
      setErro(null);
      try {
        const lista = await api.agendaVagas({ status: status || undefined, de: de || undefined, ate: ate || undefined });
        if (!cancelado) setVagas(lista);
      } catch (e) {
        if (!cancelado) setErro(e instanceof ApiError ? e.message : "Falha ao carregar suas vagas");
      } finally {
        if (!cancelado) setCarregando(false);
      }
    }, 0);
    return () => {
      cancelado = true;
      clearTimeout(timer);
    };
  }, [status, de, ate]);

  const livres = vagas.filter((v) => v.status === "DISPONIVEL").length;
  const comPaciente = vagas.filter((v) => v.encaminhamentoId !== null).length;

  function abrir(vaga: AgendaVagaResponse) {
    if (vaga.encaminhamentoId !== null) setDetalhe(vaga);
  }

  return (
    <div className="card">
      <h2>Minhas vagas</h2>
      <p className="hint">
        Suas vagas de atendimento. Clique numa vaga com paciente para ver o encaminhamento. Quem abre e agenda vagas é a
        secretaria.
      </p>

      <div className="filtro-periodo">
        <label>
          Mostrar
          <select value={status} onChange={(e) => setStatus(e.target.value as StatusVaga | "")}>
            {OPCOES_STATUS.map((opcao) => (
              <option key={opcao.valor} value={opcao.valor}>
                {opcao.rotulo}
              </option>
            ))}
          </select>
        </label>
        <label>
          De (vazio = desde o início)
          <input type="date" value={de} onChange={(e) => setDe(e.target.value)} />
        </label>
        <label>
          Até (opcional)
          <input type="date" value={ate} min={de || undefined} onChange={(e) => setAte(e.target.value)} />
        </label>
      </div>

      {carregando && <Carregando label="Carregando suas vagas..." />}
      {!carregando && erro && <ErroMensagem mensagem={erro} />}
      {!carregando && !erro && vagas.length === 0 && <SemDados label="Nenhuma vaga com esses filtros." />}
      {!carregando && !erro && vagas.length > 0 && (
        <>
          <p className="hint">
            {vagas.length} vaga(s): {livres} livre(s), {comPaciente} com paciente.
          </p>
          <table className="tabela tabela-compacta">
            <thead>
              <tr>
                <th>Data e hora</th>
                <th>Unidade</th>
                <th>Vaga</th>
                <th>Paciente</th>
                <th>
                  <abbr title="Encaminhamento">Enca.</abbr>
                </th>
                <th>Situação</th>
                <th>Presença</th>
              </tr>
            </thead>
            <tbody>
              {vagas.map((vaga) => {
                const clicavel = vaga.encaminhamentoId !== null;
                return (
                  <tr
                    key={vaga.vagaId}
                    {...(clicavel && {
                      className: "linha-clicavel",
                      title: "Ver o encaminhamento",
                      tabIndex: 0,
                      onClick: () => abrir(vaga),
                      onKeyDown: (e: KeyboardEvent) => {
                        if (e.key === "Enter") abrir(vaga);
                      },
                    })}
                  >
                    <td>{formatarDataHora(vaga.dataHora)}</td>
                    <td>{vaga.unidadeNome ?? "—"}</td>
                    <td>{vaga.status === "DISPONIVEL" ? "Livre" : "Com paciente"}</td>
                    <td>{vaga.encaminhamentoId !== null ? nomeOuId(vaga.pacienteNome, vaga.encaminhamentoId) : "—"}</td>
                    <td>{vaga.encaminhamentoId ?? "—"}</td>
                    <td>{vaga.statusEncaminhamento ? <BadgeStatus status={vaga.statusEncaminhamento} /> : "—"}</td>
                    <td>
                      {vaga.statusEncaminhamento === "AGENDADO" ? (
                        <BadgePresenca confirmadaEm={vaga.presencaConfirmadaEm} />
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </>
      )}

      <Modal
        titulo={detalhe ? `Encaminhamento #${detalhe.encaminhamentoId}` : ""}
        aberto={detalhe !== null}
        onFechar={() => setDetalhe(null)}
      >
        {detalhe?.encaminhamentoId != null && (
          <PainelEncaminhamento key={detalhe.encaminhamentoId} encaminhamentoId={detalhe.encaminhamentoId} />
        )}
      </Modal>
    </div>
  );
}
