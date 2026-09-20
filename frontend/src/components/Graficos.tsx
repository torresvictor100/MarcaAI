import { useEffect, useRef, useState, type ReactNode } from "react";

/*
 * Gráficos do painel em SVG próprio (sem biblioteca — TECH-SPEC). Regras seguidas:
 * barras finas (<= 24px) com ponta arredondada de 4px e base reta; 2px de respiro entre marcas;
 * linhas de 2px; grade em traço fino e claro; texto nunca na cor da série; legenda para 2+ séries;
 * detalhe ao passar o mouse/focar (o valor também está na tabela) e "ver dados em tabela".
 * As cores vêm de variáveis CSS (--serie-*, --risco-*) validadas para daltonismo.
 */

export interface Serie {
  chave: string;
  rotulo: string;
  cor: string; // var(--...)
}

interface Dica {
  x: number;
  y: number;
  titulo: string;
  linhas: { rotulo: string; valor: string; cor?: string }[];
}

const ALTURA_BARRA = 18;
const RAIO = 4;

/** Largura disponível do contêiner, acompanhando redimensionamento. */
function useLargura<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [largura, setLargura] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observador = new ResizeObserver(([entrada]) => setLargura(Math.floor(entrada.contentRect.width)));
    observador.observe(el);
    return () => observador.disconnect();
  }, []);
  return { ref, largura };
}

/** Retângulo horizontal com a ponta direita arredondada e a base (esquerda) reta. */
function caminhoBarra(x: number, y: number, largura: number, altura: number, arredondar: boolean) {
  if (largura <= 0) return "";
  const r = arredondar ? Math.min(RAIO, largura / 2, altura / 2) : 0;
  return [
    `M${x},${y}`,
    `H${x + largura - r}`,
    r ? `Q${x + largura},${y} ${x + largura},${y + r}` : "",
    `V${y + altura - r}`,
    r ? `Q${x + largura},${y + altura} ${x + largura - r},${y + altura}` : "",
    `H${x}`,
    "Z",
  ].join(" ");
}

/** Marcas "redondas" para o eixo: 0, 5, 10… até cobrir o máximo. */
function escalaLimpa(maximo: number, marcas = 4) {
  if (maximo <= 0) return { topo: 1, passos: [0, 1] };
  const bruto = maximo / marcas;
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  const passo = [1, 2, 5, 10].map((m) => m * potencia).find((p) => p >= bruto) ?? bruto;
  const topo = Math.ceil(maximo / passo) * passo;
  const passos: number[] = [];
  for (let v = 0; v <= topo + 1e-9; v += passo) passos.push(Math.round(v * 100) / 100);
  return { topo, passos };
}

const formatarNumero = (n: number) => n.toLocaleString("pt-BR");

/** Abaixo desta largura, o nome da categoria vai em cima da barra (ao lado, nomes longos vazariam do cartão). */
const LARGURA_COMPACTA = 440;
const ALTURA_ROTULO_COMPACTO = 16;

function CaixaDica({ dica }: { dica: Dica | null }) {
  if (!dica) return null;
  return (
    <div className="grafico-dica" style={{ left: dica.x, top: dica.y }} role="status">
      <div className="grafico-dica-titulo">{dica.titulo}</div>
      {dica.linhas.map((l) => (
        <div key={l.rotulo} className="grafico-dica-linha">
          {l.cor && <span className="grafico-chave-linha" style={{ background: l.cor }} />}
          <strong>{l.valor}</strong> <span>{l.rotulo}</span>
        </div>
      ))}
    </div>
  );
}

export function Legenda({ series, forma = "barra" }: { series: Serie[]; forma?: "barra" | "linha" }) {
  return (
    <ul className="grafico-legenda">
      {series.map((s) => (
        <li key={s.chave}>
          <span className={forma === "linha" ? "grafico-chave-linha" : "grafico-chave-barra"} style={{ background: s.cor }} />
          {s.rotulo}
        </li>
      ))}
    </ul>
  );
}

/** Cartão de um gráfico: título, subtítulo, o gráfico e a alternância para ver os mesmos dados em tabela. */
export function CartaoGrafico({
  titulo,
  subtitulo,
  tabela,
  children,
}: {
  titulo: string;
  subtitulo?: string;
  tabela: { colunas: string[]; linhas: (string | number)[][] };
  children: ReactNode;
}) {
  const [verTabela, setVerTabela] = useState(false);
  return (
    <section className="cartao-grafico">
      <div className="cartao-grafico-topo">
        <div>
          <h3>{titulo}</h3>
          {subtitulo && <p className="hint">{subtitulo}</p>}
        </div>
        <button type="button" className="botao-voltar" onClick={() => setVerTabela((v) => !v)}>
          {verTabela ? "Ver gráfico" : "Ver dados em tabela"}
        </button>
      </div>
      {verTabela ? (
        <div className="tabela-rolagem">
          <table className="tabela tabela-compacta">
            <thead>
              <tr>
                {tabela.colunas.map((c) => (
                  <th key={c}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {tabela.linhas.map((linha, i) => (
                <tr key={i}>
                  {linha.map((valor, j) => (
                    <td key={j}>{typeof valor === "number" ? formatarNumero(valor) : valor}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        children
      )}
    </section>
  );
}

/**
 * Barras horizontais de uma série só (sem legenda: o título diz o que é). Valor na ponta da barra.
 * Itens com {@code destaque} usam a cor de destaque (ex.: bloqueados), sempre com o rótulo ao lado.
 */
export function GraficoBarras({
  itens,
  cor,
}: {
  itens: { rotulo: string; valor: number; cor?: string }[];
  cor: string;
}) {
  const { ref, largura } = useLargura<HTMLDivElement>();
  const [dica, setDica] = useState<Dica | null>(null);
  const compacto = largura < LARGURA_COMPACTA;
  const larguraRotulo = compacto ? 0 : Math.min(170, Math.max(110, largura * 0.35));
  const espacoValor = 44;
  const areaBarras = Math.max(0, largura - larguraRotulo - espacoValor);
  const maximo = Math.max(1, ...itens.map((i) => i.valor));
  const linha = compacto ? 30 + ALTURA_ROTULO_COMPACTO : 30;
  const deslocamento = compacto ? ALTURA_ROTULO_COMPACTO : 0;
  const altura = itens.length * linha;

  return (
    <div ref={ref} className="grafico" onMouseLeave={() => setDica(null)}>
      {largura > 0 && (
        <svg width={largura} height={altura} role="img" aria-label="Gráfico de barras horizontais">
          {itens.map((item, i) => {
            const y = i * linha + deslocamento + (linha - deslocamento - ALTURA_BARRA) / 2;
            const w = (item.valor / maximo) * areaBarras;
            const mostrar = () =>
              setDica({ x: larguraRotulo + w + 8, y: y - 6, titulo: item.rotulo, linhas: [{ rotulo: "", valor: formatarNumero(item.valor) }] });
            return (
              <g key={item.rotulo} tabIndex={0} onMouseMove={mostrar} onFocus={mostrar} onBlur={() => setDica(null)}>
                <rect x={0} y={i * linha} width={largura} height={linha} fill="transparent" />
                {compacto ? (
                  <text x={0} y={y - 5} className="grafico-texto">
                    {item.rotulo}
                  </text>
                ) : (
                  <text x={larguraRotulo - 8} y={y + ALTURA_BARRA / 2} className="grafico-texto" textAnchor="end" dominantBaseline="middle">
                    {item.rotulo}
                  </text>
                )}
                <line x1={larguraRotulo} x2={larguraRotulo} y1={y - 2} y2={y + ALTURA_BARRA + 2} className="grafico-eixo" />
                <path d={caminhoBarra(larguraRotulo, y, w, ALTURA_BARRA, true)} fill={item.cor ?? cor} />
                <text x={larguraRotulo + w + 6} y={y + ALTURA_BARRA / 2} className="grafico-valor" dominantBaseline="middle">
                  {formatarNumero(item.valor)}
                </text>
              </g>
            );
          })}
        </svg>
      )}
      <CaixaDica dica={dica} />
    </div>
  );
}

/** Barras horizontais empilhadas (uma por categoria), com 2px de respiro entre os pedaços e o total na ponta. */
export function GraficoBarrasEmpilhadas({
  categorias,
  series,
}: {
  categorias: { rotulo: string; valores: Record<string, number> }[];
  series: Serie[];
}) {
  const { ref, largura } = useLargura<HTMLDivElement>();
  const [dica, setDica] = useState<Dica | null>(null);
  const compacto = largura < LARGURA_COMPACTA;
  const larguraRotulo = compacto ? 0 : Math.min(150, Math.max(100, largura * 0.3));
  const espacoValor = 40;
  const areaBarras = Math.max(0, largura - larguraRotulo - espacoValor);
  const totais = categorias.map((c) => series.reduce((s, serie) => s + (c.valores[serie.chave] ?? 0), 0));
  const maximo = Math.max(1, ...totais);
  const deslocamento = compacto ? ALTURA_ROTULO_COMPACTO : 0;
  const linha = 34 + deslocamento;

  return (
    <>
      <Legenda series={series} />
      <div ref={ref} className="grafico" onMouseLeave={() => setDica(null)}>
        {largura > 0 && (
          <svg width={largura} height={categorias.length * linha} role="img" aria-label="Gráfico de barras empilhadas">
            {categorias.map((cat, i) => {
              const y = i * linha + deslocamento + (linha - deslocamento - ALTURA_BARRA) / 2;
              let x = larguraRotulo;
              const visiveis = series.filter((s) => (cat.valores[s.chave] ?? 0) > 0);
              return (
                <g key={cat.rotulo}>
                  {compacto ? (
                    <text x={0} y={y - 5} className="grafico-texto">
                      {cat.rotulo}
                    </text>
                  ) : (
                    <text x={larguraRotulo - 8} y={y + ALTURA_BARRA / 2} className="grafico-texto" textAnchor="end" dominantBaseline="middle">
                      {cat.rotulo}
                    </text>
                  )}
                  <line x1={larguraRotulo} x2={larguraRotulo} y1={y - 2} y2={y + ALTURA_BARRA + 2} className="grafico-eixo" />
                  {visiveis.map((serie, k) => {
                    const valor = cat.valores[serie.chave];
                    const larguraCheia = (valor / maximo) * areaBarras;
                    const ultimo = k === visiveis.length - 1;
                    const w = Math.max(1, larguraCheia - (ultimo ? 0 : 2)); // 2px de respiro na cor do fundo
                    const inicio = x;
                    x += larguraCheia;
                    const mostrar = () =>
                      setDica({
                        x: inicio + w / 2,
                        y: y - 8,
                        titulo: cat.rotulo,
                        linhas: [{ rotulo: serie.rotulo, valor: formatarNumero(valor), cor: serie.cor }],
                      });
                    return (
                      <path
                        key={serie.chave}
                        d={caminhoBarra(inicio, y, w, ALTURA_BARRA, ultimo)}
                        fill={serie.cor}
                        tabIndex={0}
                        className="grafico-marca"
                        onMouseMove={mostrar}
                        onFocus={mostrar}
                        onBlur={() => setDica(null)}
                      />
                    );
                  })}
                  <text x={x + 6} y={y + ALTURA_BARRA / 2} className="grafico-valor" dominantBaseline="middle">
                    {formatarNumero(totais[i])}
                  </text>
                </g>
              );
            })}
          </svg>
        )}
        <CaixaDica dica={dica} />
      </div>
    </>
  );
}

/** Barras horizontais agrupadas: para cada categoria, uma barra por série, com o valor na ponta. */
export function GraficoBarrasAgrupadas({
  categorias,
  series,
}: {
  categorias: { rotulo: string; valores: Record<string, number> }[];
  series: Serie[];
}) {
  const { ref, largura } = useLargura<HTMLDivElement>();
  const [dica, setDica] = useState<Dica | null>(null);
  const compacto = largura < LARGURA_COMPACTA;
  const larguraRotulo = compacto ? 0 : Math.min(150, Math.max(100, largura * 0.3));
  const espacoValor = 36;
  const areaBarras = Math.max(0, largura - larguraRotulo - espacoValor);
  const maximo = Math.max(1, ...categorias.flatMap((c) => series.map((s) => c.valores[s.chave] ?? 0)));
  const barra = 14;
  const deslocamento = compacto ? ALTURA_ROTULO_COMPACTO : 0;
  const grupo = series.length * (barra + 2) + 16 + deslocamento;

  return (
    <>
      <Legenda series={series} />
      <div ref={ref} className="grafico" onMouseLeave={() => setDica(null)}>
        {largura > 0 && (
          <svg width={largura} height={categorias.length * grupo} role="img" aria-label="Gráfico de barras agrupadas">
            {categorias.map((cat, i) => {
              const topo = i * grupo + 8 + deslocamento;
              const mostrar = () =>
                setDica({
                  x: larguraRotulo + 12,
                  y: topo - 4,
                  titulo: cat.rotulo,
                  linhas: series.map((s) => ({ rotulo: s.rotulo, valor: formatarNumero(cat.valores[s.chave] ?? 0), cor: s.cor })),
                });
              return (
                <g key={cat.rotulo} tabIndex={0} onMouseMove={mostrar} onFocus={mostrar} onBlur={() => setDica(null)}>
                  <rect x={0} y={i * grupo} width={largura} height={grupo} fill="transparent" />
                  {compacto ? (
                    <text x={0} y={topo - 6} className="grafico-texto">
                      {cat.rotulo}
                    </text>
                  ) : (
                    <text
                      x={larguraRotulo - 8}
                      y={topo + (series.length * (barra + 2)) / 2}
                      className="grafico-texto"
                      textAnchor="end"
                      dominantBaseline="middle"
                    >
                      {cat.rotulo}
                    </text>
                  )}
                  <line x1={larguraRotulo} x2={larguraRotulo} y1={topo - 3} y2={topo + series.length * (barra + 2) + 1} className="grafico-eixo" />
                  {series.map((s, k) => {
                    const valor = cat.valores[s.chave] ?? 0;
                    const y = topo + k * (barra + 2);
                    const w = (valor / maximo) * areaBarras;
                    return (
                      <g key={s.chave}>
                        <path d={caminhoBarra(larguraRotulo, y, w, barra, true)} fill={s.cor} />
                        <text x={larguraRotulo + w + 6} y={y + barra / 2} className="grafico-valor" dominantBaseline="middle">
                          {formatarNumero(valor)}
                        </text>
                      </g>
                    );
                  })}
                </g>
              );
            })}
          </svg>
        )}
        <CaixaDica dica={dica} />
      </div>
    </>
  );
}

/** Linhas ao longo do tempo (um eixo só), com linha-guia vertical que acha o dia mais próximo do ponteiro. */
export function GraficoLinhas({
  pontos,
  series,
  rotuloX,
}: {
  pontos: { x: string; valores: Record<string, number> }[];
  series: Serie[];
  rotuloX: (x: string) => string;
}) {
  const { ref, largura } = useLargura<HTMLDivElement>();
  const [indice, setIndice] = useState<number | null>(null);
  const altura = 220;
  const margem = { topo: 12, direita: 16, baixo: 26, esquerda: 34 };
  const larguraPlot = Math.max(0, largura - margem.esquerda - margem.direita);
  const alturaPlot = altura - margem.topo - margem.baixo;
  const { topo, passos } = escalaLimpa(Math.max(0, ...pontos.flatMap((p) => series.map((s) => p.valores[s.chave] ?? 0))));
  const xDe = (i: number) => margem.esquerda + (pontos.length <= 1 ? 0 : (i / (pontos.length - 1)) * larguraPlot);
  const yDe = (v: number) => margem.topo + alturaPlot - (v / topo) * alturaPlot;
  const intervaloRotulos = Math.max(1, Math.ceil(pontos.length / Math.max(2, Math.floor(larguraPlot / 70))));

  function aoMover(evento: React.PointerEvent<SVGSVGElement>) {
    const caixa = evento.currentTarget.getBoundingClientRect();
    const x = evento.clientX - caixa.left - margem.esquerda;
    const i = Math.round((x / Math.max(1, larguraPlot)) * (pontos.length - 1));
    setIndice(Math.min(pontos.length - 1, Math.max(0, i)));
  }

  const atual = indice !== null ? pontos[indice] : null;

  return (
    <>
      {/* Uma série só dispensa legenda: o título do cartão já diz o que é. */}
      {series.length > 1 && <Legenda series={series} forma="linha" />}
      <div ref={ref} className="grafico">
        {largura > 0 && (
          <svg
            width={largura}
            height={altura}
            role="img"
            aria-label="Gráfico de linhas"
            onPointerMove={aoMover}
            onPointerLeave={() => setIndice(null)}
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "ArrowRight") setIndice((i) => Math.min(pontos.length - 1, (i ?? -1) + 1));
              if (e.key === "ArrowLeft") setIndice((i) => Math.max(0, (i ?? pontos.length) - 1));
            }}
            onBlur={() => setIndice(null)}
          >
            {passos.map((v) => (
              <g key={v}>
                <line x1={margem.esquerda} x2={margem.esquerda + larguraPlot} y1={yDe(v)} y2={yDe(v)} className="grafico-grade" />
                <text x={margem.esquerda - 6} y={yDe(v)} className="grafico-texto-eixo" textAnchor="end" dominantBaseline="middle">
                  {formatarNumero(v)}
                </text>
              </g>
            ))}
            {pontos.map((p, i) =>
              i % intervaloRotulos === 0 || i === pontos.length - 1 ? (
                <text key={p.x} x={xDe(i)} y={altura - 8} className="grafico-texto-eixo" textAnchor="middle">
                  {rotuloX(p.x)}
                </text>
              ) : null,
            )}
            {series.map((s) => (
              <polyline
                key={s.chave}
                points={pontos.map((p, i) => `${xDe(i)},${yDe(p.valores[s.chave] ?? 0)}`).join(" ")}
                fill="none"
                stroke={s.cor}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
              />
            ))}
            {atual && indice !== null && (
              <g>
                <line x1={xDe(indice)} x2={xDe(indice)} y1={margem.topo} y2={margem.topo + alturaPlot} className="grafico-guia" />
                {series.map((s) => (
                  <circle
                    key={s.chave}
                    cx={xDe(indice)}
                    cy={yDe(atual.valores[s.chave] ?? 0)}
                    r={4}
                    fill={s.cor}
                    stroke="var(--cor-superficie)"
                    strokeWidth={2}
                  />
                ))}
              </g>
            )}
          </svg>
        )}
        {atual && indice !== null && (
          <CaixaDica
            dica={{
              x: Math.min(xDe(indice) + 10, Math.max(0, largura - 170)),
              y: margem.topo,
              titulo: rotuloX(atual.x),
              linhas: series.map((s) => ({ rotulo: s.rotulo, valor: formatarNumero(atual.valores[s.chave] ?? 0), cor: s.cor })),
            }}
          />
        )}
      </div>
    </>
  );
}
