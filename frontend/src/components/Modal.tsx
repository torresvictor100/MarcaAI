import { useEffect, useRef, type ReactNode } from "react";

/** Janela sobre a tela, via <dialog> nativo: fecha no X, no Esc ou clicando fora. */
export function Modal({
  titulo,
  aberto,
  onFechar,
  children,
}: {
  titulo: string;
  aberto: boolean;
  onFechar: () => void;
  children: ReactNode;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (aberto && !dialog.open) dialog.showModal();
    else if (!aberto && dialog.open) dialog.close();
  }, [aberto]);

  return (
    <dialog
      ref={dialogRef}
      className="modal"
      onClose={onFechar}
      // O <dialog> não tem padding e o conteúdo o preenche, então o clique só cai nele mesmo no fundo escurecido.
      onClick={(e) => {
        if (e.target === dialogRef.current) onFechar();
      }}
    >
      {aberto && (
        <div className="modal-conteudo">
          <div className="modal-topo">
            <h2>{titulo}</h2>
            <button type="button" className="modal-fechar" aria-label="Fechar" onClick={onFechar}>
              ×
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  );
}
