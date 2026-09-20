import type { ReactNode } from "react";

export interface ItemDetalhe {
  rotulo: string;
  valor: ReactNode;
}

/** Mostra a resposta de uma operação como pares "rótulo: valor", em vez de JSON cru. */
export function Detalhes({ titulo, itens }: { titulo: string; itens: ItemDetalhe[] }) {
  return (
    <div className="detalhes">
      <p className="detalhes-titulo">{titulo}</p>
      <dl>
        {itens.map((item) => (
          <div key={item.rotulo} className="detalhes-linha">
            <dt>{item.rotulo}</dt>
            <dd>{item.valor}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
