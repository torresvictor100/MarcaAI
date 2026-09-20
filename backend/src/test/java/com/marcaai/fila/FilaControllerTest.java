package com.marcaai.fila;

import com.marcaai.config.RequisicaoInvalidaException;
import com.marcaai.auth.Papel;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Regras de papel da fila (quem vê qual fila e quem faz ajuste manual) — ADR-007. */
class FilaControllerTest {

    private static final long MEDICO_USUARIO_ID = 3L;
    private static final long MEDICO_PROFISSIONAL_ID = 1L;

    private FilaService filaService;
    private FilaDetalhesService filaDetalhesService;
    private ProfissionalRepository profissionalRepository;
    private EncaminhamentoService encaminhamentoService;
    private FilaController controller;

    @BeforeEach
    void setUp() {
        filaService = mock(FilaService.class);
        filaDetalhesService = mock(FilaDetalhesService.class);
        profissionalRepository = mock(ProfissionalRepository.class);
        encaminhamentoService = mock(EncaminhamentoService.class);
        controller = new FilaController(filaService, filaDetalhesService, profissionalRepository, encaminhamentoService);
        when(filaDetalhesService.detalhar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profissionalRepository.findByUsuarioId(MEDICO_USUARIO_ID))
                .thenReturn(Optional.of(Profissional.builder().id(MEDICO_PROFISSIONAL_ID).usuarioId(MEDICO_USUARIO_ID).build()));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(long usuarioId, Papel papel) {
        MarcaAiPrincipal principal = new MarcaAiPrincipal(usuarioId, "usuario", papel);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static FilaItemResponse item(long itemId, String especialidade) {
        return new FilaItemResponse(itemId, itemId + 100, especialidade, 1, 50.0, false, null, null, null, null, null);
    }

    // --- GET /fila/todas -----------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"SECRETARIA", "ADMIN"})
    void gestaoVeTodasAsFilasAgrupadasSemEspecialidadeVazia(Papel papel) {
        logadoComo(9L, papel);
        when(encaminhamentoService.listarEspecialidades()).thenReturn(List.of("Cardiologia", "Dermatologia", "Ortopedia"));
        when(filaService.listarPorEspecialidade("Cardiologia")).thenReturn(List.of(item(1L, "Cardiologia")));
        when(filaService.listarPorEspecialidade("Dermatologia")).thenReturn(List.of());
        when(filaService.listarPorEspecialidade("Ortopedia")).thenReturn(List.of(item(2L, "Ortopedia")));

        Map<String, List<FilaItemResponse>> filas = controller.listarTodas();

        assertThat(filas).containsOnlyKeys("Cardiologia", "Ortopedia");
        assertThat(filas.keySet()).containsExactly("Cardiologia", "Ortopedia");
        verify(filaDetalhesService, times(3)).detalhar(any());
    }

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"PACIENTE", "MEDICO_UBS", "ESPECIALISTA"})
    void demaisPapeisNaoVeemTodasAsFilas(Papel papel) {
        logadoComo(9L, papel);

        assertThatThrownBy(controller::listarTodas).isInstanceOf(AccessDeniedException.class);
        verify(filaService, never()).listarPorEspecialidade(any());
    }

    // --- GET /fila -----------------------------------------------------------------------------------

    @Test
    void semEspecialidadeNemMedicoDa400AntesDeOlharOLogin() {
        assertThatThrownBy(() -> controller.listar(null, null))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("especialidade");
        verifyNoInteractions(filaService);
    }

    @Test
    void medicoVeAPropriaFilaDeEncaminhamentos() {
        logadoComo(MEDICO_USUARIO_ID, Papel.MEDICO_UBS);
        when(filaService.listarPorProfissional(MEDICO_PROFISSIONAL_ID)).thenReturn(List.of(item(1L, "Cardiologia")));

        assertThat(controller.listar(null, MEDICO_PROFISSIONAL_ID)).hasSize(1);
        verify(filaDetalhesService, never()).detalhar(any());
    }

    @Test
    void medicoNaoVeAFilaDeOutroMedico() {
        logadoComo(MEDICO_USUARIO_ID, Papel.MEDICO_UBS);

        assertThatThrownBy(() -> controller.listar(null, 99L)).isInstanceOf(AccessDeniedException.class);
        verify(filaService, never()).listarPorProfissional(anyLong());
    }

    @Test
    void medicoSemCadastroDeProfissionalNaoVeFilaNenhuma() {
        logadoComo(44L, Papel.MEDICO_UBS);
        when(profissionalRepository.findByUsuarioId(44L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.listar(null, MEDICO_PROFISSIONAL_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"SECRETARIA", "ADMIN"})
    void gestaoVeAFilaDeQualquerMedico(Papel papel) {
        logadoComo(9L, papel);
        when(filaService.listarPorProfissional(99L)).thenReturn(List.of(item(1L, "Cardiologia")));

        assertThat(controller.listar(null, 99L)).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"PACIENTE", "ESPECIALISTA"})
    void outrosPapeisNaoVeemFilaDeMedico(Papel papel) {
        logadoComo(MEDICO_USUARIO_ID, papel);

        assertThatThrownBy(() -> controller.listar(null, MEDICO_PROFISSIONAL_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"SECRETARIA", "ADMIN"})
    void gestaoVeAFilaDaEspecialidadeComDetalhes(Papel papel) {
        logadoComo(9L, papel);
        when(filaService.listarPorEspecialidade("Cardiologia")).thenReturn(List.of(item(1L, "Cardiologia")));

        assertThat(controller.listar("Cardiologia", null)).hasSize(1);
        verify(filaDetalhesService).detalhar(any());
    }

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"PACIENTE", "MEDICO_UBS", "ESPECIALISTA"})
    void demaisPapeisNaoVeemAFilaDaEspecialidade(Papel papel) {
        logadoComo(MEDICO_USUARIO_ID, papel);

        assertThatThrownBy(() -> controller.listar("Cardiologia", null)).isInstanceOf(AccessDeniedException.class);
        verify(filaService, never()).listarPorEspecialidade(any());
    }

    // --- PATCH /fila/{itemId}/override ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"SECRETARIA", "ADMIN"})
    void gestaoFazAjusteManual(Papel papel) {
        logadoComo(9L, papel);
        OverrideRequest request = new OverrideRequest(1, "Piora clínica relatada por telefone");
        when(filaService.aplicarOverride(7L, request)).thenReturn(item(7L, "Cardiologia"));

        assertThat(controller.override(7L, request).itemId()).isEqualTo(7L);
    }

    @ParameterizedTest
    @EnumSource(value = Papel.class, names = {"PACIENTE", "MEDICO_UBS", "ESPECIALISTA"})
    void demaisPapeisNaoFazemAjusteManual(Papel papel) {
        logadoComo(9L, papel);

        assertThatThrownBy(() -> controller.override(7L, new OverrideRequest(1, "motivo")))
                .isInstanceOf(AccessDeniedException.class);
        verify(filaService, never()).aplicarOverride(anyLong(), any());
    }
}
