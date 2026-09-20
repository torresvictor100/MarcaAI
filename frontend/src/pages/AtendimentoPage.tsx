import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { AtendimentoResponse, ClassificacaoRisco, PacienteResumoResponse, UnidadeResponse } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { BadgeRisco } from "../components/Badge";
import { BuscaPaciente } from "../components/BuscaPaciente";
import { Detalhes } from "../components/Detalhes";
import { formatarDataHora, nomeOuId, RISCO_LABELS } from "../lib/formatacao";

function DetalhesAtendimento({ titulo, atendimento }: { titulo: string; atendimento: AtendimentoResponse }) {
  return (
    <Detalhes
      titulo={titulo}
      itens={[
        { rotulo: "Paciente", valor: nomeOuId(atendimento.pacienteNome, atendimento.pacienteId) },
        { rotulo: "Profissional", valor: nomeOuId(atendimento.profissionalNome, atendimento.profissionalId) },
        { rotulo: "Unidade", valor: nomeOuId(atendimento.unidadeNome, atendimento.unidadeId) },
        { rotulo: "Data e hora", valor: formatarDataHora(atendimento.data) },
        { rotulo: "Classificação de risco", valor: <BadgeRisco risco={atendimento.classificacaoRisco} /> },
        { rotulo: "Notas", valor: atendimento.notas || "Sem notas" },
      ]}
    />
  );
}

const RISCOS: ClassificacaoRisco[] = ["AZUL", "VERDE", "AMARELO", "LARANJA", "VERMELHO"];

export function AtendimentoPage() {
  const [paciente, setPaciente] = useState<PacienteResumoResponse | null>(null);
  const [unidades, setUnidades] = useState<UnidadeResponse[]>([]);
  const [erroUnidades, setErroUnidades] = useState<string | null>(null);
  const [unidadeId, setUnidadeId] = useState("");
  const [data, setData] = useState("");
  const [notas, setNotas] = useState("");
  const [classificacaoRisco, setClassificacaoRisco] = useState<ClassificacaoRisco>("VERDE");

  const [criando, setCriando] = useState(false);
  const [erroCriar, setErroCriar] = useState<string | null>(null);
  const [criado, setCriado] = useState<AtendimentoResponse | null>(null);

  const [pacienteBusca, setPacienteBusca] = useState<PacienteResumoResponse | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [erroBusca, setErroBusca] = useState<string | null>(null);
  const [atendimentosDoPaciente, setAtendimentosDoPaciente] = useState<AtendimentoResponse[] | null>(null);

  useEffect(() => {
    api
      .listarUnidades()
      // O atendimento acontece na UBS; se não houver nenhuma cadastrada, mostra todas.
      .then((todas) => {
        const ubs = todas.filter((u) => u.tipo === "UBS");
        setUnidades(ubs.length ? ubs : todas);
      })
      .catch((e) => setErroUnidades(e instanceof ApiError ? e.message : "Falha ao carregar unidades"));
  }, []);

  async function criar(event: FormEvent) {
    event.preventDefault();
    if (!paciente) {
      setErroCriar("Escolha um paciente na lista de resultados da busca");
      return;
    }
    setCriando(true);
    setErroCriar(null);
    setCriado(null);
    try {
      const resposta = await api.criarAtendimento({
        pacienteId: paciente.id,
        unidadeId: Number(unidadeId),
        data,
        notas: notas || undefined,
        classificacaoRisco,
      });
      setCriado(resposta);
    } catch (e) {
      setErroCriar(e instanceof ApiError ? e.message : "Falha ao criar atendimento");
    } finally {
      setCriando(false);
    }
  }

  async function escolherPacienteDaBusca(escolhido: PacienteResumoResponse | null) {
    setPacienteBusca(escolhido);
    setErroBusca(null);
    setAtendimentosDoPaciente(null);
    if (!escolhido) return;
    setBuscando(true);
    try {
      setAtendimentosDoPaciente(await api.listarAtendimentosDoPaciente(escolhido.id));
    } catch (e) {
      setErroBusca(e instanceof ApiError ? e.message : "Falha ao buscar atendimentos");
    } finally {
      setBuscando(false);
    }
  }

  return (
    <div className="grid-2">
      <div className="card">
        <h2>Novo atendimento</h2>
        <p className="hint">O atendimento fica registrado no seu nome (médico logado).</p>
        <form onSubmit={criar}>
          <div className="campo">
            <span className="campo-rotulo">Paciente</span>
            <BuscaPaciente selecionado={paciente} onSelecionar={setPaciente} />
          </div>
          <label>
            Unidade
            <select value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)} required>
              <option value="">{unidades.length ? "Selecione a unidade" : "Carregando unidades..."}</option>
              {unidades.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.nome}
                </option>
              ))}
            </select>
          </label>
          {erroUnidades && <ErroMensagem mensagem={erroUnidades} />}
          <label>
            Data/hora
            <input type="datetime-local" value={data} onChange={(e) => setData(e.target.value)} required />
          </label>
          <label>
            Classificação de risco
            <select value={classificacaoRisco} onChange={(e) => setClassificacaoRisco(e.target.value as ClassificacaoRisco)}>
              {RISCOS.map((r) => (
                <option key={r} value={r}>
                  {RISCO_LABELS[r]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Notas
            <textarea value={notas} onChange={(e) => setNotas(e.target.value)} />
          </label>
          <button type="submit" disabled={criando}>
            {criando ? "Criando..." : "Criar atendimento"}
          </button>
        </form>
        {erroCriar && <ErroMensagem mensagem={erroCriar} />}
        {criado && <DetalhesAtendimento titulo={`Atendimento #${criado.id} registrado`} atendimento={criado} />}
      </div>

      <div className="card">
        <h2>Atendimentos do paciente</h2>
        <div className="campo">
          <span className="campo-rotulo">Paciente</span>
          <BuscaPaciente selecionado={pacienteBusca} onSelecionar={escolherPacienteDaBusca} />
        </div>
        <p className="hint">Aparecem só os atendimentos que você registrou.</p>
        {buscando && <Carregando />}
        {erroBusca && <ErroMensagem mensagem={erroBusca} />}
        {atendimentosDoPaciente && atendimentosDoPaciente.length === 0 && (
          <SemDados label="Nenhum atendimento seu para este paciente." />
        )}
        {atendimentosDoPaciente && atendimentosDoPaciente.length > 0 && (
          <p className="hint">
            {atendimentosDoPaciente.length === 1 ? "1 atendimento" : `${atendimentosDoPaciente.length} atendimentos`}, do mais
            recente para o mais antigo.
          </p>
        )}
        {atendimentosDoPaciente?.map((a) => (
          <DetalhesAtendimento key={a.id} titulo={`Atendimento #${a.id} — ${formatarDataHora(a.data)}`} atendimento={a} />
        ))}
      </div>
    </div>
  );
}
