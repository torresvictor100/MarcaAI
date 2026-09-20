import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setToken } from "../lib/api";
import type { Papel } from "../lib/types";

interface Sessao {
  token: string;
  usuarioId: number;
  nome: string;
  papel: Papel;
  pacienteId: number | null;
  profissionalId: number | null;
}

interface AuthContextValue {
  sessao: Sessao | null;
  carregando: boolean;
  erro: string | null;
  login: (login: string, senha: string) => Promise<void>;
  logout: () => void;
}

const STORAGE_KEY = "marcaai.sessao";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const salva = JSON.parse(raw) as Sessao;
        setSessao(salva);
        setToken(salva.token);
      }
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  async function login(login: string, senha: string) {
    setCarregando(true);
    setErro(null);
    try {
      const resposta = await api.login({ login, senha });
      const nova: Sessao = resposta;
      setToken(nova.token);
      setSessao(nova);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nova));
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Falha no login");
      throw e;
    } finally {
      setCarregando(false);
    }
  }

  function logout() {
    setToken(null);
    setSessao(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  const value = useMemo(() => ({ sessao, carregando, erro, login, logout }), [sessao, carregando, erro]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth precisa estar dentro de <AuthProvider>");
  return ctx;
}
