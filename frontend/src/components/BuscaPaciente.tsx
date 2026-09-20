import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import type { PacienteResumoResponse } from "../lib/types";

const TAMANHO_MINIMO = 2;
const ESPERA_DIGITACAO_MS = 300;

interface Props {
  selecionado: PacienteResumoResponse | null;
  onSelecionar: (paciente: PacienteResumoResponse | null) => void;
}

/** Campo de busca de paciente pelo nome, com sugestões enquanto digita (GET /pacientes?nome=). */
export function BuscaPaciente({ selecionado, onSelecionar }: Props) {
  const [termo, setTermo] = useState("");
  const [resultados, setResultados] = useState<PacienteResumoResponse[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const termoValido = termo.trim().length >= TAMANHO_MINIMO;

  useEffect(() => {
    if (selecionado || !termoValido) return;
    let cancelado = false;
    const timer = setTimeout(async () => {
      setBuscando(true);
      setErro(null);
      try {
        const encontrados = await api.buscarPacientes(termo.trim());
        if (!cancelado) setResultados(encontrados);
      } catch (e) {
        if (!cancelado) setErro(e instanceof ApiError ? e.message : "Falha ao buscar pacientes");
      } finally {
        if (!cancelado) setBuscando(false);
      }
    }, ESPERA_DIGITACAO_MS);
    return () => {
      cancelado = true;
      clearTimeout(timer);
    };
  }, [termo, termoValido, selecionado]);

  if (selecionado) {
    return (
      <div className="paciente-selecionado">
        <span>
          <strong>{selecionado.nome}</strong> <span className="hint">CPF {selecionado.cpfMascarado}</span>
        </span>
        <button type="button" className="botao-link" onClick={() => onSelecionar(null)}>
          trocar
        </button>
      </div>
    );
  }

  return (
    <div className="busca-paciente">
      <input
        value={termo}
        onChange={(e) => setTermo(e.target.value)}
        placeholder="Digite o nome do paciente"
        autoComplete="off"
        required
      />
      {termoValido && (
        <ul className="resultados-busca" role="listbox">
          {buscando && <li className="hint">Buscando...</li>}
          {!buscando && erro && <li className="erro-busca">{erro}</li>}
          {!buscando && !erro && resultados.length === 0 && <li className="hint">Nenhum paciente encontrado</li>}
          {!buscando &&
            !erro &&
            resultados.map((p) => (
              <li key={p.id} role="option" aria-selected={false}>
                <button
                  type="button"
                  onClick={() => {
                    onSelecionar(p);
                    setTermo("");
                    setResultados([]);
                  }}
                >
                  <span>{p.nome}</span>
                  <span className="hint">CPF {p.cpfMascarado}</span>
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
