import { useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { AgendaEncaminhamentoResponse, ResultadoExameResponse } from "../lib/types";
import { Carregando, ErroMensagem } from "../components/AsyncState";
import { Detalhes } from "../components/Detalhes";
import { BuscaEncaminhamentoAgenda } from "../components/BuscaEncaminhamentoAgenda";
import { PainelEncaminhamento } from "../components/DetalhesEncaminhamento";
import { Modal } from "../components/Modal";
import { formatarData } from "../lib/formatacao";

function DetalhesResultado({ titulo, resultado }: { titulo: string; resultado: ResultadoExameResponse }) {
  return (
    <Detalhes
      titulo={titulo}
      itens={[
        { rotulo: "Encaminhamento", valor: `#${resultado.encaminhamentoId}` },
        { rotulo: "Laudo/arquivo", valor: resultado.referenciaArquivo },
        { rotulo: "Data do resultado", valor: formatarData(resultado.dataResultado) },
        { rotulo: "Observações", valor: resultado.observacoes || "Sem observações" },
      ]}
    />
  );
}

export function ResultadoExamePage() {
  // O especialista (inclusive o laboratório) escolhe o paciente pelo nome, entre os agendados nas vagas dele.
  const [encaminhamentoDetalhe, setEncaminhamentoDetalhe] = useState<number | null>(null);

  const [aAtender, setAAtender] = useState<AgendaEncaminhamentoResponse | null>(null);
  const [versaoAAtender, setVersaoAAtender] = useState(0);
  const [atendido, setAtendido] = useState<AgendaEncaminhamentoResponse | null>(null);

  const [referenciaArquivo, setReferenciaArquivo] = useState("");
  const [dataResultado, setDataResultado] = useState("");
  const [observacoes, setObservacoes] = useState("");
  const [registrando, setRegistrando] = useState(false);
  const [erroRegistrar, setErroRegistrar] = useState<string | null>(null);
  const [registrado, setRegistrado] = useState<ResultadoExameResponse | null>(null);

  const [consultando, setConsultando] = useState(false);
  const [erroConsulta, setErroConsulta] = useState<string | null>(null);
  const [resultado, setResultado] = useState<ResultadoExameResponse | null>(null);

  const idParaRegistrar = aAtender?.encaminhamentoId ?? null;

  async function registrar(event: FormEvent) {
    event.preventDefault();
    if (!idParaRegistrar) {
      setErroRegistrar("Selecione o paciente");
      return;
    }
    setRegistrando(true);
    setErroRegistrar(null);
    setRegistrado(null);
    try {
      const resposta = await api.registrarResultadoExame(idParaRegistrar, {
        referenciaArquivo,
        dataResultado,
        observacoes: observacoes || undefined,
      });
      setRegistrado(resposta);
      // Atendido sai da lista "a atender": limpa a escolha e recarrega as sugestões.
      setAAtender(null);
      setVersaoAAtender((v) => v + 1);
    } catch (e) {
      setErroRegistrar(e instanceof ApiError ? e.message : "Falha ao registrar resultado");
    } finally {
      setRegistrando(false);
    }
  }

  async function buscarResultado(id: number) {
    setConsultando(true);
    setErroConsulta(null);
    setResultado(null);
    try {
      setResultado(await api.resultadoExame(id));
    } catch (e) {
      setErroConsulta(e instanceof ApiError ? e.message : "Falha ao consultar resultado");
    } finally {
      setConsultando(false);
    }
  }

  return (
    <div className="grid-2">
      <div className="card">
        <h2>Registrar resultado/laudo</h2>
        <p className="hint">
          O especialista registra o atendimento realizado (consulta ou exame, no caso do laboratório) dos pacientes
          agendados nas vagas dele. Só vale para encaminhamento já agendado, e cada encaminhamento aceita um único
          registro.
        </p>
        <form onSubmit={registrar}>
          <div className="campo">
            <span className="campo-rotulo">Paciente agendado com você</span>
            <BuscaEncaminhamentoAgenda
              situacao="A_ATENDER"
              selecionado={aAtender}
              onSelecionar={setAAtender}
              versao={versaoAAtender}
            />
            {aAtender && (
              <button
                type="button"
                className="botao-link"
                onClick={() => setEncaminhamentoDetalhe(aAtender.encaminhamentoId)}
              >
                Ver encaminhamento →
              </button>
            )}
          </div>
          <label>
            Referência do arquivo/laudo
            <input value={referenciaArquivo} onChange={(e) => setReferenciaArquivo(e.target.value)} required />
          </label>
          <label>
            Data do resultado
            <input type="date" value={dataResultado} onChange={(e) => setDataResultado(e.target.value)} required />
          </label>
          <label>
            Observações
            <textarea value={observacoes} onChange={(e) => setObservacoes(e.target.value)} />
          </label>
          <button type="submit" disabled={registrando || !aAtender}>
            {registrando ? "Registrando..." : "Registrar"}
          </button>
        </form>
        {erroRegistrar && <ErroMensagem mensagem={erroRegistrar} />}
        {registrado && <DetalhesResultado titulo="Resultado registrado" resultado={registrado} />}
      </div>

      <div className="card">
        <h2>Consultar resultado</h2>
        <div className="campo">
          <span className="campo-rotulo">Paciente já atendido por você</span>
          <BuscaEncaminhamentoAgenda
            situacao="ATENDIDO"
            selecionado={atendido}
            onSelecionar={(item) => {
              setAtendido(item);
              setResultado(null);
              setErroConsulta(null);
              if (item) buscarResultado(item.encaminhamentoId);
            }}
            versao={versaoAAtender}
          />
        </div>
        {consultando && <Carregando />}
        {erroConsulta && <ErroMensagem mensagem={erroConsulta} />}
        {resultado && <DetalhesResultado titulo="Resultado" resultado={resultado} />}
        {atendido && (
          <button type="button" onClick={() => setEncaminhamentoDetalhe(atendido.encaminhamentoId)}>
            Ver encaminhamento
          </button>
        )}
      </div>

      <Modal
        titulo={encaminhamentoDetalhe ? `Encaminhamento #${encaminhamentoDetalhe}` : ""}
        aberto={encaminhamentoDetalhe !== null}
        onFechar={() => setEncaminhamentoDetalhe(null)}
      >
        {encaminhamentoDetalhe && <PainelEncaminhamento key={encaminhamentoDetalhe} encaminhamentoId={encaminhamentoDetalhe} />}
      </Modal>
    </div>
  );
}
