import { useEffect, useState } from "react";
import { api, ApiError } from "../lib/api";
import type { EncaminhamentoResponse } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { EncaminhamentoCard } from "../components/EncaminhamentoCard";
import { useAuth } from "../auth/AuthContext";

/** Os encaminhamentos do paciente logado — o paciente vem do login, nada a digitar. */
export function MeusEncaminhamentosPage() {
  const { sessao } = useAuth();
  const pacienteId = sessao?.pacienteId ?? null;
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [encaminhamentos, setEncaminhamentos] = useState<EncaminhamentoResponse[]>([]);

  useEffect(() => {
    if (!pacienteId) return;
    let ativo = true;
    api
      .meusEncaminhamentos(pacienteId)
      .then((lista) => ativo && setEncaminhamentos([...lista].sort((a, b) => b.id - a.id)))
      .catch((e) => ativo && setErro(e instanceof ApiError ? e.message : "Não foi possível carregar seus encaminhamentos"))
      .finally(() => ativo && setCarregando(false));
    return () => {
      ativo = false;
    };
  }, [pacienteId]);

  const primeiroNome = sessao?.nome.split(" ")[0] ?? "";

  return (
    <div className="card">
      <h2>Olá, {primeiroNome}!</h2>
      <p className="hint">
        Aqui você acompanha cada pedido que seu médico da UBS fez para você: em que etapa está, quando e onde vai ser
        atendido(a) e o resultado depois do atendimento.
      </p>
      {!pacienteId && (
        <p className="estado estado-erro">
          Seu login ainda não está ligado ao seu cadastro de paciente. Procure a sua UBS.
        </p>
      )}
      {pacienteId && carregando && <Carregando label="Carregando seus encaminhamentos..." />}
      {erro && <ErroMensagem mensagem={erro} />}
      {pacienteId && !carregando && !erro && encaminhamentos.length === 0 && (
        <SemDados label="Você ainda não tem encaminhamentos. Quando seu médico fizer um, ele aparece aqui." />
      )}
      {encaminhamentos.length > 0 && (
        <div className="lista-cards-paciente">
          {encaminhamentos.map((e) => (
            <EncaminhamentoCard key={e.id} encaminhamento={e} />
          ))}
        </div>
      )}
    </div>
  );
}
