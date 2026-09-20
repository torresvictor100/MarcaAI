import { Link, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { Papel } from "../lib/types";
import logoMarcaAi from "../assets/logo.png";

interface NavItem {
  to: string;
  label: string;
  papeis: Papel[];
}

const NAV_ITEMS: NavItem[] = [
  { to: "/atendimentos", label: "Atendimentos", papeis: ["MEDICO_UBS"] },
  { to: "/encaminhamentos", label: "Encaminhamentos", papeis: ["MEDICO_UBS"] },
  { to: "/fila", label: "Fila encaminhamentos", papeis: ["MEDICO_UBS"] },
  { to: "/fila-secretaria", label: "Todas as filas", papeis: ["SECRETARIA", "ADMIN"] },
  { to: "/vagas-agendamento", label: "Vagas & Agendamento", papeis: ["SECRETARIA", "ADMIN"] },
  { to: "/painel", label: "Painel", papeis: ["SECRETARIA", "ADMIN"] },
  { to: "/resultado-exame", label: "Atendimento realizado", papeis: ["ESPECIALISTA"] },
  { to: "/minha-agenda", label: "Meus atendimentos", papeis: ["ESPECIALISTA"] },
  { to: "/minhas-vagas", label: "Minhas vagas", papeis: ["ESPECIALISTA"] },
  { to: "/meus-encaminhamentos", label: "Meus encaminhamentos", papeis: ["PACIENTE"] },
];

export function Layout() {
  const { sessao, logout } = useAuth();

  const itensVisiveis = NAV_ITEMS.filter((item) => sessao && item.papeis.includes(sessao.papel));

  return (
    <div className="layout">
      <header className="topbar">
        <Link to="/" className="marca">
          <img src={logoMarcaAi} alt="MarcaAI" className="logo" />
        </Link>
        <nav className="nav">
          {itensVisiveis.map((item) => (
            <Link key={item.to} to={item.to}>
              {item.label}
            </Link>
          ))}
        </nav>
        {sessao && (
          <div className="sessao">
            <span>{sessao.nome}</span>
            <button onClick={logout}>Sair</button>
          </div>
        )}
      </header>
      <main className="conteudo">
        <Outlet />
      </main>
    </div>
  );
}
