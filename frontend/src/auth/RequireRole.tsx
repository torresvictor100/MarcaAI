import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import type { Papel } from "../lib/types";

export function RequireRole({ papeis, children }: { papeis?: Papel[]; children: ReactNode }) {
  const { sessao } = useAuth();

  if (!sessao) {
    return <Navigate to="/login" replace />;
  }

  if (papeis && !papeis.includes(sessao.papel)) {
    return <p className="estado estado-erro">Seu papel ({sessao.papel}) não tem acesso a esta tela.</p>;
  }

  return <>{children}</>;
}
