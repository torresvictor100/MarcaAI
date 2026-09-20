export function Carregando({ label = "Carregando..." }: { label?: string }) {
  return <p className="estado estado-carregando">{label}</p>;
}

export function ErroMensagem({ mensagem }: { mensagem: string }) {
  return <p className="estado estado-erro">{mensagem}</p>;
}

export function SemDados({ label = "Nenhum registro encontrado." }: { label?: string }) {
  return <p className="estado estado-vazio">{label}</p>;
}
