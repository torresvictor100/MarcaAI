import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import type {
  AtendimentoResponse,
  CidResponse,
  DocumentoRequest,
  EncaminhamentoResponse,
  FilaItemResponse,
  PacienteResumoResponse,
  TipoEncaminhamento,
} from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { BadgeStatus } from "../components/Badge";
import { BuscaPaciente } from "../components/BuscaPaciente";
import { CamposDocumento } from "../components/CamposDocumento";
import { PainelEncaminhamento } from "../components/DetalhesEncaminhamento";
import { documentoPreenchido, documentoVazio, listarRotulos, paraEnvio, tiposFaltantes } from "../lib/documentos";
import { formatarDataHora, RISCO_LABELS, TIPO_ENCAMINHAMENTO_LABELS } from "../lib/formatacao";

const TIPOS: TipoEncaminhamento[] = ["CONSULTA_ESPECIALISTA", "EXAME"];

function rotuloAtendimento(atendimento: AtendimentoResponse): string {
  return [
    formatarDataHora(atendimento.data),
    RISCO_LABELS[atendimento.classificacaoRisco],
    atendimento.unidadeNome ?? `Unidade #${atendimento.unidadeId}`,
  ].join(" — ");
}

function CriarEncaminhamento() {
  const [paciente, setPaciente] = useState<PacienteResumoResponse | null>(null);
  const [atendimentos, setAtendimentos] = useState<AtendimentoResponse[] | null>(null);
  const [carregandoAtendimentos, setCarregandoAtendimentos] = useState(false);
  const [erroAtendimentos, setErroAtendimentos] = useState<string | null>(null);
  const [atendimentoId, setAtendimentoId] = useState("");

  const [tipo, setTipo] = useState<TipoEncaminhamento>("CONSULTA_ESPECIALISTA");
  const [especialidades, setEspecialidades] = useState<string[]>([]);
  const [especialidade, setEspecialidade] = useState("");
  const [cids, setCids] = useState<CidResponse[]>([]);
  const [buscandoCids, setBuscandoCids] = useState(false);
  const [erroCids, setErroCids] = useState<string | null>(null);
  const [cidId, setCidId] = useState("");
  const [urgente, setUrgente] = useState(false);
  const [justificativaUrgencia, setJustificativaUrgencia] = useState("");
  const [documentos, setDocumentos] = useState<DocumentoRequest[]>(() => [documentoVazio()]);
  const [exigidos, setExigidos] = useState<string[]>([]);

  const [criando, setCriando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [criado, setCriado] = useState<EncaminhamentoResponse | null>(null);
  const [faltavamAoCriar, setFaltavamAoCriar] = useState<string[]>([]);
  const [posicaoFila, setPosicaoFila] = useState<FilaItemResponse | null>(null);

  useEffect(() => {
    api
      .listarEspecialidades()
      .then((lista) => {
        setEspecialidades(lista);
        setEspecialidade((atual) => atual || lista[0] || "");
      })
      .catch(() => {
        // Seletor de apoio — se falhar, o formulário segue utilizável, só sem sugestões.
      });
  }, []);

  async function buscarCids() {
    if (!especialidade) return;
    setBuscandoCids(true);
    setErroCids(null);
    setCids([]);
    setCidId("");
    try {
      setCids(await api.listarCids(especialidade));
    } catch (e) {
      setErroCids(e instanceof ApiError ? e.message : "Falha ao buscar CIDs");
    } finally {
      setBuscandoCids(false);
    }
  }

  // Busca os CIDs de novo automaticamente sempre que a especialidade selecionada muda.
  useEffect(() => {
    if (especialidade) buscarCids();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [especialidade]);

  // Documentos exigidos pela especialidade: o formulário abre com uma linha para cada tipo, enquanto
  // o médico ainda não preencheu nada (não apaga o que já foi digitado).
  useEffect(() => {
    if (!especialidade) return;
    let ativo = true;
    api
      .listarDocumentosExigidos(especialidade)
      .then((lista) => {
        if (!ativo) return;
        setExigidos(lista);
        setDocumentos((atuais) =>
          atuais.some((d) => d.referenciaArquivo || d.dataEmissao)
            ? atuais
            : lista.length > 0
              ? lista.map((tipo) => documentoVazio(tipo))
              : [documentoVazio()],
        );
      })
      .catch(() => ativo && setExigidos([]));
    return () => {
      ativo = false;
    };
  }, [especialidade]);

  async function escolherPaciente(escolhido: PacienteResumoResponse | null) {
    setPaciente(escolhido);
    setAtendimentos(null);
    setAtendimentoId("");
    setErroAtendimentos(null);
    if (!escolhido) return;
    setCarregandoAtendimentos(true);
    try {
      const lista = await api.listarAtendimentosDoPaciente(escolhido.id);
      setAtendimentos(lista);
      // O mais recente já vem escolhido: é o caso comum (encaminhar logo após atender).
      if (lista.length > 0) setAtendimentoId(String(lista[0].id));
    } catch (e) {
      setErroAtendimentos(e instanceof ApiError ? e.message : "Falha ao carregar os atendimentos do paciente");
    } finally {
      setCarregandoAtendimentos(false);
    }
  }

  function alterarDocumento(indice: number, documento: DocumentoRequest) {
    setDocumentos((lista) => lista.map((d, i) => (i === indice ? documento : d)));
  }

  function removerDocumento(indice: number) {
    setDocumentos((lista) => lista.filter((_, i) => i !== indice));
  }

  const documentosOk = documentos.length > 0 && documentos.every(documentoPreenchido);
  const podeCriar = !!atendimentoId && !!cidId && documentosOk && !criando;

  // O que ainda impede "Criar encaminhamento" — mostrado embaixo do botão desativado.
  const pendencias: string[] = [];
  if (!paciente) pendencias.push("Escolher o paciente");
  else if (!atendimentoId) pendencias.push("Escolher o atendimento de origem");
  if (!especialidade) pendencias.push("Escolher a especialidade/exame");
  else if (!cidId) pendencias.push(cids.length > 0 ? "Escolher o CID" : "Especialidade sem CID cadastrado");
  if (documentos.length === 0) pendencias.push("Adicionar pelo menos um documento");
  documentos.forEach((d, i) => {
    const faltando = [
      d.referenciaArquivo.trim() === "" && "a referência do arquivo",
      d.dataEmissao === "" && "a data de emissão",
    ].filter(Boolean);
    if (faltando.length > 0) pendencias.push(`Documento ${i + 1}: falta ${faltando.join(" e ")}`);
  });

  async function criar(event: FormEvent) {
    event.preventDefault();
    setCriando(true);
    setErro(null);
    setCriado(null);
    setPosicaoFila(null);
    try {
      const resposta = await api.criarEncaminhamento({
        atendimentoId: Number(atendimentoId),
        tipo,
        especialidadeOuExame: especialidade,
        cidId: Number(cidId),
        urgente,
        justificativaUrgencia: urgente ? justificativaUrgencia : undefined,
        documentos: documentos.map(paraEnvio),
      });
      setCriado(resposta);
      // A triagem roda na mesma chamada: se entrou na fila, já dá para mostrar a posição.
      if (resposta.status === "NA_FILA") {
        api
          .posicaoFila(resposta.id)
          .then(setPosicaoFila)
          .catch(() => setPosicaoFila(null));
      }
      setFaltavamAoCriar(tiposFaltantes(exigidos, documentos));
      setDocumentos(exigidos.length > 0 ? exigidos.map((tipo) => documentoVazio(tipo)) : [documentoVazio()]);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao criar encaminhamento");
    } finally {
      setCriando(false);
    }
  }

  return (
    <div className="card">
      <h2>Novo encaminhamento</h2>
      <form onSubmit={criar}>
        <div className="campo">
          <span className="campo-rotulo">Paciente</span>
          <BuscaPaciente selecionado={paciente} onSelecionar={escolherPaciente} />
        </div>
        {carregandoAtendimentos && <Carregando label="Carregando atendimentos..." />}
        {erroAtendimentos && <ErroMensagem mensagem={erroAtendimentos} />}
        {atendimentos && atendimentos.length === 0 && (
          <SemDados label="Nenhum atendimento seu para este paciente. Registre o atendimento antes de encaminhar." />
        )}
        {atendimentos && atendimentos.length > 0 && (
          <label>
            Atendimento de origem
            <select value={atendimentoId} onChange={(e) => setAtendimentoId(e.target.value)} required>
              {atendimentos.map((a) => (
                <option key={a.id} value={a.id}>
                  {rotuloAtendimento(a)}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Tipo
          <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoEncaminhamento)}>
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {TIPO_ENCAMINHAMENTO_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
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
        {buscandoCids && <Carregando label="Buscando CIDs..." />}
        {erroCids && <ErroMensagem mensagem={erroCids} />}
        {!buscandoCids && !erroCids && especialidade && cids.length === 0 && (
          <SemDados label={`Nenhum CID cadastrado para ${especialidade}. Escolha outra especialidade/exame.`} />
        )}
        {!buscandoCids && cids.length > 0 && (
          <label>
            CID
            <select value={cidId} onChange={(e) => setCidId(e.target.value)} required>
              <option value="">Selecione...</option>
              {cids.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.codigo} — {c.descricao}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="linha-checkbox">
          <input type="checkbox" checked={urgente} onChange={(e) => setUrgente(e.target.checked)} />
          Marcar urgência
        </label>
        {urgente && (
          <label>
            Justificativa da urgência
            <textarea
              value={justificativaUrgencia}
              onChange={(e) => setJustificativaUrgencia(e.target.value)}
              required
            />
          </label>
        )}

        <fieldset className="documentos-encaminhamento">
          <legend>Documentos</legend>
          {exigidos.length > 0 && (
            <p className="hint">
              Exigidos para {especialidade}: {listarRotulos(exigidos)}.
            </p>
          )}
          <p className="hint">
            Pelo menos um. Ao criar, a análise de IA roda na hora e o encaminhamento já entra na fila; o que
            faltar aparece como alerta na análise.
          </p>
          {documentos.map((documento, indice) => (
            <div key={indice} className="bloco-documento">
              <div className="bloco-documento-topo">
                <strong>Documento {indice + 1}</strong>
                {documentos.length > 1 && (
                  <button type="button" className="botao-link" onClick={() => removerDocumento(indice)}>
                    remover
                  </button>
                )}
              </div>
              <CamposDocumento documento={documento} onAlterar={(d) => alterarDocumento(indice, d)} />
            </div>
          ))}
          <button
            type="button"
            className="botao-secundario"
            onClick={() => setDocumentos((lista) => [...lista, documentoVazio()])}
          >
            + Adicionar documento
          </button>
        </fieldset>

        <button type="submit" disabled={!podeCriar}>
          {criando ? "Criando..." : "Criar encaminhamento"}
        </button>
        {!criando && pendencias.length > 0 && (
          <ul className="hint lista-pendencias">
            {pendencias.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
      </form>
      {erro && <ErroMensagem mensagem={erro} />}
      {criado && (
        <>
          <p className="hint">Encaminhamento #{criado.id} criado e analisado pela IA:</p>
          {criado.status === "BLOQUEADO_REVISAO" && (
            <p className="estado estado-alerta">
              A análise encontrou uma irregularidade bloqueante: o encaminhamento ficou em revisão e não entrou na
              fila. Veja o motivo na análise abaixo.
            </p>
          )}
          {posicaoFila && (
            <p className="destaque destaque-fila">
              Entrou na fila de {posicaoFila.especialidadeOuExame} na posição <strong>{posicaoFila.posicao}</strong>.
            </p>
          )}
          {faltavamAoCriar.length > 0 && (
            <p className="estado estado-alerta">
              Faltam: {listarRotulos(faltavamAoCriar)}. Anexe pelo "+ Anexar documento" abaixo para tirar o alerta da
              análise.
            </p>
          )}
          <PainelEncaminhamento key={criado.id} encaminhamentoId={criado.id} podeAnexarDocumento />
        </>
      )}
    </div>
  );
}

function ConsultarEncaminhamento() {
  const [paciente, setPaciente] = useState<PacienteResumoResponse | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [encaminhamentos, setEncaminhamentos] = useState<EncaminhamentoResponse[] | null>(null);
  const [abertoId, setAbertoId] = useState<number | null>(null);

  async function carregar(pacienteId: number) {
    setCarregando(true);
    setErro(null);
    try {
      const lista = await api.meusEncaminhamentos(pacienteId);
      setEncaminhamentos([...lista].sort((a, b) => b.id - a.id));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao buscar encaminhamentos");
    } finally {
      setCarregando(false);
    }
  }

  function escolherPaciente(escolhido: PacienteResumoResponse | null) {
    setPaciente(escolhido);
    setEncaminhamentos(null);
    setAbertoId(null);
    setErro(null);
    if (escolhido) carregar(escolhido.id);
  }

  return (
    <div className="card">
      <h2>Consultar encaminhamento</h2>
      <div className="campo">
        <span className="campo-rotulo">Paciente</span>
        <BuscaPaciente selecionado={paciente} onSelecionar={escolherPaciente} />
      </div>
      <p className="hint">Aparecem só os encaminhamentos que você gerou.</p>
      {carregando && <Carregando />}
      {erro && <ErroMensagem mensagem={erro} />}

      {abertoId !== null ? (
        <>
          <button type="button" className="botao-voltar" onClick={() => setAbertoId(null)}>
            ← Voltar à lista
          </button>
          <PainelEncaminhamento
            key={abertoId}
            encaminhamentoId={abertoId}
            podeAnexarDocumento
            onAlterado={() => paciente && carregar(paciente.id)}
          />
        </>
      ) : (
        encaminhamentos &&
        (encaminhamentos.length === 0 ? (
          <SemDados label="Nenhum encaminhamento seu para este paciente." />
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
                {encaminhamentos.map((enc) => (
                  <tr
                    key={enc.id}
                    className="linha-clicavel"
                    title="Ver detalhes do encaminhamento"
                    tabIndex={0}
                    onClick={() => setAbertoId(enc.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") setAbertoId(enc.id);
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
        ))
      )}
    </div>
  );
}

export function EncaminhamentoPage() {
  return (
    <div className="grid-2">
      <CriarEncaminhamento />
      <ConsultarEncaminhamento />
    </div>
  );
}
