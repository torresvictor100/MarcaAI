import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../lib/api";
import type { EncaminhamentoResponse, FilaItemResponse } from "../lib/types";
import { Carregando, ErroMensagem, SemDados } from "../components/AsyncState";
import { EncaminhamentoCard } from "../components/EncaminhamentoCard";
import { PainelResumo } from "../components/PainelResumo";
import { TabelaFila } from "./FilaMedicoPage";

export function HomePage() {
  const { sessao } = useAuth();
  if (!sessao) return null;

  switch (sessao.papel) {
    case "PACIENTE":
      return <DashboardPaciente pacienteId={sessao.pacienteId} nome={sessao.nome} />;
    case "MEDICO_UBS":
      return <DashboardMedico profissionalId={sessao.profissionalId} nome={sessao.nome} />;
    case "SECRETARIA":
    case "ADMIN":
      return <DashboardSecretaria nome={sessao.nome} />;
    default:
      return <DashboardGenerico nome={sessao.nome} papel={sessao.papel} />;
  }
}

function DashboardPaciente({ pacienteId, nome }: { pacienteId: number | null; nome: string }) {
  const [encaminhamentos, setEncaminhamentos] = useState<EncaminhamentoResponse[] | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!pacienteId) {
      setCarregando(false);
      return;
    }
    let ativo = true;
    api
      .meusEncaminhamentos(pacienteId)
      .then((r) => {
        if (ativo) setEncaminhamentos(r);
      })
      .catch((e) => {
        if (ativo) setErro(e instanceof ApiError ? e.message : "Falha ao carregar seus encaminhamentos");
      })
      .finally(() => {
        if (ativo) setCarregando(false);
      });
    return () => {
      ativo = false;
    };
  }, [pacienteId]);

  return (
    <div className="card">
      <h1>Olá, {nome}</h1>
      <p className="hint">Aqui você acompanha o andamento dos seus encaminhamentos.</p>
      {!pacienteId && (
        <p className="estado estado-erro">
          Seu usuário de login não está vinculado a um cadastro de paciente — fale com a secretaria.
        </p>
      )}
      {carregando && <Carregando label="Carregando seus encaminhamentos..." />}
      {erro && <ErroMensagem mensagem={erro} />}
      {encaminhamentos &&
        (encaminhamentos.length > 0 ? (
          <div className="lista-cards-paciente">
            {encaminhamentos.map((e) => (
              <EncaminhamentoCard key={e.id} encaminhamento={e} />
            ))}
          </div>
        ) : (
          <SemDados label="Você ainda não tem encaminhamentos registrados." />
        ))}
    </div>
  );
}

function DashboardMedico({ profissionalId, nome }: { profissionalId: number | null; nome: string }) {
  const [itens, setItens] = useState<FilaItemResponse[] | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!profissionalId) {
      setCarregando(false);
      return;
    }
    let ativo = true;
    api
      .filaPorMedico(profissionalId)
      .then((r) => {
        if (ativo) setItens(r);
      })
      .catch((e) => {
        if (ativo) setErro(e instanceof ApiError ? e.message : "Falha ao carregar sua fila");
      })
      .finally(() => {
        if (ativo) setCarregando(false);
      });
    return () => {
      ativo = false;
    };
  }, [profissionalId]);

  return (
    <div className="card">
      <h1>Olá, {nome}</h1>
      <h2>Pacientes que você encaminhou</h2>
      {!profissionalId && (
        <p className="estado estado-erro">
          Seu usuário de login não está vinculado a um cadastro de profissional — fale com a secretaria.
        </p>
      )}
      {carregando && <Carregando label="Carregando sua fila..." />}
      {erro && <ErroMensagem mensagem={erro} />}
      {itens &&
        (itens.length > 0 ? (
          <TabelaFila itens={itens} />
        ) : (
          <SemDados label="Nenhum paciente seu está na fila no momento." />
        ))}
    </div>
  );
}

function DashboardSecretaria({ nome }: { nome: string }) {
  return (
    <div className="card">
      <h1>Olá, {nome}</h1>
      <h2>Resumo da rede</h2>
      <PainelResumo />
      <p className="hint">
        Veja a fila completa por especialidade em <Link to="/fila-secretaria">Todas as filas</Link> e as
        vagas de horário em <Link to="/vagas-agendamento">Vagas &amp; Agendamento</Link>.
      </p>
    </div>
  );
}

function DashboardGenerico({ nome, papel }: { nome: string; papel: string }) {
  return (
    <div className="card">
      <h1>Bem-vindo(a), {nome}</h1>
      <p>
        Papel autenticado: <strong>{papel}</strong>.
      </p>
      <p className="hint">
        Use o menu acima para exercitar os endpoints do backend. Esta é uma ferramenta interna de teste
        manual — o vídeo do MVP do hackathon continua sendo gravado via Postman/Swagger.
      </p>
      {papel === "ESPECIALISTA" && (
        <p className="hint">
          Veja quem você vai atender e quem já atendeu em <Link to="/minha-agenda">Meus atendimentos</Link>, suas
          vagas em <Link to="/minhas-vagas">Minhas vagas</Link>, e registre o atendimento realizado em{" "}
          <Link to="/resultado-exame">Atendimento realizado</Link>.
        </p>
      )}
    </div>
  );
}
