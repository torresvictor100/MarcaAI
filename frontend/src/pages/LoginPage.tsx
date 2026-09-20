import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import logoMarcaAi from "../assets/logo.png";

export function LoginPage() {
  const { sessao, login, carregando, erro } = useAuth();
  const [usuario, setUsuario] = useState("");
  const [senha, setSenha] = useState("");

  if (sessao) {
    return <Navigate to="/" replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await login(usuario, senha);
    } catch {
      // erro já fica exposto via contexto
    }
  }

  return (
    <div className="card login-card">
      <img src={logoMarcaAi} alt="MarcaAI" className="logo logo-login" />
      <h1>Entrar</h1>
      <form onSubmit={onSubmit}>
        <label>
          Login
          <input value={usuario} onChange={(e) => setUsuario(e.target.value)} required autoFocus />
        </label>
        <label>
          Senha
          <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required />
        </label>
        <button type="submit" disabled={carregando}>
          {carregando ? "Entrando..." : "Entrar"}
        </button>
        {erro && <p className="estado estado-erro">{erro}</p>}
      </form>
    </div>
  );
}
