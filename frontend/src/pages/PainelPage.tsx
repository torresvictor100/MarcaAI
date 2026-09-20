import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import type { ClassificacaoRisco, PainelIndicadoresResponse, StatusEncaminhamento } from "../lib/types";
import { Carregando, ErroMensagem } from "../components/AsyncState";
import { PainelResumo } from "../components/PainelResumo";
import {
  CartaoGrafico,
  GraficoBarras,
  GraficoBarrasAgrupadas,
  GraficoBarrasEmpilhadas,
  GraficoLinhas,
  type Serie,
} from "../components/Graficos";

const ROTULOS_SITUACAO: Record<StatusEncaminhamento, string> = {
  AGUARDANDO_DOCUMENTOS: "Aguardando documentos",
  EM_ANALISE: "Em análise da IA",
  BLOQUEADO_REVISAO: "Bloqueado p/ revisão",
  NA_FILA: "Na fila",
  AGENDADO: "Agendado",
  REALIZADO: "Atendido",
  CANCELADO: "Cancelado",
};

// Ordem do protocolo de Manchester, do menor para o maior risco (cores validadas lado a lado).
const SERIES_RISCO: Serie[] = [
  { chave: "AZUL", rotulo: "Azul", cor: "var(--risco-azul)" },
  { chave: "VERDE", rotulo: "Verde", cor: "var(--risco-verde)" },
  { chave: "AMARELO", rotulo: "Amarelo", cor: "var(--risco-amarelo)" },
  { chave: "LARANJA", rotulo: "Laranja", cor: "var(--risco-laranja)" },
  { chave: "VERMELHO", rotulo: "Vermelho", cor: "var(--risco-vermelho)" },
];

const SERIES_DEMANDA: Serie[] = [
  { chave: "aguardando", rotulo: "Aguardando agendamento", cor: "var(--serie-2)" },
  { chave: "vagas", rotulo: "Vagas livres", cor: "var(--serie-1)" },
];

// Só encaminhamentos no gráfico: na demonstração cada atendimento gera exatamente um encaminhamento, e as
// duas linhas ficariam idênticas (uma esconderia a outra). Os atendimentos seguem na tabela do cartão.
const SERIES_MOVIMENTO: Serie[] = [{ chave: "encaminhamentos", rotulo: "Encaminhamentos", cor: "var(--serie-1)" }];

const diaMes = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
const umDecimal = (n: number) => n.toLocaleString("pt-BR", { maximumFractionDigits: 1 });

export function PainelPage() {
  const [indicadores, setIndicadores] = useState<PainelIndicadoresResponse | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    let ativo = true;
    api
      .painelIndicadores()
      .then((d) => {
        if (ativo) {
          setIndicadores(d);
          setErro(null);
        }
      })
      .catch((e) => {
        if (ativo) setErro(e instanceof ApiError ? e.message : "Falha ao carregar os indicadores");
      })
      .finally(() => {
        if (ativo) setCarregando(false);
      });
    return () => {
      ativo = false;
    };
  }, [versao]);

  function atualizar() {
    setCarregando(true);
    setVersao((v) => v + 1);
  }

  const n = indicadores?.numeros;

  return (
    <div className="card painel">
      <div className="card-encaminhamento-topo">
        <h2>Painel da Secretaria</h2>
        <button type="button" onClick={atualizar} disabled={carregando}>
          {carregando ? "Atualizando..." : "Atualizar"}
        </button>
      </div>
      <PainelResumo key={versao} />

      {erro && <ErroMensagem mensagem={erro} />}
      {!indicadores && carregando && <Carregando label="Carregando indicadores..." />}

      {indicadores && n && (
        // Mantém o desenho anterior (esmaecido) enquanto recarrega: sem pulo de layout.
        <div style={{ opacity: carregando ? 0.6 : 1 }}>
          <div className="metricas">
            <Numero rotulo="Na fila agora" valor={String(n.naFila)} />
            <Numero
              rotulo="Risco vermelho aguardando"
              valor={String(n.vermelhoAguardando)}
              alerta={n.vermelhoAguardando > 0}
            />
            <Numero
              rotulo="Espera média (dias)"
              valor={n.esperaMediaDias === null ? "—" : umDecimal(n.esperaMediaDias)}
              dica="Dias desde o atendimento, de quem aguarda agendamento"
            />
            <Numero
              rotulo="Ocupação das vagas"
              valor={n.ocupacaoVagasPercentual === null ? "—" : `${umDecimal(n.ocupacaoVagasPercentual)}%`}
              dica="Vagas futuras já ocupadas"
            />
            <Numero rotulo="Bloqueados p/ revisão" valor={String(n.bloqueadosRevisao)} />
          </div>

          <div className="grade-graficos">
            <CartaoGrafico
              titulo="Situação dos encaminhamentos"
              subtitulo="Do envio dos documentos ao atendimento"
              tabela={{
                colunas: ["Situação", "Encaminhamentos"],
                linhas: indicadores.situacaoEncaminhamentos.map((s) => [ROTULOS_SITUACAO[s.status], s.quantidade]),
              }}
            >
              <GraficoBarras
                cor="var(--serie-1)"
                itens={indicadores.situacaoEncaminhamentos.map((s) => ({
                  rotulo: ROTULOS_SITUACAO[s.status],
                  valor: s.quantidade,
                }))}
              />
            </CartaoGrafico>

            <CartaoGrafico
              titulo="Fila por especialidade e risco"
              subtitulo="Pacientes na fila (aguardando e agendados), pela classificação de risco"
              tabela={{
                colunas: ["Especialidade/exame", ...SERIES_RISCO.map((s) => s.rotulo), "Total"],
                linhas: indicadores.filaPorRisco.map((f) => [
                  f.especialidadeOuExame,
                  ...SERIES_RISCO.map((s) => f.porRisco[s.chave as ClassificacaoRisco] ?? 0),
                  f.total,
                ]),
              }}
            >
              <GraficoBarrasEmpilhadas
                series={SERIES_RISCO}
                categorias={indicadores.filaPorRisco.map((f) => ({ rotulo: f.especialidadeOuExame, valores: f.porRisco }))}
              />
            </CartaoGrafico>

            <CartaoGrafico
              titulo="Demanda × vagas livres"
              subtitulo="Onde há mais gente esperando do que vagas abertas"
              tabela={{
                colunas: ["Especialidade/exame", "Aguardando agendamento", "Vagas livres"],
                linhas: indicadores.demandaCapacidade.map((d) => [d.especialidadeOuExame, d.aguardandoAgendamento, d.vagasLivres]),
              }}
            >
              <GraficoBarrasAgrupadas
                series={SERIES_DEMANDA}
                categorias={indicadores.demandaCapacidade.map((d) => ({
                  rotulo: d.especialidadeOuExame,
                  valores: { aguardando: d.aguardandoAgendamento, vagas: d.vagasLivres },
                }))}
              />
            </CartaoGrafico>

            <CartaoGrafico
              titulo="Encaminhamentos por dia (últimos 30 dias)"
              subtitulo="Contados no dia do atendimento que os gerou; a tabela traz também os atendimentos"
              tabela={{
                colunas: ["Dia", "Atendimentos", "Encaminhamentos"],
                linhas: indicadores.movimento30Dias.map((m) => [diaMes(m.data), m.atendimentos, m.encaminhamentos]),
              }}
            >
              <GraficoLinhas
                series={SERIES_MOVIMENTO}
                rotuloX={diaMes}
                pontos={indicadores.movimento30Dias.map((m) => ({
                  x: m.data,
                  valores: { encaminhamentos: m.encaminhamentos },
                }))}
              />
            </CartaoGrafico>
          </div>
        </div>
      )}
    </div>
  );
}

function Numero({ rotulo, valor, alerta, dica }: { rotulo: string; valor: string; alerta?: boolean; dica?: string }) {
  return (
    <div className={alerta ? "metrica metrica-alerta" : "metrica"} title={dica}>
      <span className="metrica-valor">
        {alerta && <span aria-hidden="true">⚠ </span>}
        {valor}
      </span>
      <span className="metrica-label">{rotulo}</span>
    </div>
  );
}
