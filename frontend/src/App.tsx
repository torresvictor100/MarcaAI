import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { RequireRole } from "./auth/RequireRole";
import { LoginPage } from "./pages/LoginPage";
import { HomePage } from "./pages/HomePage";
import { AtendimentoPage } from "./pages/AtendimentoPage";
import { EncaminhamentoPage } from "./pages/EncaminhamentoPage";
import { FilaMedicoPage } from "./pages/FilaMedicoPage";
import { FilaSecretariaPage } from "./pages/FilaSecretariaPage";
import { VagasAgendamentoPage } from "./pages/VagasAgendamentoPage";
import { ResultadoExamePage } from "./pages/ResultadoExamePage";
import { PainelPage } from "./pages/PainelPage";
import { MeusEncaminhamentosPage } from "./pages/MeusEncaminhamentosPage";
import { MinhaAgendaPage } from "./pages/MinhaAgendaPage";
import { MinhasVagasPage } from "./pages/MinhasVagasPage";

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireRole>
            <Layout />
          </RequireRole>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route
          path="/atendimentos"
          element={
            <RequireRole papeis={["MEDICO_UBS"]}>
              <AtendimentoPage />
            </RequireRole>
          }
        />
        <Route
          path="/encaminhamentos"
          element={
            <RequireRole papeis={["MEDICO_UBS"]}>
              <EncaminhamentoPage />
            </RequireRole>
          }
        />
        <Route
          path="/fila"
          element={
            <RequireRole papeis={["MEDICO_UBS"]}>
              <FilaMedicoPage />
            </RequireRole>
          }
        />
        <Route
          path="/fila-secretaria"
          element={
            <RequireRole papeis={["SECRETARIA", "ADMIN"]}>
              <FilaSecretariaPage />
            </RequireRole>
          }
        />
        <Route
          path="/vagas-agendamento"
          element={
            <RequireRole papeis={["SECRETARIA", "ADMIN"]}>
              <VagasAgendamentoPage />
            </RequireRole>
          }
        />
        <Route
          path="/painel"
          element={
            <RequireRole papeis={["SECRETARIA", "ADMIN"]}>
              <PainelPage />
            </RequireRole>
          }
        />
        <Route
          path="/resultado-exame"
          element={
            <RequireRole papeis={["ESPECIALISTA"]}>
              <ResultadoExamePage />
            </RequireRole>
          }
        />
        <Route
          path="/minha-agenda"
          element={
            <RequireRole papeis={["ESPECIALISTA"]}>
              <MinhaAgendaPage />
            </RequireRole>
          }
        />
        <Route
          path="/minhas-vagas"
          element={
            <RequireRole papeis={["ESPECIALISTA"]}>
              <MinhasVagasPage />
            </RequireRole>
          }
        />
        <Route
          path="/meus-encaminhamentos"
          element={
            <RequireRole papeis={["PACIENTE"]}>
              <MeusEncaminhamentosPage />
            </RequireRole>
          }
        />
      </Route>
    </Routes>
  );
}

export default App;
