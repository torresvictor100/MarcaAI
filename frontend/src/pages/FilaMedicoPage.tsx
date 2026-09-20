import { useEffect, useState, type KeyboardEvent } from "react";
import { api, ApiError } from "../lib/api";
import type { FilaItemResponse } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { Detalhes } from "../components/Detalhes";
import { PainelEncaminhamento } from "../components/DetalhesEncaminhamento";
import { Modal } from "../components/Modal";
import { useAuth } from "../auth/AuthContext";

export function FilaMedicoPage() {
  const { sessao } = useAuth();
  const profissionalId = sessao?.profissionalId ?? null;
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [itens, setItens] = useState<FilaItemResponse[] | null>(null);
  const [itemDetalhe, setItemDetalhe] = useState<FilaItemResponse | null>(null);

  async function carregar(id: number) {
    setCarregando(true);
    setErro(null);
    try {
      setItens(await api.filaPorMedico(id));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao consultar a fila");
    } finally {
      setCarregando(false);
    }
  }

  // O médico só vê a própria fila: o profissionalId vem do login (e o backend recusa o de outro médico).
  useEffect(() => {
    if (profissionalId) carregar(profissionalId);
  }, [profissionalId]);

  return (
    <div className="card">
      <h2>Fila dos pacientes que encaminhei</h2>
      {!profissionalId && (
        <p className="estado estado-erro">
          Seu usuário de login não está vinculado a um cadastro de profissional — fale com a secretaria.
        </p>
      )}
      {carregando && <Carregando label="Carregando sua fila..." />}
      {erro && <ErroMensagem mensagem={erro} />}
      {itens &&
        (itens.length > 0 ? (
          <>
            <p className="hint">Clique num encaminhamento para ver todos os dados dele.</p>
            <TabelaFila itens={itens} onAbrir={setItemDetalhe} />
          </>
        ) : (
          <SemDados label="Nenhum paciente seu está na fila no momento." />
        ))}

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
            <PainelEncaminhamento
              key={itemDetalhe.encaminhamentoId}
              encaminhamentoId={itemDetalhe.encaminhamentoId}
              podeAnexarDocumento
              onAlterado={() => profissionalId && carregar(profissionalId)}
            />
          </>
        )}
      </Modal>
    </div>
  );
}

const ITENS_POR_PAGINA = 10;

export function TabelaFila({
  itens,
  onAbrir,
}: {
  itens: FilaItemResponse[];
  /** Quando informado, cada linha vira clicável e abre o detalhe do encaminhamento. */
  onAbrir?: (item: FilaItemResponse) => void;
}) {
  const [pagina, setPagina] = useState(1);

  // Volta pra 1ª página sempre que a fila é recarregada (ex.: nova consulta).
  useEffect(() => {
    setPagina(1);
  }, [itens]);

  const totalPaginas = Math.max(1, Math.ceil(itens.length / ITENS_POR_PAGINA));
  const paginaAtual = Math.min(pagina, totalPaginas);
  const inicio = (paginaAtual - 1) * ITENS_POR_PAGINA;
  const itensDaPagina = itens.slice(inicio, inicio + ITENS_POR_PAGINA);

  return (
    <>
      <table className="tabela">
        <thead>
          <tr>
            <th>Posição</th>
            <th>
              <abbr title="Encaminhamento">Enca.</abbr>
            </th>
            <th>Especialidade/exame</th>
            <th>Score</th>
            <th>Ajuste manual</th>
          </tr>
        </thead>
        <tbody>
          {itensDaPagina.map((item) => (
            <tr
              key={item.itemId}
              {...(onAbrir && {
                className: "linha-clicavel",
                title: "Ver detalhes do encaminhamento",
                tabIndex: 0,
                onClick: () => onAbrir(item),
                onKeyDown: (e: KeyboardEvent) => {
                  if (e.key === "Enter") onAbrir(item);
                },
              })}
            >
              <td>{item.posicao}</td>
              <td>{item.encaminhamentoId}</td>
              <td>{item.especialidadeOuExame}</td>
              <td>{item.scoreAtual.toFixed(1)}</td>
              <td>{item.overrideManual ? item.justificativaOverride ?? "sim" : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {totalPaginas > 1 && (
        <div className="paginacao">
          <button type="button" onClick={() => setPagina((p) => Math.max(1, p - 1))} disabled={paginaAtual === 1}>
            Anterior
          </button>
          <span className="hint">
            Página {paginaAtual} de {totalPaginas} · {itens.length} no total
          </span>
          <button
            type="button"
            onClick={() => setPagina((p) => Math.min(totalPaginas, p + 1))}
            disabled={paginaAtual === totalPaginas}
          >
            Próxima
          </button>
        </div>
      )}
    </>
  );
}
