import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { FilaItemResponse, StatusEncaminhamento } from "../lib/types";
import { BadgeStatus, BadgePresenca } from "../components/Badge";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { Detalhes } from "../components/Detalhes";
import { EncaminhamentoNavegavel } from "../components/DetalhesEncaminhamento";
import { Modal } from "../components/Modal";
import { RelatorioFila } from "../components/RelatorioFila";

type FiltroSituacao = "TODAS" | Extract<StatusEncaminhamento, "NA_FILA" | "AGENDADO">;

const OPCOES_SITUACAO: { valor: FiltroSituacao; rotulo: string }[] = [
  { valor: "TODAS", rotulo: "Todas" },
  { valor: "NA_FILA", rotulo: "Aguardando agendamento" },
  { valor: "AGENDADO", rotulo: "Agendado" },
];

const ehVermelho = (item: FilaItemResponse) => item.classificacaoRisco === "VERMELHO";

export function FilaSecretariaPage() {
  const [porEspecialidade, setPorEspecialidade] = useState<Record<string, FilaItemResponse[]> | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [filtroSituacao, setFiltroSituacao] = useState<FiltroSituacao>("TODAS");

  const [especialidadeAjuste, setEspecialidadeAjuste] = useState("");
  const [itemId, setItemId] = useState("");
  const [posicao, setPosicao] = useState("");
  const [justificativa, setJustificativa] = useState("");
  const [aplicandoOverride, setAplicandoOverride] = useState(false);
  const [erroOverride, setErroOverride] = useState<string | null>(null);

  const [itemDetalhe, setItemDetalhe] = useState<FilaItemResponse | null>(null);
  const [relatorioDe, setRelatorioDe] = useState<string | null>(null);

  const cardAjusteRef = useRef<HTMLDivElement>(null);
  const posicaoInputRef = useRef<HTMLInputElement>(null);

  async function carregarTodas() {
    setCarregando(true);
    setErro(null);
    try {
      setPorEspecialidade(await api.filaTodas());
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao consultar as filas");
    } finally {
      setCarregando(false);
    }
  }

  useEffect(() => {
    carregarTodas();
  }, []);

  const especialidades = porEspecialidade ? Object.keys(porEspecialidade) : [];
  const itensDaEspecialidadeAjuste = porEspecialidade?.[especialidadeAjuste] ?? [];

  // Mantém uma especialidade sempre selecionada assim que as filas carregam ou mudam.
  useEffect(() => {
    if (especialidades.length === 0) {
      setEspecialidadeAjuste("");
    } else if (!especialidades.includes(especialidadeAjuste)) {
      setEspecialidadeAjuste(especialidades[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [porEspecialidade]);

  // Ao trocar de especialidade, o item selecionado anteriormente não existe mais nessa fila.
  useEffect(() => {
    setItemId((atual) =>
      itensDaEspecialidadeAjuste.some((item) => item.itemId.toString() === atual)
        ? atual
        : itensDaEspecialidadeAjuste[0]?.itemId.toString() ?? "",
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [especialidadeAjuste, porEspecialidade]);

  // Preenche o "Ajuste manual" com o item escolhido na tabela; nova posição e justificativa seguem manuais.
  function selecionarItem(especialidade: string, item: FilaItemResponse) {
    setEspecialidadeAjuste(especialidade);
    setItemId(item.itemId.toString());
    setErroOverride(null);
    // Com as colunas lado a lado o card já está visível (fica fixo na rolagem) e isto não move a tela;
    // em tela estreita, onde os cards ficam empilhados, rola até o formulário.
    cardAjusteRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    posicaoInputRef.current?.focus({ preventScroll: true });
  }

  async function aplicarOverride(event: FormEvent) {
    event.preventDefault();
    setAplicandoOverride(true);
    setErroOverride(null);
    try {
      await api.overrideFila(Number(itemId), { posicao: Number(posicao), justificativa });
      setPosicao("");
      setJustificativa("");
      await carregarTodas();
    } catch (e) {
      setErroOverride(e instanceof ApiError ? e.message : "Falha ao aplicar o ajuste manual");
    } finally {
      setAplicandoOverride(false);
    }
  }

  // O filtro só muda o que aparece nas tabelas; o ajuste manual continua enxergando a fila inteira.
  const visiveisPorEspecialidade = especialidades
    .map((esp) => ({
      especialidade: esp,
      itens: (porEspecialidade?.[esp] ?? []).filter(
        (item) => filtroSituacao === "TODAS" || item.statusEncaminhamento === filtroSituacao,
      ),
    }))
    .filter((grupo) => grupo.itens.length > 0);
  const totalVisivel = visiveisPorEspecialidade.reduce((soma, grupo) => soma + grupo.itens.length, 0);
  const totalVermelhos = visiveisPorEspecialidade.reduce((soma, grupo) => soma + grupo.itens.filter(ehVermelho).length, 0);

  return (
    <div className="grid-2">
      <div className="card">
        <div className="card-encaminhamento-topo">
          <h2>Todas as filas</h2>
          <button onClick={carregarTodas} disabled={carregando}>
            {carregando ? "Atualizando..." : "Atualizar"}
          </button>
        </div>
        <label className="filtro-situacao">
          Situação
          <select value={filtroSituacao} onChange={(e) => setFiltroSituacao(e.target.value as FiltroSituacao)}>
            {OPCOES_SITUACAO.map((opcao) => (
              <option key={opcao.valor} value={opcao.valor}>
                {opcao.rotulo}
              </option>
            ))}
          </select>
        </label>
        <p className="hint">
          {totalVisivel > 0
            ? `${totalVisivel} paciente(s) em ${visiveisPorEspecialidade.length} especialidade(s)/exame(s).`
            : "Fila por especialidade/exame, já ordenada pela IA."}
        </p>
        {totalVermelhos > 0 && (
          <p className="alerta-vermelho">
            ⚠ {totalVermelhos} paciente(s) com risco <strong>vermelho (emergência)</strong> nesta lista.
          </p>
        )}
        {carregando && <Carregando label="Carregando as filas..." />}
        {erro && <ErroMensagem mensagem={erro} />}
        {porEspecialidade &&
          (visiveisPorEspecialidade.length > 0 ? (
            visiveisPorEspecialidade.map(({ especialidade, itens }) => (
              <FilaDaEspecialidade
                key={especialidade}
                especialidade={especialidade}
                itens={itens}
                itemSelecionadoId={especialidade === especialidadeAjuste ? itemId : ""}
                onSelecionar={(item) => selecionarItem(especialidade, item)}
                onAbrir={setItemDetalhe}
                onAbrirRelatorio={setRelatorioDe}
              />
            ))
          ) : (
            <SemDados
              label={
                especialidades.length > 0
                  ? "Nenhum paciente com essa situação na fila."
                  : "Nenhum paciente na fila no momento."
              }
            />
          ))}
      </div>

      <div className="card card-fixo" ref={cardAjusteRef}>
        <h2>Ajuste manual</h2>
        <p className="hint">Exige justificativa — fica registrado para auditoria (LGPD).</p>
        {especialidades.length === 0 ? (
          <SemDados label="Nenhum paciente na fila para ajustar no momento." />
        ) : (
          <form onSubmit={aplicarOverride}>
            <label>
              Especialidade/exame
              <select value={especialidadeAjuste} onChange={(e) => setEspecialidadeAjuste(e.target.value)}>
                {especialidades.map((esp) => (
                  <option key={esp} value={esp}>
                    {esp}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Item da fila
              <select value={itemId} onChange={(e) => setItemId(e.target.value)} required>
                {itensDaEspecialidadeAjuste.map((item) => (
                  <option key={item.itemId} value={item.itemId}>
                    Posição {item.posicao} — Encaminhamento #{item.encaminhamentoId} (score{" "}
                    {item.scoreAtual.toFixed(1)})
                  </option>
                ))}
              </select>
            </label>
            <label>
              Nova posição
              <input
                ref={posicaoInputRef}
                type="number"
                min={1}
                value={posicao}
                onChange={(e) => setPosicao(e.target.value)}
                required
              />
            </label>
            <label>
              Justificativa
              <textarea value={justificativa} onChange={(e) => setJustificativa(e.target.value)} required />
            </label>
            <button type="submit" disabled={aplicandoOverride || !itemId}>
              {aplicandoOverride ? "Aplicando..." : "Aplicar ajuste"}
            </button>
          </form>
        )}
        {erroOverride && <ErroMensagem mensagem={erroOverride} />}
      </div>

      <Modal
        titulo={relatorioDe ? `Análise IA — ${relatorioDe}` : ""}
        aberto={relatorioDe !== null}
        onFechar={() => setRelatorioDe(null)}
      >
        {relatorioDe && <RelatorioFila key={relatorioDe} especialidade={relatorioDe} onAlterado={carregarTodas} />}
      </Modal>

      <Modal
        titulo={itemDetalhe ? `Encaminhamento #${itemDetalhe.encaminhamentoId}` : ""}
        aberto={itemDetalhe !== null}
        onFechar={() => setItemDetalhe(null)}
      >
        {itemDetalhe && (
          <>
            <Detalhes
              titulo="Na fila"
              itens={[
                { rotulo: "Fila", valor: itemDetalhe.especialidadeOuExame },
                { rotulo: "Posição", valor: itemDetalhe.posicao },
                { rotulo: "Score", valor: itemDetalhe.scoreAtual.toFixed(1) },
                {
                  rotulo: "Ajuste manual",
                  valor: itemDetalhe.overrideManual
                    ? itemDetalhe.justificativaOverride ?? "Sim"
                    : "Não, ordem definida pela triagem",
                },
              ]}
            />
            <EncaminhamentoNavegavel
              key={itemDetalhe.encaminhamentoId}
              encaminhamentoId={itemDetalhe.encaminhamentoId}
              onAlterado={carregarTodas}
            />
          </>
        )}
      </Modal>
    </div>
  );
}

function FilaDaEspecialidade({
  especialidade,
  itens,
  itemSelecionadoId,
  onSelecionar,
  onAbrir,
  onAbrirRelatorio,
}: {
  especialidade: string;
  itens: FilaItemResponse[];
  itemSelecionadoId: string;
  onSelecionar: (item: FilaItemResponse) => void;
  onAbrir: (item: FilaItemResponse) => void;
  onAbrirRelatorio: (especialidade: string) => void;
}) {
  return (
    <div className="fila-especialidade">
      <h3 className="titulo-fila">
        {especialidade} <span className="hint">({itens.length})</span>
        {itens.some(ehVermelho) && (
          <span className="badge badge-risco-vermelho contagem-vermelho">
            ⚠ {itens.filter(ehVermelho).length} no vermelho
          </span>
        )}
        <button
          type="button"
          className="botao-secundario botao-relatorio"
          title="Sugestões sobre esta fila: quem subir, quem antecipar, esperas longas"
          onClick={() => onAbrirRelatorio(especialidade)}
        >
          Análise IA
        </button>
      </h3>
      <div className="tabela-rolagem">
        <table className="tabela tabela-compacta">
          <thead>
            <tr>
              <th>Posição</th>
              <th>Paciente</th>
              <th>
                <abbr title="Encaminhamento">Enca.</abbr>
              </th>
              <th>Situação</th>
              <th>Score</th>
              <th>Ajuste manual</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {itens.map((item) => (
              <tr
                key={item.itemId}
                className={[
                  "linha-clicavel",
                  item.itemId.toString() === itemSelecionadoId ? "linha-selecionada" : "",
                  ehVermelho(item) ? "linha-risco-vermelho" : "",
                ]
                  .filter(Boolean)
                  .join(" ")}
                title="Ver detalhes do encaminhamento"
                tabIndex={0}
                onClick={() => onAbrir(item)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") onAbrir(item);
                }}
              >
                <td>
                  {item.posicao}
                  {ehVermelho(item) && (
                    <span
                      className="selo-vermelho"
                      title="Risco vermelho (emergência)"
                      aria-label="Risco vermelho (emergência)"
                    >
                      ⚠
                    </span>
                  )}
                </td>
                <td>{item.pacienteNome ?? "—"}</td>
                <td>#{item.encaminhamentoId}</td>
                <td>
                  {item.statusEncaminhamento ? <BadgeStatus status={item.statusEncaminhamento} /> : "—"}
                  {item.statusEncaminhamento === "AGENDADO" && (
                    <>
                      {" "}
                      <BadgePresenca confirmadaEm={item.presencaConfirmadaEm} />
                    </>
                  )}
                </td>
                <td>{item.scoreAtual.toFixed(1)}</td>
                <td>{item.overrideManual ? item.justificativaOverride ?? "sim" : "—"}</td>
                <td>
                  <button
                    type="button"
                    className="caixa-selecao"
                    title="Selecionar"
                    aria-label={`Selecionar item ${item.itemId} da fila de ${especialidade}`}
                    aria-pressed={item.itemId.toString() === itemSelecionadoId}
                    onClick={(e) => {
                      // A caixinha só preenche o ajuste manual — não abre o detalhe da linha.
                      e.stopPropagation();
                      onSelecionar(item);
                    }}
                    onKeyDown={(e) => e.stopPropagation()}
                  >
                    {item.itemId.toString() === itemSelecionadoId ? "✓" : ""}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
