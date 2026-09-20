package com.marcaai.cadastro;

import com.marcaai.config.RequisicaoInvalidaException;
import com.marcaai.config.ConflitoException;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.shared.TipoProfissional;
import com.marcaai.shared.UnidadeSaudeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CadastroServiceTest {

    private ProfissionalRepository profissionalRepository;
    private PacienteRepository pacienteRepository;
    private CadastroService service;

    @BeforeEach
    void setUp() {
        profissionalRepository = mock(ProfissionalRepository.class);
        EncaminhamentoService encaminhamentoService = mock(EncaminhamentoService.class);
        pacienteRepository = mock(PacienteRepository.class);
        service = new CadastroService(profissionalRepository, mock(UnidadeSaudeRepository.class), encaminhamentoService,
                pacienteRepository);
        when(encaminhamentoService.listarEspecialidades()).thenReturn(List.of("Cardiologia", "Dermatologia"));
        when(profissionalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cadastraEspecialistaComAGrafiaDoCatalogo() {
        Profissional salvo = service.cadastrarEspecialista(new ProfissionalRequest("  Dra. Ana Lima ", " CRM-SP 777777 ", "cardiologia"));

        assertThat(salvo.getEspecialidade()).isEqualTo("Cardiologia");
        assertThat(salvo.getTipo()).isEqualTo(TipoProfissional.ESPECIALISTA);
        assertThat(salvo.getNome()).isEqualTo("Dra. Ana Lima");
        assertThat(salvo.getRegistroConselho()).isEqualTo("CRM-SP 777777");
    }

    @Test
    void recusaEspecialidadeForaDoCatalogoERegistroRepetido() {
        assertThatThrownBy(() -> service.cadastrarEspecialista(new ProfissionalRequest("X", "CRM 1", "Astrologia")))
                .isInstanceOf(RegraDeNegocioException.class);

        when(profissionalRepository.existsByRegistroConselhoIgnoreCase("CRM-SP 222222")).thenReturn(true);
        assertThatThrownBy(() -> service.cadastrarEspecialista(new ProfissionalRequest("X", "CRM-SP 222222", "Cardiologia")))
                .as("registro repetido é conflito (409)").isInstanceOf(ConflitoException.class);
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void listaTodosOuPorEspecialidade() {
        service.listarProfissionais(null);
        verify(profissionalRepository).findAllByOrderByNome();
        service.listarProfissionais(" Cardiologia ");
        verify(profissionalRepository).findByEspecialidadeIgnoreCaseOrderByNome("Cardiologia");
    }

    @Test
    void buscaPacientesPorNomeComTermoAparadoELimiteDeResultados() {
        Paciente marta = Paciente.builder().id(1L).nome("Marta Souza").cpf("12345678901").build();
        when(pacienteRepository.buscarPorNome("%mar%", PageRequest.of(0, CadastroService.LIMITE_RESULTADOS_BUSCA)))
                .thenReturn(List.of(marta));

        assertThat(service.buscarPacientesPorNome("  mar ")).containsExactly(marta);
    }

    @Test
    void buscaDePacienteTiraAcentoCaixaECuringasDoTermo() {
        service.buscarPacientesPorNome(" JOÃO_% ");
        verify(pacienteRepository).buscarPorNome("%joao%", PageRequest.of(0, CadastroService.LIMITE_RESULTADOS_BUSCA));
    }

    @Test
    void recusaBuscaDePacienteComMenosDeDuasLetras() {
        assertThatThrownBy(() -> service.buscarPacientesPorNome(" a ")).isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> service.buscarPacientesPorNome(null)).isInstanceOf(RequisicaoInvalidaException.class);
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void mascaraOCpfNaRespostaDaBusca() {
        Paciente paciente = Paciente.builder().id(7L).nome("Ana").cpf("12345678901").build();

        assertThat(PacienteResumoResponse.of(paciente).cpfMascarado()).isEqualTo("***.456.789-**");
        assertThat(PacienteResumoResponse.mascararCpf("123")).isEqualTo("***.***.***-**");
    }
}
