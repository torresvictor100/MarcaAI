import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import type { PainelResumoResponse } from "../lib/types";
import { Carregando, ErroMensagem } from "./AsyncState";

/** Métricas gerais da rede (SECRETARIA/ADMIN) — usado no Painel completo e no dashboard da Home. */
export function PainelResumo() {
  const [resumo, setResumo] = useState<PainelResumoResponse | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .painelResumo()
      .then((r) => {
        if (ativo) setResumo(r);
      })
      .catch((e) => {
        if (ativo) setErro(e instanceof ApiError ? e.message : "Falha ao carregar o painel");
      })
      .finally(() => {
        if (ativo) setCarregando(false);
      });
    return () => {
      ativo = false;
    };
  }, []);

  if (carregando) return <Carregando label="Carregando painel..." />;
  if (erro) return <ErroMensagem mensagem={erro} />;
  if (!resumo) return null;

  return (
    <div className="metricas">
      <Metrica label="Atendidos hoje" valor={resumo.atendidosHoje} />
      <Metrica label="Encaminhamentos" valor={resumo.encaminhamentos} />
      <Metrica label="Exames solicitados" valor={resumo.examesSolicitados} />
      <Metrica label="Consultas agendadas" valor={resumo.consultasAgendadas} />
      <Metrica label="Vagas disponíveis" valor={resumo.vagasDisponiveis} />
    </div>
  );
}

function Metrica({ label, valor }: { label: string; valor: number }) {
  return (
    <div className="metrica">
      <span className="metrica-valor">{valor}</span>
      <span className="metrica-label">{label}</span>
    </div>
  );
}
