import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import { formatarDataHora } from "../lib/formatacao";
import type { RelatorioFilaResponse, SugestaoFila, TipoSugestao } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "./AsyncState";
import { EncaminhamentoNavegavel } from "./DetalhesEncaminhamento";

const ROTULOS_TIPO: Record<TipoSugestao, string> = {
  SUBIR_NA_FILA: "Subir na fila",
  ANTECIPAR: "Antecipar",
  ESPERA_LONGA: "Espera longa",
  URGENTE_SEM_VAGA: "Urgente sem vaga",
  FALTA_DE_VAGAS: "Falta de vagas",
};

type Estado =
  | { tipo: "carregando" }
  | { tipo: "sem-relatorio" }
  | { tipo: "ok"; relatorio: RelatorioFilaResponse }
  | { tipo: "erro"; mensagem: string };

/**
 * Relatório de sugestões de uma fila (ADR-008): mostra o último gerado e permite gerar outro. As sugestões
 * vêm das regras de priorização; a IA só escreve o texto. Clicar numa sugestão abre o encaminhamento, onde a
 * secretaria decide (ajuste manual, antecipar, marcar).
 */
export function RelatorioFila({ especialidade, onAlterado }: { especialidade: string; onAlterado: () => void }) {
  const [estado, setEstado] = useState<Estado>({ tipo: "carregando" });
  const [gerando, setGerando] = useState(false);
  const [erroGerar, setErroGerar] = useState<string | null>(null);
  const [encaminhamentoAberto, setEncaminhamentoAberto] = useState<number | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .relatorioFila(especialidade)
      .then((relatorio) => ativo && setEstado({ tipo: "ok", relatorio }))
      .catch((e) => {
        if (!ativo) return;
        if (e instanceof ApiError && e.status === 404) setEstado({ tipo: "sem-relatorio" });
        else setEstado({ tipo: "erro", mensagem: e instanceof ApiError ? e.message : "Falha ao carregar o relatório" });
      });
    return () => {
      ativo = false;
    };
  }, [especialidade]);

  async function gerar() {
    setGerando(true);
    setErroGerar(null);
    try {
      setEstado({ tipo: "ok", relatorio: await api.gerarRelatorioFila(especialidade) });
    } catch (e) {
      setErroGerar(e instanceof ApiError ? e.message : "Falha ao gerar o relatório");
    } finally {
      setGerando(false);
    }
  }

  if (encaminhamentoAberto !== null) {
    return (
      <>
        <button type="button" className="botao-voltar" onClick={() => setEncaminhamentoAberto(null)}>
          ← Voltar ao relatório
        </button>
        <h3>Encaminhamento #{encaminhamentoAberto}</h3>
        <EncaminhamentoNavegavel
          key={encaminhamentoAberto}
          encaminhamentoId={encaminhamentoAberto}
          onAlterado={onAlterado}
        />
      </>
    );
  }

  return (
    <>
      <div className="acoes-agendamento">
        <button type="button" onClick={gerar} disabled={gerando}>
          {gerando ? "Analisando a fila..." : estado.tipo === "ok" ? "Gerar novo relatório" : "Gerar relatório"}
        </button>
      </div>
      {gerando && <Carregando label="Analisando a fila e redigindo o relatório. Isso pode levar alguns segundos..." />}
      {erroGerar && <ErroMensagem mensagem={erroGerar} />}

      {estado.tipo === "carregando" && <Carregando label="Buscando o último relatório..." />}
      {estado.tipo === "erro" && <ErroMensagem mensagem={estado.mensagem} />}
      {estado.tipo === "sem-relatorio" && (
        <SemDados label="Nenhum relatório gerado para esta fila ainda. Clique em Gerar relatório." />
      )}
      {estado.tipo === "ok" && (
        <ConteudoRelatorio relatorio={estado.relatorio} onAbrir={setEncaminhamentoAberto} />
      )}
    </>
  );
}

function ConteudoRelatorio({
  relatorio,
  onAbrir,
}: {
  relatorio: RelatorioFilaResponse;
  onAbrir: (encaminhamentoId: number) => void;
}) {
  return (
    <>
      <p className="hint">
        Gerado em {formatarDataHora(relatorio.geradoEm)} por {relatorio.geradoPor} · {relatorio.totalNaFila}{" "}
        paciente(s) analisados · texto{" "}
        {relatorio.origemTexto === "IA" ? "redigido pela IA" : "padrão (IA indisponível no momento)"}
      </p>
      <div className="relatorio-texto">{relatorio.texto}</div>

      <h3>Sugestões ({relatorio.sugestoes.length})</h3>
      <p className="hint">
        Calculadas pelas regras de priorização. Nada é aplicado sozinho: abra o encaminhamento para decidir.
      </p>
      {relatorio.sugestoes.length === 0 ? (
        <SemDados label="Nenhum ponto de atenção nesta fila." />
      ) : (
        <ul className="lista-sugestoes">
          {relatorio.sugestoes.map((s, indice) => (
            <ItemSugestao key={indice} sugestao={s} onAbrir={onAbrir} />
          ))}
        </ul>
      )}
    </>
  );
}

function ItemSugestao({ sugestao, onAbrir }: { sugestao: SugestaoFila; onAbrir: (id: number) => void }) {
  const clicavel = sugestao.encaminhamentoId !== null;
  const abrir = () => sugestao.encaminhamentoId !== null && onAbrir(sugestao.encaminhamentoId);
  return (
    <li
      className={`sugestao sugestao-${sugestao.prioridade.toLowerCase()}${clicavel ? " sugestao-clicavel" : ""}`}
      onClick={clicavel ? abrir : undefined}
      onKeyDown={(e) => {
        if (clicavel && e.key === "Enter") abrir();
      }}
      tabIndex={clicavel ? 0 : undefined}
      title={clicavel ? "Abrir o encaminhamento" : undefined}
    >
      <div className="sugestao-topo">
        <span className={`badge badge-prioridade-${sugestao.prioridade.toLowerCase()}`}>
          {sugestao.prioridade === "ALTA" ? "Alta" : "Média"}
        </span>
        <strong>{ROTULOS_TIPO[sugestao.tipo]}</strong>
        {sugestao.encaminhamentoId !== null && (
          <span className="hint">
            {sugestao.pacienteNome ?? "Paciente"} · <abbr title="Encaminhamento">Enca.</abbr> #
            {sugestao.encaminhamentoId}
            {sugestao.posicao !== null && ` · posição ${sugestao.posicao}`}
          </span>
        )}
      </div>
      <p>{sugestao.motivo}</p>
    </li>
  );
}
