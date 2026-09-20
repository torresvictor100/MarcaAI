import { useEffect, useState, type ReactNode } from "react";
import { api, ApiError } from "../lib/api";
import {
  formatarData,
  formatarDataHora,
  nomeOuId,
  rotuloTipoDocumento,
  STATUS_AGENDAMENTO_LABELS,
  TIPO_ENCAMINHAMENTO_LABELS,
} from "../lib/formatacao";

const ROTULOS_ACAO_AGENDAMENTO = {
  CANCELADO: "Cancelado",
  REMARCADO: "Remarcado",
  ANTECIPADO: "Antecipado",
} as const;
import type {
  AgendamentoResponse,
  AnaliseIAResponse,
  AtendimentoResponse,
  DocumentoResponse,
  EncaminhamentoResponse,
  TimelineResponse,
} from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "./AsyncState";
import { BadgeRisco, BadgeStatus } from "./Badge";
import { Detalhes } from "./Detalhes";
import { AcoesAgendamento, MarcarAgendamento } from "./AcoesAgendamento";
import { AnexarDocumento } from "./CamposDocumento";
import { tiposFaltantes } from "../lib/documentos";
import { useAuth } from "../auth/AuthContext";

/** Resumo legível de um encaminhamento — usado após criar, ao consultar e no detalhe da fila. */
export function ResumoEncaminhamento({ encaminhamento }: { encaminhamento: EncaminhamentoResponse }) {
  return (
    <div className="card-encaminhamento">
      <div className="card-encaminhamento-topo">
        <strong>{encaminhamento.especialidadeOuExame}</strong>
        <BadgeStatus status={encaminhamento.status} />
      </div>
      <p className="hint">
        {TIPO_ENCAMINHAMENTO_LABELS[encaminhamento.tipo]} · Encaminhamento #
        {encaminhamento.id}
        {encaminhamento.urgente && " · marcado como urgente"}
      </p>
      {encaminhamento.urgente && encaminhamento.justificativaUrgencia && (
        <p className="hint">Justificativa da urgência: {encaminhamento.justificativaUrgencia}</p>
      )}
    </div>
  );
}

const ROTULOS_FATOR: Record<string, string> = {
  classificacaoRisco: "Classificação de risco",
  pesoRisco: "Peso do risco",
  diasDesdeAtendimento: "Dias desde o atendimento",
  bonusTempoEspera: "Bônus por tempo de espera",
  urgenteMarcadoPeloMedico: "Marcado como urgente pelo médico",
  bonusUrgencia: "Bônus de urgência",
  justificativaUrgencia: "Justificativa da urgência",
};

const ROTULOS_SEVERIDADE: Record<string, string> = {
  BLOQUEANTE: "Bloqueante",
  ALERTA: "Alerta",
};

function formatarValorFator(valor: unknown): string {
  if (typeof valor === "boolean") return valor ? "Sim" : "Não";
  return String(valor);
}

/** Detalhes legíveis de uma análise de IA — os campos fatoresConsiderados/irregularidades chegam do backend como texto JSON. */
export function DetalhesAnaliseIA({ analise }: { analise: AnaliseIAResponse }) {
  let fatores: Record<string, unknown> = {};
  let irregularidades: { descricao: string; severidade: string }[] = [];
  try {
    fatores = JSON.parse(analise.fatoresConsiderados);
  } catch {
    // formato inesperado — segue sem detalhar os fatores
  }
  try {
    irregularidades = JSON.parse(analise.irregularidades);
  } catch {
    // formato inesperado — segue sem detalhar as irregularidades
  }

  return (
    <div className="card-encaminhamento">
      <p>
        Score de prioridade: <strong>{analise.scorePrioridade.toFixed(1)}</strong>
        {" · "}
        {analise.bloqueado ? "Bloqueado para revisão" : "Liberado para a fila"}
      </p>
      <p>{analise.justificativaTexto}</p>
      {Object.keys(fatores).length > 0 && (
        <>
          <p className="hint">Fatores considerados:</p>
          <ul>
            {Object.entries(fatores).map(([chave, valor]) => (
              <li key={chave}>
                {ROTULOS_FATOR[chave] ?? chave}: {formatarValorFator(valor)}
              </li>
            ))}
          </ul>
        </>
      )}
      {irregularidades.length > 0 && (
        <>
          <p className="hint">Irregularidades encontradas:</p>
          <ul>
            {irregularidades.map((irregularidade, indice) => (
              <li key={indice}>
                {ROTULOS_SEVERIDADE[irregularidade.severidade] ?? irregularidade.severidade}:{" "}
                {irregularidade.descricao}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

const ROTULOS_ETAPA: Record<string, string> = {
  AGUARDANDO_DOCUMENTOS: "Documentação",
  EM_ANALISE: "Análise da IA",
  NA_FILA: "Na fila",
  AGENDADO: "Agendado",
  REALIZADO: "Atendido",
  BLOQUEADO_REVISAO: "Bloqueado para revisão",
  CANCELADO: "Cancelado",
};

/** Etapas lado a lado: concluídas em verde, a atual destacada; bloqueio/cancelamento vêm como etapa única de alerta. */
export function TimelineEtapas({ timeline }: { timeline: TimelineResponse }) {
  if (timeline.etapas.length === 0) return <SemDados />;
  const indiceAtual = timeline.etapas.map((etapa) => etapa.concluida).lastIndexOf(true);
  const ehAlerta = timeline.etapas.length === 1;
  return (
    <ol className="timeline">
      {timeline.etapas.map((etapa, indice) => {
        const classes = [
          etapa.concluida ? "concluida" : "",
          indice === indiceAtual ? "atual" : "",
          ehAlerta ? "alerta" : "",
        ].filter(Boolean);
        return (
          <li key={etapa.nome} className={classes.join(" ")}>
            <span className="timeline-marcador">{ehAlerta ? "!" : etapa.concluida ? "✓" : indice + 1}</span>
            <span className="timeline-nome">{ROTULOS_ETAPA[etapa.nome] ?? etapa.nome}</span>
          </li>
        );
      })}
    </ol>
  );
}

export function ListaDocumentos({ documentos }: { documentos: DocumentoResponse[] }) {
  if (documentos.length === 0) return <SemDados label="Nenhum documento anexado ainda." />;
  return (
    <ul>
      {documentos.map((documento) => (
        <li key={documento.id}>
          {rotuloTipoDocumento(documento.tipo)} — {documento.referenciaArquivo} (emitido em{" "}
          {formatarData(documento.dataEmissao)}
          {documento.validade && `, válido até ${formatarData(documento.validade)}`})
        </li>
      ))}
    </ul>
  );
}

/** Depois de agendado, anexar não refaz a triagem (ADR-009) — o botão some para não confundir. */
const STATUS_SEM_ANEXO: EncaminhamentoResponse["status"][] = ["AGENDADO", "REALIZADO", "CANCELADO"];

/** Um item por documento exigido pela especialidade: ✔ anexado ou ✖ faltando. */
function ChecklistDocumentos({ exigidos, documentos }: { exigidos: string[]; documentos: DocumentoResponse[] }) {
  if (exigidos.length === 0) return null;
  const faltando = new Set(tiposFaltantes(exigidos, documentos));
  return (
    <ul className="checklist-documentos" aria-label="Documentos exigidos">
      {exigidos.map((tipo) => (
        <li key={tipo} className={faltando.has(tipo) ? "doc-faltando" : "doc-ok"}>
          <span aria-hidden="true">{faltando.has(tipo) ? "✖" : "✔"}</span> {rotuloTipoDocumento(tipo)}
          <span className="visualmente-oculto">{faltando.has(tipo) ? " (faltando)" : " (anexado)"}</span>
        </li>
      ))}
    </ul>
  );
}

type Secao<T> = { status: "carregando" } | { status: "ok"; dados: T } | { status: "erro"; mensagem: string };

const CARREGANDO = { status: "carregando" } as const;

/** Mostra o estado de uma seção carregada de forma independente — uma falha não derruba as outras. */
function ConteudoSecao<T>({
  secao,
  erroComoVazio = false,
  children,
}: {
  secao: Secao<T>;
  erroComoVazio?: boolean;
  children: (dados: T) => ReactNode;
}) {
  if (secao.status === "carregando") return <Carregando />;
  if (secao.status === "erro")
    return erroComoVazio ? <SemDados label={secao.mensagem} /> : <ErroMensagem mensagem={secao.mensagem} />;
  return <>{children(secao.dados)}</>;
}

/**
 * Tudo sobre um encaminhamento (dados, atendimento de origem, timeline, documentos e análise de IA),
 * cada parte carregada por conta própria. Remonte com `key={encaminhamentoId}` ao trocar de encaminhamento.
 */
export function PainelEncaminhamento({
  encaminhamentoId,
  onVerTodosDoPaciente,
  onAlterado,
  podeAnexarDocumento = false,
}: {
  encaminhamentoId: number;
  /** Médico da UBS: mostra "+ Anexar documento" enquanto o encaminhamento ainda não foi agendado. */
  podeAnexarDocumento?: boolean;
  /** Quando informado, mostra o botão "Ver todos os encaminhamentos de <paciente>". */
  onVerTodosDoPaciente?: (paciente: { id: number; nome: string }) => void;
  /** Avisado depois de cancelar/remarcar/antecipar, para quem mostra listas (fila, vagas) se atualizar. */
  onAlterado?: () => void;
}) {
  const { sessao } = useAuth();
  const podeAlterarAgendamento = sessao?.papel === "SECRETARIA" || sessao?.papel === "ADMIN";
  // Incrementado após uma alteração no agendamento: recarrega o painel inteiro (situação, timeline, agendamento).
  const [versao, setVersao] = useState(0);
  const [encaminhamento, setEncaminhamento] = useState<Secao<EncaminhamentoResponse>>(CARREGANDO);
  const [agendamento, setAgendamento] = useState<Secao<AgendamentoResponse | null>>(CARREGANDO);
  const [atendimento, setAtendimento] = useState<Secao<AtendimentoResponse>>(CARREGANDO);
  const [timeline, setTimeline] = useState<Secao<TimelineResponse>>(CARREGANDO);
  const [documentos, setDocumentos] = useState<Secao<DocumentoResponse[]>>(CARREGANDO);
  const [analise, setAnalise] = useState<Secao<AnaliseIAResponse>>(CARREGANDO);
  const [exigidos, setExigidos] = useState<string[]>([]);

  useEffect(() => {
    let ativo = true;

    function acompanhar<T>(promessa: Promise<T>, setar: (secao: Secao<T>) => void, falha: string) {
      promessa
        .then((dados) => ativo && setar({ status: "ok", dados }))
        .catch((e) => ativo && setar({ status: "erro", mensagem: e instanceof ApiError ? e.message : falha }));
    }

    const promessaEncaminhamento = api.buscarEncaminhamento(encaminhamentoId);
    acompanhar(promessaEncaminhamento, setEncaminhamento, "Falha ao carregar o encaminhamento");
    acompanhar(
      promessaEncaminhamento.then((enc) => api.buscarAtendimento(enc.atendimentoId)),
      setAtendimento,
      "Falha ao carregar o atendimento",
    );
    acompanhar(
      promessaEncaminhamento.then((enc) =>
        enc.status === "AGENDADO" || enc.status === "REALIZADO" ? api.agendamentoDoEncaminhamento(enc.id) : null,
      ),
      setAgendamento,
      "Falha ao carregar o agendamento",
    );
    // Apoio ao checklist de documentos: se falhar, a lista de anexados continua aparecendo normalmente.
    promessaEncaminhamento
      .then((enc) => api.listarDocumentosExigidos(enc.especialidadeOuExame))
      .then((lista) => ativo && setExigidos(lista))
      .catch(() => ativo && setExigidos([]));
    acompanhar(api.timelineEncaminhamento(encaminhamentoId), setTimeline, "Falha ao carregar a timeline");
    acompanhar(api.listarDocumentos(encaminhamentoId), setDocumentos, "Falha ao carregar os documentos");
    acompanhar(api.analiseIA(encaminhamentoId), setAnalise, "Análise de IA ainda não disponível");

    return () => {
      ativo = false;
    };
  }, [encaminhamentoId, versao]);

  function aposAnexarDocumento() {
    setEncaminhamento(CARREGANDO);
    setTimeline(CARREGANDO);
    setDocumentos(CARREGANDO);
    setAnalise(CARREGANDO);
    setVersao((v) => v + 1);
    onAlterado?.();
  }

  function aposAlterarAgendamento() {
    setEncaminhamento(CARREGANDO);
    setAgendamento(CARREGANDO);
    setTimeline(CARREGANDO);
    setVersao((v) => v + 1);
    onAlterado?.();
  }

  return (
    <>
      <h3>Dados</h3>
      <ConteudoSecao secao={encaminhamento}>{(enc) => <ResumoEncaminhamento encaminhamento={enc} />}</ConteudoSecao>

      {/* Na fila e ainda sem vaga: a secretaria marca daqui mesmo. */}
      {podeAlterarAgendamento &&
        encaminhamento.status === "ok" &&
        encaminhamento.dados.status === "NA_FILA" &&
        agendamento.status === "ok" &&
        agendamento.dados === null && (
          <>
            <h3>Agendamento</h3>
            <p className="hint">Aguardando agendamento.</p>
            <MarcarAgendamento
              encaminhamentoId={encaminhamento.dados.id}
              especialidade={encaminhamento.dados.especialidadeOuExame}
              onMarcado={aposAlterarAgendamento}
            />
          </>
        )}

      {/* Só existe agendamento a mostrar quando o encaminhamento já foi agendado ou atendido. */}
      {!(agendamento.status === "ok" && agendamento.dados === null) && (
        <>
          <h3>Agendamento</h3>
          <ConteudoSecao secao={agendamento}>
            {(ag) =>
              ag && (
                <>
                  <Detalhes
                    titulo={`${STATUS_AGENDAMENTO_LABELS[ag.status]} para ${formatarDataHora(ag.dataHora)}`}
                    itens={[
                      { rotulo: "Profissional", valor: ag.profissionalNome ?? "—" },
                      { rotulo: "Unidade", valor: ag.unidadeNome ?? "—" },
                      { rotulo: "Vaga", valor: `#${ag.vagaId}` },
                      { rotulo: "Agendado por", valor: ag.agendadoPor },
                      {
                        rotulo: "Presença do paciente",
                        valor: ag.presencaConfirmadaEm
                          ? `Confirmada em ${formatarDataHora(ag.presencaConfirmadaEm)}`
                          : "Ainda não confirmada",
                      },
                    ]}
                  />
                  {podeAlterarAgendamento && ag.status === "CONFIRMADO" && encaminhamento.status === "ok" && (
                    <AcoesAgendamento
                      agendamento={ag}
                      especialidade={encaminhamento.dados.especialidadeOuExame}
                      onAlterado={aposAlterarAgendamento}
                    />
                  )}
                  {ag.historico.length > 0 && (
                    <ul className="historico-agendamento">
                      {ag.historico.map((h, indice) => (
                        <li key={indice}>
                          <strong>{ROTULOS_ACAO_AGENDAMENTO[h.acao]}</strong> em {formatarDataHora(h.feitoEm)} por{" "}
                          {h.feitoPor}: de {formatarDataHora(h.dataHoraAnterior)}
                          {h.dataHoraNova && ` para ${formatarDataHora(h.dataHoraNova)}`}. Motivo: {h.motivo}
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )
            }
          </ConteudoSecao>
        </>
      )}

      <h3>Atendimento de origem</h3>
      <ConteudoSecao secao={atendimento}>
        {(at) => (
          <Detalhes
            titulo={`Atendimento #${at.id}`}
            itens={[
              { rotulo: "Paciente", valor: nomeOuId(at.pacienteNome, at.pacienteId) },
              { rotulo: "Médico(a)", valor: nomeOuId(at.profissionalNome, at.profissionalId) },
              { rotulo: "Unidade", valor: nomeOuId(at.unidadeNome, at.unidadeId) },
              { rotulo: "Data e hora", valor: formatarDataHora(at.data) },
              { rotulo: "Classificação de risco", valor: <BadgeRisco risco={at.classificacaoRisco} /> },
              { rotulo: "Notas", valor: at.notas || "Sem notas" },
            ]}
          />
        )}
      </ConteudoSecao>
      {onVerTodosDoPaciente && atendimento.status === "ok" && (
        <button
          type="button"
          className="botao-voltar"
          onClick={() =>
            onVerTodosDoPaciente({
              id: atendimento.dados.pacienteId,
              nome: nomeOuId(atendimento.dados.pacienteNome, atendimento.dados.pacienteId),
            })
          }
        >
          Ver todos os encaminhamentos de {nomeOuId(atendimento.dados.pacienteNome, atendimento.dados.pacienteId)} →
        </button>
      )}

      <h3>Timeline</h3>
      <ConteudoSecao secao={timeline}>{(tl) => <TimelineEtapas timeline={tl} />}</ConteudoSecao>

      <h3>Documentos anexados</h3>
      <ConteudoSecao secao={documentos}>
        {(docs) => (
          <>
            <ChecklistDocumentos exigidos={exigidos} documentos={docs} />
            <ListaDocumentos documentos={docs} />
          </>
        )}
      </ConteudoSecao>
      {podeAnexarDocumento &&
        encaminhamento.status === "ok" &&
        !STATUS_SEM_ANEXO.includes(encaminhamento.dados.status) && (
          <AnexarDocumento
            encaminhamentoId={encaminhamento.dados.id}
            tipoSugerido={documentos.status === "ok" ? tiposFaltantes(exigidos, documentos.dados)[0] : undefined}
            onAnexado={aposAnexarDocumento}
          />
        )}

      <h3>Análise de IA</h3>
      <ConteudoSecao secao={analise} erroComoVazio>
        {(a) => <DetalhesAnaliseIA analise={a} />}
      </ConteudoSecao>
    </>
  );
}

/**
 * Detalhe de um encaminhamento com navegação: dá para abrir a lista de todos os encaminhamentos do paciente
 * (inclusive os já atendidos, que saíram da fila) e, dali, o detalhe de qualquer um deles.
 */
export function EncaminhamentoNavegavel({
  encaminhamentoId,
  onAlterado,
}: {
  encaminhamentoId: number;
  onAlterado?: () => void;
}) {
  const [atual, setAtual] = useState(encaminhamentoId);
  const [paciente, setPaciente] = useState<{ id: number; nome: string } | null>(null);

  if (paciente) {
    return (
      <EncaminhamentosDoPaciente
        paciente={paciente}
        destacadoId={atual}
        onAbrir={(id) => {
          setAtual(id);
          setPaciente(null);
        }}
        onVoltar={() => setPaciente(null)}
      />
    );
  }

  return (
    <>
      {atual !== encaminhamentoId && (
        <>
          <button type="button" className="botao-voltar" onClick={() => setAtual(encaminhamentoId)}>
            ← Voltar ao encaminhamento #{encaminhamentoId}
          </button>
          <h3>Encaminhamento #{atual}</h3>
        </>
      )}
      <PainelEncaminhamento
        key={atual}
        encaminhamentoId={atual}
        onVerTodosDoPaciente={setPaciente}
        onAlterado={onAlterado}
      />
    </>
  );
}

function EncaminhamentosDoPaciente({
  paciente,
  destacadoId,
  onAbrir,
  onVoltar,
}: {
  paciente: { id: number; nome: string };
  destacadoId: number;
  onAbrir: (encaminhamentoId: number) => void;
  onVoltar: () => void;
}) {
  const [lista, setLista] = useState<Secao<EncaminhamentoResponse[]>>(CARREGANDO);

  useEffect(() => {
    let ativo = true;
    api
      .meusEncaminhamentos(paciente.id)
      .then((dados) => ativo && setLista({ status: "ok", dados }))
      .catch(
        (e) =>
          ativo &&
          setLista({
            status: "erro",
            mensagem: e instanceof ApiError ? e.message : "Falha ao carregar os encaminhamentos do paciente",
          }),
      );
    return () => {
      ativo = false;
    };
  }, [paciente.id]);

  return (
    <>
      <button type="button" className="botao-voltar" onClick={onVoltar}>
        ← Voltar
      </button>
      <h3>Todos os encaminhamentos de {paciente.nome}</h3>
      <ConteudoSecao secao={lista}>
        {(encaminhamentos) =>
          encaminhamentos.length === 0 ? (
            <SemDados label="Nenhum encaminhamento para este paciente." />
          ) : (
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
                  {[...encaminhamentos]
                    .sort((a, b) => b.id - a.id)
                    .map((enc) => (
                      <tr
                        key={enc.id}
                        className={enc.id === destacadoId ? "linha-clicavel linha-selecionada" : "linha-clicavel"}
                        title="Ver detalhes do encaminhamento"
                        tabIndex={0}
                        onClick={() => onAbrir(enc.id)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") onAbrir(enc.id);
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
          )
        }
      </ConteudoSecao>
    </>
  );
}
