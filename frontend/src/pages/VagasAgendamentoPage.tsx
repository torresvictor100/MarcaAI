import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import type {
  AgendamentoResponse,
  EncaminhamentoResponse,
  PacienteResumoResponse,
  VagaHorarioResponse,
  VagaMarcadaResponse,
} from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { BadgeStatus, BadgePresenca } from "../components/Badge";
import { BuscaPaciente } from "../components/BuscaPaciente";
import { Detalhes } from "../components/Detalhes";
import { Modal } from "../components/Modal";
import { CadastroVagas } from "../components/CadastroVagas";
import { EncaminhamentoNavegavel } from "../components/DetalhesEncaminhamento";
import {
  formatarData,
  formatarDataHora,
  hojeLocal,
  nomeOuId,
  STATUS_AGENDAMENTO_LABELS,
  STATUS_VAGA_LABELS,
  TIPO_ENCAMINHAMENTO_LABELS,
} from "../lib/formatacao";

export function VagasAgendamentoPage() {
  const [especialidades, setEspecialidades] = useState<string[]>([]);
  const [especialidade, setEspecialidade] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [vagas, setVagas] = useState<VagaHorarioResponse[] | null>(null);

  const [paciente, setPaciente] = useState<PacienteResumoResponse | null>(null);
  const [encaminhamentos, setEncaminhamentos] = useState<EncaminhamentoResponse[] | null>(null);
  const [carregandoEncaminhamentos, setCarregandoEncaminhamentos] = useState(false);
  const [erroEncaminhamentos, setErroEncaminhamentos] = useState<string | null>(null);
  const [encaminhamentoId, setEncaminhamentoId] = useState("");
  const [vagaId, setVagaId] = useState("");
  const [agendando, setAgendando] = useState(false);
  const [erroAgendar, setErroAgendar] = useState<string | null>(null);
  const [agendamento, setAgendamento] = useState<AgendamentoResponse | null>(null);

  const [marcadasAberto, setMarcadasAberto] = useState(false);
  const [cadastroAberto, setCadastroAberto] = useState(false);
  const [vagaDetalhe, setVagaDetalhe] = useState<VagaHorarioResponse | null>(null);

  const cardAgendamentoRef = useRef<HTMLDivElement>(null);

  // Preenche o "Confirmar agendamento" com a vaga escolhida na tabela; o usuário só escolhe o encaminhamento.
  function usarVaga(vaga: VagaHorarioResponse) {
    setVagaId(String(vaga.id));
    setErroAgendar(null);
    setAgendamento(null);
    // Com as colunas lado a lado o card já está visível (fica fixo na rolagem) e isto não move a tela;
    // em tela estreita, onde os cards ficam empilhados, rola até o formulário.
    cardAgendamentoRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    cardAgendamentoRef.current?.querySelector<HTMLInputElement>(".busca-paciente input")?.focus({ preventScroll: true });
  }

  // Ao escolher o paciente, lista os encaminhamentos dele para a secretaria clicar no que vai agendar.
  async function escolherPaciente(escolhido: PacienteResumoResponse | null) {
    setPaciente(escolhido);
    setEncaminhamentos(null);
    setEncaminhamentoId("");
    setErroEncaminhamentos(null);
    setErroAgendar(null);
    setAgendamento(null);
    if (!escolhido) return;
    setCarregandoEncaminhamentos(true);
    try {
      const lista = [...(await api.meusEncaminhamentos(escolhido.id))].sort((a, b) => b.id - a.id);
      setEncaminhamentos(lista);
      // Se só um encaminhamento é da especialidade que está sendo agendada, ele já vem escolhido.
      const daEspecialidade = lista.filter((enc) => enc.especialidadeOuExame === especialidade);
      if (daEspecialidade.length === 1) setEncaminhamentoId(String(daEspecialidade[0].id));
    } catch (e) {
      setErroEncaminhamentos(e instanceof ApiError ? e.message : "Falha ao carregar os encaminhamentos do paciente");
    } finally {
      setCarregandoEncaminhamentos(false);
    }
  }

  function escolherEncaminhamento(enc: EncaminhamentoResponse) {
    setEncaminhamentoId(String(enc.id));
    setErroAgendar(null);
    setAgendamento(null);
  }

  useEffect(() => {
    api
      .listarEspecialidades()
      .then((lista) => {
        setEspecialidades(lista);
        setEspecialidade((atual) => atual || lista[0] || "");
      })
      .catch(() => {
        // Lista de apoio pro seletor — se falhar, o campo continua utilizável, só sem sugestões.
      });
  }, []);

  async function buscarVagas(event?: FormEvent) {
    event?.preventDefault();
    if (!especialidade) return;
    setCarregando(true);
    setErro(null);
    setVagas(null);
    try {
      setVagas(await api.listarVagas(especialidade));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao buscar vagas");
    } finally {
      setCarregando(false);
    }
  }

  useEffect(() => {
    if (especialidade) buscarVagas();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [especialidade]);

  async function agendar(event: FormEvent) {
    event.preventDefault();
    setAgendando(true);
    setErroAgendar(null);
    setAgendamento(null);
    try {
      setAgendamento(
        await api.criarAgendamento({ encaminhamentoId: Number(encaminhamentoId), vagaId: Number(vagaId) }),
      );
    } catch (e) {
      setErroAgendar(e instanceof ApiError ? e.message : "Falha ao agendar");
    } finally {
      setAgendando(false);
    }
  }

  return (
    <div className="grid-2">
      <div className="card">
        <div className="topo-vagas">
          <h2>Vagas disponíveis</h2>
          <div className="acoes-vagas">
            <button type="button" onClick={() => setMarcadasAberto(true)} disabled={!especialidade}>
              Ver vagas marcadas
            </button>
            <button type="button" className="botao-secundario" onClick={() => setCadastroAberto(true)}>
              Cadastrar vagas
            </button>
          </div>
        </div>
        <form onSubmit={buscarVagas}>
          <label>
            Especialidade/exame
            {especialidades.length > 0 ? (
              <select value={especialidade} onChange={(e) => setEspecialidade(e.target.value)}>
                {especialidades.map((esp) => (
                  <option key={esp} value={esp}>
                    {esp}
                  </option>
                ))}
              </select>
            ) : (
              <input value={especialidade} onChange={(e) => setEspecialidade(e.target.value)} required />
            )}
          </label>
          <button type="submit" disabled={carregando}>
            Buscar
          </button>
        </form>
        {carregando && <Carregando />}
        {erro && <ErroMensagem mensagem={erro} />}
        {vagas &&
          (vagas.length > 0 ? (
            <div className="tabela-rolagem">
              <table className="tabela tabela-compacta">
                <thead>
                  <tr>
                    <th>Vaga</th>
                    <th>Data/hora</th>
                    <th>Profissional</th>
                    <th>Unidade</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {vagas.map((v) => (
                    <tr
                      key={v.id}
                      className={String(v.id) === vagaId ? "linha-clicavel linha-selecionada" : "linha-clicavel"}
                      title="Ver dados da vaga"
                      tabIndex={0}
                      onClick={() => setVagaDetalhe(v)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") setVagaDetalhe(v);
                      }}
                    >
                      <td>#{v.id}</td>
                      <td>{formatarDataHora(v.dataHora)}</td>
                      <td>{nomeOuId(v.profissionalNome, v.profissionalId)}</td>
                      <td>{nomeOuId(v.unidadeNome, v.unidadeId)}</td>
                      <td>{STATUS_VAGA_LABELS[v.status]}</td>
                      <td>
                        {v.status === "DISPONIVEL" && (
                          <button
                            type="button"
                            className="caixa-selecao"
                            title="Selecionar"
                            aria-label={`Selecionar vaga ${v.id}`}
                            aria-pressed={String(v.id) === vagaId}
                            onClick={(e) => {
                              // A caixinha só preenche o agendamento — não abre os dados da vaga.
                              e.stopPropagation();
                              usarVaga(v);
                            }}
                            onKeyDown={(e) => e.stopPropagation()}
                          >
                            {String(v.id) === vagaId ? "✓" : ""}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <SemDados />
          ))}
      </div>

      <div className="card card-fixo" ref={cardAgendamentoRef}>
        <h2>Confirmar agendamento</h2>
        <form onSubmit={agendar}>
          <div className="campo">
            <span className="campo-rotulo">Paciente</span>
            <BuscaPaciente selecionado={paciente} onSelecionar={escolherPaciente} />
          </div>
          {carregandoEncaminhamentos && <Carregando label="Carregando encaminhamentos..." />}
          {erroEncaminhamentos && <ErroMensagem mensagem={erroEncaminhamentos} />}
          {encaminhamentos &&
            (encaminhamentos.length === 0 ? (
              <SemDados label="Nenhum encaminhamento para este paciente." />
            ) : (
              <div className="campo">
                <span className="campo-rotulo">Encaminhamento</span>
                <div className="tabela-rolagem">
                  <table className="tabela tabela-compacta">
                    <thead>
                      <tr>
                        <th>
                          <abbr title="Encaminhamento">Enca.</abbr>
                        </th>
                        <th>Especialidade/exame</th>
                        <th>Tipo</th>
                        <th>Situação</th>
                      </tr>
                    </thead>
                    <tbody>
                      {encaminhamentos.map((enc) => (
                        <tr
                          key={enc.id}
                          className={
                            String(enc.id) === encaminhamentoId ? "linha-clicavel linha-selecionada" : "linha-clicavel"
                          }
                          title="Usar este encaminhamento no agendamento"
                          tabIndex={0}
                          aria-selected={String(enc.id) === encaminhamentoId}
                          onClick={() => escolherEncaminhamento(enc)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              // Enter escolhe a linha, não envia o formulário.
                              e.preventDefault();
                              escolherEncaminhamento(enc);
                            }
                          }}
                        >
                          <td>#{enc.id}</td>
                          <td>{enc.especialidadeOuExame}</td>
                          <td>{TIPO_ENCAMINHAMENTO_LABELS[enc.tipo]}</td>
                          <td>
                            <BadgeStatus status={enc.status} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ))}
          <label>
            Vaga ID
            <input value={vagaId} onChange={(e) => setVagaId(e.target.value)} required />
          </label>
          <button type="submit" disabled={agendando || !encaminhamentoId}>
            {agendando ? "Agendando..." : "Agendar"}
          </button>
        </form>
        {erroAgendar && <ErroMensagem mensagem={erroAgendar} />}
        {agendamento && (
          <Detalhes
            titulo="Agendamento confirmado"
            itens={[
              { rotulo: "Encaminhamento", valor: `#${agendamento.encaminhamentoId}` },
              { rotulo: "Vaga", valor: `#${agendamento.vagaId}` },
              { rotulo: "Data e hora", valor: formatarDataHora(agendamento.dataHora) },
              { rotulo: "Situação", valor: STATUS_AGENDAMENTO_LABELS[agendamento.status] },
              { rotulo: "Agendado por", valor: agendamento.agendadoPor },
            ]}
          />
        )}
      </div>

      <Modal titulo="Cadastrar vagas" aberto={cadastroAberto} onFechar={() => setCadastroAberto(false)}>
        {cadastroAberto && (
          <CadastroVagas
            especialidades={especialidades}
            especialidadeInicial={especialidade}
            onCriadas={() => buscarVagas()}
          />
        )}
      </Modal>

      <Modal
        titulo={vagaDetalhe ? `Vaga #${vagaDetalhe.id}` : ""}
        aberto={vagaDetalhe !== null}
        onFechar={() => setVagaDetalhe(null)}
      >
        {vagaDetalhe && (
          <>
            <Detalhes
              titulo={STATUS_VAGA_LABELS[vagaDetalhe.status]}
              itens={[
                { rotulo: "Data e hora", valor: formatarDataHora(vagaDetalhe.dataHora) },
                { rotulo: "Especialidade/exame", valor: vagaDetalhe.especialidadeOuExame },
                { rotulo: "Profissional", valor: nomeOuId(vagaDetalhe.profissionalNome, vagaDetalhe.profissionalId) },
                { rotulo: "Unidade", valor: nomeOuId(vagaDetalhe.unidadeNome, vagaDetalhe.unidadeId) },
              ]}
            />
            {vagaDetalhe.status === "DISPONIVEL" && (
              <button
                type="button"
                className="modal-acao"
                onClick={() => {
                  usarVaga(vagaDetalhe);
                  setVagaDetalhe(null);
                }}
              >
                Usar esta vaga no agendamento
              </button>
            )}
          </>
        )}
      </Modal>

      <Modal
        titulo={`Vagas marcadas — ${especialidade}`}
        aberto={marcadasAberto}
        onFechar={() => setMarcadasAberto(false)}
      >
        <VagasMarcadas especialidade={especialidade} onAlterado={() => buscarVagas()} />
      </Modal>
    </div>
  );
}

/** Vagas já ocupadas de uma especialidade: por padrão de hoje em diante, com filtro opcional de período. */
function VagasMarcadas({ especialidade, onAlterado }: { especialidade: string; onAlterado: () => void }) {
  const [de, setDe] = useState(hojeLocal);
  const [ate, setAte] = useState("");
  const [periodo, setPeriodo] = useState<{ de: string; ate: string }>(() => ({ de: hojeLocal(), ate: "" }));
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [marcadas, setMarcadas] = useState<VagaMarcadaResponse[] | null>(null);
  const [selecionada, setSelecionada] = useState<VagaMarcadaResponse | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .listarVagasMarcadas(especialidade, periodo.de, periodo.ate || undefined)
      .then((lista) => {
        if (ativo) {
          setMarcadas(lista);
          setErro(null);
        }
      })
      .catch((e) => {
        if (ativo) {
          setMarcadas(null);
          setErro(e instanceof ApiError ? e.message : "Falha ao buscar as vagas marcadas");
        }
      })
      .finally(() => {
        if (ativo) setCarregando(false);
      });
    return () => {
      ativo = false;
    };
  }, [especialidade, periodo]);

  function filtrar(event: FormEvent) {
    event.preventDefault();
    setCarregando(true);
    setPeriodo({ de, ate });
  }

  function hojeEmDiante() {
    const hoje = hojeLocal();
    setDe(hoje);
    setAte("");
    setCarregando(true);
    setPeriodo({ de: hoje, ate: "" });
  }

  if (selecionada) {
    return (
      <DetalheVagaMarcada
        vaga={selecionada}
        onVoltar={() => setSelecionada(null)}
        onAlterado={() => {
          // Depois de cancelar/remarcar/antecipar, os dados desta vaga ficaram velhos: volta à lista recarregada.
          setSelecionada(null);
          setCarregando(true);
          setPeriodo((atual) => ({ ...atual }));
          onAlterado();
        }}
      />
    );
  }

  return (
    <>
      <form onSubmit={filtrar} className="filtro-periodo">
        <label>
          De
          <input type="date" value={de} onChange={(e) => setDe(e.target.value)} required />
        </label>
        <label>
          Até (opcional)
          <input type="date" value={ate} min={de} onChange={(e) => setAte(e.target.value)} />
        </label>
        <button type="submit" disabled={carregando}>
          Filtrar
        </button>
        <button type="button" onClick={hojeEmDiante} disabled={carregando}>
          Hoje em diante
        </button>
      </form>
      <p className="hint">
        {periodo.ate
          ? `De ${formatarData(periodo.de)} até ${formatarData(periodo.ate)}.`
          : `De ${formatarData(periodo.de)} em diante.`}
      </p>
      {carregando && <Carregando />}
      {erro && <ErroMensagem mensagem={erro} />}
      {!carregando &&
        marcadas &&
        (marcadas.length > 0 ? (
          <div className="tabela-rolagem">
            <table className="tabela tabela-compacta">
              <thead>
                <tr>
                  <th>Data/hora</th>
                  <th>Paciente</th>
                  <th>
                    <abbr title="Encaminhamento">Enca.</abbr>
                  </th>
                  <th>Profissional</th>
                  <th>Situação</th>
                  <th>Presença</th>
                  <th>Agendado por</th>
                </tr>
              </thead>
              <tbody>
                {marcadas.map((m) => (
                  <tr
                    key={m.vagaId}
                    className="linha-clicavel"
                    title="Ver dados da vaga"
                    tabIndex={0}
                    onClick={() => setSelecionada(m)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") setSelecionada(m);
                    }}
                  >
                    <td>{formatarDataHora(m.dataHora)}</td>
                    <td>{m.pacienteNome ?? "—"}</td>
                    <td>{m.encaminhamentoId ? `#${m.encaminhamentoId}` : "—"}</td>
                    <td>{m.profissionalNome ?? "—"}</td>
                    <td>{m.statusAgendamento ? STATUS_AGENDAMENTO_LABELS[m.statusAgendamento] : "—"}</td>
                    <td>{m.statusAgendamento === "CONFIRMADO" ? <BadgePresenca confirmadaEm={m.presencaConfirmadaEm} /> : "—"}</td>
                    <td>{m.agendadoPor ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SemDados label="Nenhuma vaga marcada neste período." />
        ))}
    </>
  );
}

/** Dados de uma vaga marcada e do encaminhamento que está nela; o filtro da lista fica guardado ao voltar. */
function DetalheVagaMarcada({
  vaga,
  onVoltar,
  onAlterado,
}: {
  vaga: VagaMarcadaResponse;
  onVoltar: () => void;
  onAlterado: () => void;
}) {
  return (
    <>
      <button type="button" className="botao-voltar" onClick={onVoltar}>
        ← Voltar à lista
      </button>
      <Detalhes
        titulo={`Vaga #${vaga.vagaId}`}
        itens={[
          { rotulo: "Data e hora", valor: formatarDataHora(vaga.dataHora) },
          { rotulo: "Especialidade/exame", valor: vaga.especialidadeOuExame },
          { rotulo: "Profissional", valor: vaga.profissionalNome ?? "—" },
          { rotulo: "Unidade", valor: vaga.unidadeNome ?? "—" },
          { rotulo: "Paciente", valor: vaga.pacienteNome ?? "—" },
          {
            rotulo: "Situação",
            valor: vaga.statusAgendamento ? STATUS_AGENDAMENTO_LABELS[vaga.statusAgendamento] : "—",
          },
          { rotulo: "Agendado por", valor: vaga.agendadoPor ?? "—" },
          {
            rotulo: "Presença do paciente",
            valor: vaga.presencaConfirmadaEm ? `Confirmada em ${formatarDataHora(vaga.presencaConfirmadaEm)}` : "Ainda não confirmada",
          },
        ]}
      />
      {vaga.encaminhamentoId ? (
        <>
          <h3>Encaminhamento #{vaga.encaminhamentoId}</h3>
          <EncaminhamentoNavegavel
            key={vaga.encaminhamentoId}
            encaminhamentoId={vaga.encaminhamentoId}
            onAlterado={onAlterado}
          />
        </>
      ) : (
        <SemDados label="Sem agendamento vinculado a esta vaga." />
      )}
    </>
  );
}
