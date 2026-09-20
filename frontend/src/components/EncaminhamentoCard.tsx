import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import type {
  AgendamentoResponse,
  AtendimentoResponse,
  EncaminhamentoResponse,
  FilaItemResponse,
  ResultadoExameResponse,
  StatusEncaminhamento,
  TimelineResponse,
} from "../lib/types";
import { Carregando } from "./AsyncState";
import { TimelineEtapas } from "./DetalhesEncaminhamento";
import { formatarData, formatarDataHora, formatarDataPorExtenso } from "../lib/formatacao";

/** Situação em palavras do paciente (sem termos internos como "bloqueado para revisão"). */
const SITUACAO_PACIENTE: Record<StatusEncaminhamento, string> = {
  AGUARDANDO_DOCUMENTOS: "Juntando documentos",
  EM_ANALISE: "Em análise",
  BLOQUEADO_REVISAO: "Em revisão",
  NA_FILA: "Aguardando vaga",
  AGENDADO: "Marcado",
  REALIZADO: "Atendido",
  CANCELADO: "Cancelado",
};

interface Dados {
  atendimento: AtendimentoResponse | null;
  timeline: TimelineResponse | null;
  posicao: FilaItemResponse | null;
  agendamento: AgendamentoResponse | null;
  resultado: ResultadoExameResponse | null;
}

/** Busca que pode não ter dado ainda (ex.: sem resultado registrado): falha vira `null`, não erro na tela. */
function talvez<T>(promessa: Promise<T>): Promise<T | null> {
  return promessa.catch(() => null);
}

/**
 * Card de um encaminhamento na visão do paciente (Home e "Meus encaminhamentos"): o que está acontecendo,
 * o próximo passo, onde e quando ir. Não mostra dado interno da equipe (score, análise de IA, risco,
 * notas do médico, CID, documentos, ajustes da fila, motivos de remarcação).
 */
export function EncaminhamentoCard({ encaminhamento }: { encaminhamento: EncaminhamentoResponse }) {
  const [dados, setDados] = useState<Dados | null>(null);
  const [versao, setVersao] = useState(0);
  const [confirmando, setConfirmando] = useState(false);
  const [erroConfirmar, setErroConfirmar] = useState<string | null>(null);

  const { id, status } = encaminhamento;

  useEffect(() => {
    let ativo = true;
    const agendado = status === "AGENDADO" || status === "REALIZADO";
    Promise.all([
      talvez(api.buscarAtendimento(encaminhamento.atendimentoId)),
      talvez(api.timelineEncaminhamento(id)),
      status === "NA_FILA" ? talvez(api.posicaoFila(id)) : Promise.resolve(null),
      agendado ? talvez(api.agendamentoDoEncaminhamento(id)) : Promise.resolve(null),
      status === "REALIZADO" ? talvez(api.resultadoExame(id)) : Promise.resolve(null),
    ]).then(([atendimento, timeline, posicao, agendamento, resultado]) => {
      if (ativo) setDados({ atendimento, timeline, posicao, agendamento, resultado });
    });
    return () => {
      ativo = false;
    };
  }, [id, status, encaminhamento.atendimentoId, versao]);

  async function confirmarPresenca(agendamentoId: number) {
    setConfirmando(true);
    setErroConfirmar(null);
    try {
      await api.confirmarPresenca(agendamentoId);
      setVersao((v) => v + 1);
    } catch (e) {
      setErroConfirmar(e instanceof ApiError ? e.message : "Não foi possível confirmar agora. Tente de novo.");
    } finally {
      setConfirmando(false);
    }
  }

  const ehExame = encaminhamento.tipo === "EXAME";
  const oQue = ehExame ? "exame" : "consulta";
  const titulo = ehExame
    ? `Exame: ${encaminhamento.especialidadeOuExame}`
    : `Consulta com ${encaminhamento.especialidadeOuExame}`;

  return (
    <article className="card card-paciente">
      <div className="card-encaminhamento-topo">
        <h3>{titulo}</h3>
        <span className={`badge badge-status-${status.toLowerCase()}`}>{SITUACAO_PACIENTE[status]}</span>
      </div>
      <p className="hint">
        Protocolo nº {id}
        {encaminhamento.urgente && " · Seu médico pediu prioridade para este encaminhamento"}
      </p>

      {!dados && <Carregando label="Carregando..." />}
      {dados && (
        <>
          {dados.timeline && <TimelineEtapas timeline={dados.timeline} />}
          <Situacao
            status={status}
            oQue={oQue}
            dados={dados}
            confirmando={confirmando}
            erroConfirmar={erroConfirmar}
            onConfirmar={confirmarPresenca}
          />
          {dados.atendimento && (
            <div className="paciente-origem">
              <h4>Onde tudo começou</h4>
              <p>
                <strong>{dados.atendimento.unidadeNome ?? "Sua UBS"}</strong>
                {dados.atendimento.unidadeEndereco && <> · {dados.atendimento.unidadeEndereco}</>}
              </p>
              <p className="hint">
                Atendido(a) por {dados.atendimento.profissionalNome ?? "seu médico"} em{" "}
                {formatarData(dados.atendimento.data)}, que fez este encaminhamento.
              </p>
            </div>
          )}
        </>
      )}
    </article>
  );
}

function Situacao({
  status,
  oQue,
  dados,
  confirmando,
  erroConfirmar,
  onConfirmar,
}: {
  status: StatusEncaminhamento;
  oQue: string;
  dados: Dados;
  confirmando: boolean;
  erroConfirmar: string | null;
  onConfirmar: (agendamentoId: number) => void;
}) {
  const { agendamento, posicao, resultado } = dados;

  switch (status) {
    case "AGUARDANDO_DOCUMENTOS":
      return (
        <p className="aviso-paciente">
          Seu médico ainda está reunindo os documentos necessários. Assim que estiver tudo certo, seu pedido entra
          na fila. Você não precisa fazer nada agora.
        </p>
      );
    case "EM_ANALISE":
      return <p className="aviso-paciente">Estamos conferindo seus documentos. Isso costuma ser rápido.</p>;
    case "BLOQUEADO_REVISAO":
      return (
        <p className="aviso-paciente">
          Seu pedido está passando por uma revisão da equipe de saúde. Se precisar de algo, a sua UBS vai entrar em
          contato.
        </p>
      );
    case "NA_FILA":
      return (
        <p className="aviso-paciente aviso-fila">
          {posicao ? (
            <>
              Você está na <strong>posição {posicao.posicao}</strong> da fila.{" "}
            </>
          ) : (
            "Você está na fila. "
          )}
          A secretaria de saúde vai marcar sua {oQue} assim que houver vaga, e a data aparece aqui.
        </p>
      );
    case "CANCELADO":
      return (
        <p className="aviso-paciente">Este encaminhamento foi cancelado. Em caso de dúvida, procure a sua UBS.</p>
      );
    case "AGENDADO":
      if (!agendamento) return <p className="aviso-paciente aviso-agendado">Sua {oQue} está marcada.</p>;
      return (
        <div className="aviso-paciente aviso-agendado">
          <p className="aviso-titulo">Sua {oQue} está marcada para</p>
          <p className="aviso-data">{formatarDataPorExtenso(agendamento.dataHora)}</p>
          <dl className="aviso-lista">
            <dt>Local</dt>
            <dd>
              {agendamento.unidadeNome ?? "—"}
              {agendamento.unidadeEndereco && <> · {agendamento.unidadeEndereco}</>}
            </dd>
            {agendamento.profissionalNome && (
              <>
                <dt>Com</dt>
                <dd>{agendamento.profissionalNome}</dd>
              </>
            )}
          </dl>
          {agendamento.historico.some((h) => h.acao !== "CANCELADO") && (
            <p className="hint">A data foi alterada pela secretaria de saúde. Vale a data acima.</p>
          )}
          {agendamento.presencaConfirmadaEm ? (
            <p className="presenca-ok">
              ✓ Você confirmou presença em {formatarDataHora(agendamento.presencaConfirmadaEm)}. Obrigado!
            </p>
          ) : (
            <div className="presenca-pedido">
              <p>Você vai conseguir ir? Confirme para a equipe se organizar.</p>
              <button type="button" onClick={() => onConfirmar(agendamento.id)} disabled={confirmando}>
                {confirmando ? "Confirmando..." : "Confirmar presença"}
              </button>
              {erroConfirmar && <p className="erro-busca">{erroConfirmar}</p>}
            </div>
          )}
          <p className="hint">
            Leve um documento com foto, o cartão do SUS e chegue 15 minutos antes. Se não puder ir, avise a sua
            UBS para a vaga ir para outra pessoa.
          </p>
        </div>
      );
    case "REALIZADO":
      return (
        <div className="aviso-paciente aviso-atendido">
          <p className="aviso-titulo">
            {agendamento
              ? `Você foi atendido(a) em ${formatarDataPorExtenso(agendamento.dataHora)}.`
              : `Sua ${oQue} já foi realizada.`}
          </p>
          {agendamento?.unidadeNome && (
            <p className="hint">
              {agendamento.unidadeNome}
              {agendamento.profissionalNome && ` · ${agendamento.profissionalNome}`}
            </p>
          )}
          {resultado ? (
            <p>
              Resultado registrado em {formatarData(resultado.dataResultado)}
              {resultado.observacoes && (
                <>
                  : <em>{resultado.observacoes}</em>
                </>
              )}
              . Converse com seu médico da UBS sobre ele.
            </p>
          ) : (
            <p>O resultado ainda não está disponível.</p>
          )}
        </div>
      );
  }
}
