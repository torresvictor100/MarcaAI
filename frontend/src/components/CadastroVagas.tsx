import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import { formatarDataHora, hojeLocal } from "../lib/formatacao";
import type { DiaDaSemana, ProfissionalResponse, UnidadeResponse, VagaLoteResponse } from "../lib/types";
import { ErroMensagem } from "./AsyncState";
import { Detalhes } from "./Detalhes";

const DIAS: { valor: DiaDaSemana; rotulo: string; indice: number }[] = [
  { valor: "MONDAY", rotulo: "Seg", indice: 1 },
  { valor: "TUESDAY", rotulo: "Ter", indice: 2 },
  { valor: "WEDNESDAY", rotulo: "Qua", indice: 3 },
  { valor: "THURSDAY", rotulo: "Qui", indice: 4 },
  { valor: "FRIDAY", rotulo: "Sex", indice: 5 },
  { valor: "SATURDAY", rotulo: "Sáb", indice: 6 },
  { valor: "SUNDAY", rotulo: "Dom", indice: 0 },
];

function minutos(hora: string): number {
  const [h, m] = hora.split(":").map(Number);
  return h * 60 + m;
}

/** Prévia do tamanho do lote (mesma regra do backend, sem descontar horários já passados de hoje). */
function contarVagas(dataInicio: string, dataFim: string, dias: DiaDaSemana[], horaInicio: string, horaFim: string, duracao: number) {
  if (!dataInicio || !dataFim || dataFim < dataInicio || !duracao || duracao < 10) return { diasNoPeriodo: 0, porDia: 0 };
  const porDia = Math.max(0, Math.floor((minutos(horaFim) - minutos(horaInicio)) / duracao));
  const indices = new Set(DIAS.filter((d) => dias.includes(d.valor)).map((d) => d.indice));
  // Datas montadas pelas partes (sem fuso), para o dia da semana não escorregar.
  const [ai, mi, di] = dataInicio.split("-").map(Number);
  const [af, mf, df] = dataFim.split("-").map(Number);
  const fim = new Date(af, mf - 1, df);
  let diasNoPeriodo = 0;
  for (let d = new Date(ai, mi - 1, di); d <= fim && diasNoPeriodo < 400; d.setDate(d.getDate() + 1)) {
    if (indices.has(d.getDay())) diasNoPeriodo++;
  }
  return { diasNoPeriodo, porDia };
}

/**
 * Abre vagas em lote para um profissional: período, dias da semana (repete toda semana) e faixa de horário
 * com a duração de cada consulta. Se o especialista ainda não existe, dá para cadastrá-lo aqui mesmo.
 */
export function CadastroVagas({
  especialidades,
  especialidadeInicial,
  onCriadas,
}: {
  especialidades: string[];
  especialidadeInicial: string;
  onCriadas: () => void;
}) {
  const [especialidade, setEspecialidade] = useState(especialidadeInicial || especialidades[0] || "");
  const [profissionais, setProfissionais] = useState<ProfissionalResponse[] | null>(null);
  const [profissionalId, setProfissionalId] = useState("");
  const [unidades, setUnidades] = useState<UnidadeResponse[]>([]);
  const [unidadeId, setUnidadeId] = useState("");
  const [dataInicio, setDataInicio] = useState(hojeLocal);
  const [dataFim, setDataFim] = useState(hojeLocal);
  const [dias, setDias] = useState<DiaDaSemana[]>(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]);
  const [horaInicio, setHoraInicio] = useState("08:00");
  const [horaFim, setHoraFim] = useState("12:00");
  const [duracao, setDuracao] = useState(30);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [resultado, setResultado] = useState<VagaLoteResponse | null>(null);
  const [novoProfissionalAberto, setNovoProfissionalAberto] = useState(false);
  const [versaoProfissionais, setVersaoProfissionais] = useState(0);
  const [profissionalRecemCriado, setProfissionalRecemCriado] = useState<number | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .listarUnidades()
      .then((lista) => {
        if (!ativo) return;
        setUnidades(lista);
        setUnidadeId((atual) => atual || (lista[0] ? String(lista[0].id) : ""));
      })
      .catch(() => ativo && setErro("Falha ao carregar as unidades"));
    return () => {
      ativo = false;
    };
  }, []);

  useEffect(() => {
    if (!especialidade) return;
    let ativo = true;
    api
      .listarProfissionais(especialidade)
      .then((lista) => {
        if (!ativo) return;
        setProfissionais(lista);
        setProfissionalId(() => {
          if (profissionalRecemCriado && lista.some((p) => p.id === profissionalRecemCriado)) {
            return String(profissionalRecemCriado);
          }
          return lista[0] ? String(lista[0].id) : "";
        });
      })
      .catch(() => ativo && setErro("Falha ao carregar os profissionais"));
    return () => {
      ativo = false;
    };
  }, [especialidade, versaoProfissionais, profissionalRecemCriado]);

  const { diasNoPeriodo, porDia } = contarVagas(dataInicio, dataFim, dias, horaInicio, horaFim, duracao);
  const previa = diasNoPeriodo * porDia;

  function alternarDia(dia: DiaDaSemana) {
    setDias((atual) => (atual.includes(dia) ? atual.filter((d) => d !== dia) : [...atual, dia]));
  }

  async function criar(event: FormEvent) {
    event.preventDefault();
    setEnviando(true);
    setErro(null);
    setResultado(null);
    try {
      const resposta = await api.criarVagasEmLote({
        profissionalId: Number(profissionalId),
        unidadeId: Number(unidadeId),
        dataInicio,
        dataFim,
        diasDaSemana: dias,
        horaInicio,
        horaFim,
        duracaoMinutos: duracao,
      });
      setResultado(resposta);
      onCriadas();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao cadastrar as vagas");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <form onSubmit={criar} className="form-cadastro-vagas">
        <label>
          Especialidade/exame
          <select value={especialidade} onChange={(e) => setEspecialidade(e.target.value)}>
            {especialidades.map((esp) => (
              <option key={esp} value={esp}>
                {esp}
              </option>
            ))}
          </select>
        </label>

        <label>
          Profissional
          {profissionais && profissionais.length === 0 ? (
            <span className="estado estado-vazio">Nenhum profissional de {especialidade} cadastrado.</span>
          ) : (
            <select value={profissionalId} onChange={(e) => setProfissionalId(e.target.value)} required>
              {(profissionais ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nome} ({p.registroConselho})
                </option>
              ))}
            </select>
          )}
        </label>
        {!novoProfissionalAberto && (
          <button type="button" className="botao-voltar" onClick={() => setNovoProfissionalAberto(true)}>
            + Novo profissional
          </button>
        )}
        {novoProfissionalAberto && (
          <NovoProfissional
            especialidades={especialidades}
            especialidadeInicial={especialidade}
            onCancelar={() => setNovoProfissionalAberto(false)}
            onCadastrado={(profissional) => {
              setNovoProfissionalAberto(false);
              setProfissionalRecemCriado(profissional.id);
              setEspecialidade(profissional.especialidade);
              setVersaoProfissionais((v) => v + 1);
            }}
          />
        )}

        <label>
          Unidade
          <select value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)} required>
            {unidades.map((u) => (
              <option key={u.id} value={u.id}>
                {u.nome}
              </option>
            ))}
          </select>
        </label>

        <div className="filtro-periodo">
          <label>
            De
            <input type="date" value={dataInicio} min={hojeLocal()} onChange={(e) => setDataInicio(e.target.value)} required />
          </label>
          <label>
            Até
            <input type="date" value={dataFim} min={dataInicio} onChange={(e) => setDataFim(e.target.value)} required />
          </label>
        </div>

        <fieldset className="dias-semana">
          <legend>Repetir toda semana em</legend>
          {DIAS.map((d) => (
            <label key={d.valor} className="linha-checkbox">
              <input type="checkbox" checked={dias.includes(d.valor)} onChange={() => alternarDia(d.valor)} />
              {d.rotulo}
            </label>
          ))}
        </fieldset>

        <div className="filtro-periodo">
          <label>
            Das
            <input type="time" value={horaInicio} onChange={(e) => setHoraInicio(e.target.value)} required />
          </label>
          <label>
            Até
            <input type="time" value={horaFim} onChange={(e) => setHoraFim(e.target.value)} required />
          </label>
          <label>
            Cada consulta (min)
            <input
              type="number"
              min={10}
              max={240}
              step={5}
              value={duracao}
              onChange={(e) => setDuracao(Number(e.target.value))}
              required
            />
          </label>
        </div>

        <p className={previa > 500 ? "estado estado-erro" : "hint"}>
          {previa > 0
            ? `Serão criadas até ${previa} vaga(s): ${diasNoPeriodo} dia(s) × ${porDia} horário(s).` +
              (previa > 500 ? " O limite é 500 por vez — reduza o período ou os dias." : "")
            : "Escolha período, dias e horário para ver quantas vagas serão criadas."}
        </p>

        {erro && <ErroMensagem mensagem={erro} />}
        <button type="submit" disabled={enviando || !profissionalId || !unidadeId || dias.length === 0 || previa === 0}>
          {enviando ? "Cadastrando..." : "Cadastrar vagas"}
        </button>
      </form>

      {resultado && (
        <Detalhes
          titulo={`${resultado.criadas} vaga(s) criada(s)`}
          itens={[
            { rotulo: "Profissional", valor: resultado.profissionalNome },
            { rotulo: "Especialidade", valor: resultado.especialidadeOuExame },
            { rotulo: "Primeira", valor: resultado.primeira ? formatarDataHora(resultado.primeira) : "—" },
            { rotulo: "Última", valor: resultado.ultima ? formatarDataHora(resultado.ultima) : "—" },
            { rotulo: "Já existiam (ignoradas)", valor: resultado.ignoradasDuplicadas },
          ]}
        />
      )}
    </>
  );
}

function NovoProfissional({
  especialidades,
  especialidadeInicial,
  onCancelar,
  onCadastrado,
}: {
  especialidades: string[];
  especialidadeInicial: string;
  onCancelar: () => void;
  onCadastrado: (profissional: ProfissionalResponse) => void;
}) {
  const [nome, setNome] = useState("");
  const [registroConselho, setRegistroConselho] = useState("");
  const [especialidade, setEspecialidade] = useState(especialidadeInicial);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar() {
    setEnviando(true);
    setErro(null);
    try {
      onCadastrado(await api.cadastrarProfissional({ nome, registroConselho, especialidade }));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao cadastrar o profissional");
    } finally {
      setEnviando(false);
    }
  }

  // Sem <form> aninhado (inválido em HTML): o botão salva direto.
  return (
    <div className="form-acao-agendamento">
      <strong>Novo especialista</strong>
      <label>
        Nome
        <input value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Dra. Ana Lima" />
      </label>
      <label>
        Registro do conselho
        <input value={registroConselho} onChange={(e) => setRegistroConselho(e.target.value)} placeholder="CRM-SP 123456" />
      </label>
      <label>
        Especialidade/exame
        <select value={especialidade} onChange={(e) => setEspecialidade(e.target.value)}>
          {especialidades.map((esp) => (
            <option key={esp} value={esp}>
              {esp}
            </option>
          ))}
        </select>
      </label>
      {erro && <ErroMensagem mensagem={erro} />}
      <div className="acoes-agendamento">
        <button type="button" onClick={salvar} disabled={enviando || !nome.trim() || !registroConselho.trim()}>
          {enviando ? "Salvando..." : "Salvar profissional"}
        </button>
        <button type="button" className="botao-secundario" onClick={onCancelar} disabled={enviando}>
          Cancelar
        </button>
      </div>
    </div>
  );
}
