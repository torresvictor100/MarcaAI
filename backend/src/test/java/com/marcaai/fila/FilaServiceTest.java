package com.marcaai.fila;

import com.marcaai.config.ConflitoException;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.config.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FilaServiceTest {

    private ItemFilaRepository itemFilaRepository;
    private FilaService filaService;

    @BeforeEach
    void setUp() {
        itemFilaRepository = mock(ItemFilaRepository.class);
        filaService = new FilaService(itemFilaRepository);
        when(itemFilaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ItemFila item(long id, long encId, double score, Integer posicaoOverride) {
        return ItemFila.builder()
                .id(id).encaminhamentoId(encId).especialidadeOuExame("Cardiologia")
                .profissionalId(1L).scoreAtual(score).posicaoOverride(posicaoOverride)
                .build();
    }

    @Test
    void listarPorEspecialidadeOrdenaPorScoreDecrescenteSemOverride() {
        List<ItemFila> itens = List.of(item(1, 10, 30.0, null), item(2, 20, 90.0, null), item(3, 30, 50.0, null));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia")).thenReturn(itens);

        List<FilaItemResponse> resultado = filaService.listarPorEspecialidade("Cardiologia");

        assertThat(resultado).extracting(FilaItemResponse::encaminhamentoId).containsExactly(20L, 30L, 10L);
        assertThat(resultado).extracting(FilaItemResponse::posicao).containsExactly(1, 2, 3);
    }

    @Test
    void itemComOverrideAssumeAPosicaoAlvoEDemaisSeReorganizam() {
        List<ItemFila> itens = List.of(item(1, 10, 30.0, null), item(2, 20, 90.0, null), item(3, 30, 10.0, 1));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia")).thenReturn(itens);

        List<FilaItemResponse> resultado = filaService.listarPorEspecialidade("Cardiologia");

        assertThat(resultado.get(0).encaminhamentoId()).isEqualTo(30L); // override força a 1ª posição
        assertThat(resultado).extracting(FilaItemResponse::encaminhamentoId).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void doisOverridesNaMesmaPosicaoNaoDescartamNenhumItem() {
        // Dois itens pedindo a mesma posição-alvo (1): o segundo não pode simplesmente sumir da fila.
        List<ItemFila> itens = List.of(
                item(1, 10, 30.0, 1), item(2, 20, 90.0, 1), item(3, 30, 10.0, null));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia")).thenReturn(itens);

        List<FilaItemResponse> resultado = filaService.listarPorEspecialidade("Cardiologia");

        assertThat(resultado).hasSize(3);
        assertThat(resultado).extracting(FilaItemResponse::encaminhamentoId).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void posicaoDeOverrideAlemDoTamanhoDaListaEhLimitadaAoUltimoLugar() {
        List<ItemFila> itens = List.of(item(1, 10, 30.0, null), item(2, 20, 90.0, 999));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia")).thenReturn(itens);

        List<FilaItemResponse> resultado = filaService.listarPorEspecialidade("Cardiologia");

        assertThat(resultado.get(resultado.size() - 1).encaminhamentoId()).isEqualTo(20L);
    }

    @Test
    void listarPorProfissionalCalculaPosicaoDentroDeCadaEspecialidade() {
        ItemFila cardioA = item(1, 10, 90.0, null);
        ItemFila cardioB = item(2, 20, 30.0, null);
        ItemFila orto = ItemFila.builder().id(3L).encaminhamentoId(30L).especialidadeOuExame("Ortopedia").profissionalId(1L).scoreAtual(40.0).build();

        // Instâncias separadas (não as mesmas de cardioB/orto) simulando consultas em transações
        // diferentes — a posição não pode depender de igualdade por referência entre elas.
        ItemFila cardioBOutraInstancia = item(2, 20, 30.0, null);
        ItemFila ortoOutraInstancia = ItemFila.builder().id(3L).encaminhamentoId(30L).especialidadeOuExame("Ortopedia").profissionalId(1L).scoreAtual(40.0).build();

        when(itemFilaRepository.findByProfissionalIdAndSaiuDaFilaEmIsNull(1L)).thenReturn(List.of(cardioB, orto));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia")).thenReturn(List.of(cardioA, cardioBOutraInstancia));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Ortopedia")).thenReturn(List.of(ortoOutraInstancia));

        List<FilaItemResponse> resultado = filaService.listarPorProfissional(1L);

        FilaItemResponse cardioResposta = resultado.stream().filter(r -> r.encaminhamentoId() == 20L).findFirst().orElseThrow();
        assertThat(cardioResposta.posicao()).isEqualTo(2); // atrás do item de score 90 que também é da especialidade
    }

    @Test
    void aplicarOverrideComJustificativaEmBrancoLancaRegraDeNegocio() {
        ItemFila item = item(1, 10, 30.0, null);
        when(itemFilaRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> filaService.aplicarOverride(1L, new OverrideRequest(1, "   ")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void aplicarOverrideEmItemInexistenteLancaEntityNotFound() {
        when(itemFilaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filaService.aplicarOverride(99L, new OverrideRequest(1, "justificativa válida")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void aplicarOverrideValidoMarcaOItemEDevolveANovaPosicao() {
        ItemFila item = item(1, 10, 30.0, null);
        when(itemFilaRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia")).thenReturn(List.of(item));

        FilaItemResponse resposta = filaService.aplicarOverride(1L, new OverrideRequest(1, "Paciente presente na unidade"));

        assertThat(resposta.overrideManual()).isTrue();
        assertThat(resposta.justificativaOverride()).isEqualTo("Paciente presente na unidade");
        verify(itemFilaRepository).save(item);
    }

    @Test
    void adicionarNaFilaAtualizaScoreDeItemJaExistente() {
        ItemFila existente = item(1, 10, 30.0, null);
        when(itemFilaRepository.findByEncaminhamentoId(10L)).thenReturn(Optional.of(existente));

        ItemFila resultado = filaService.adicionarNaFila(10L, "Cardiologia", 99.0, 1L);

        assertThat(resultado.getScoreAtual()).isEqualTo(99.0);
        assertThat(resultado.getId()).isEqualTo(1L);
    }

    @Test
    void buscarPorEncaminhamentoDevolveAPosicaoMesmoComInstanciasDiferentesDaMesmaLinha() {
        // Simula duas consultas em sessões/transações diferentes: o Hibernate pode devolver instâncias
        // Java distintas para a mesma linha (encaminhamentoId=20, itemId=2) — a busca não pode depender de
        // igualdade por referência (indexOf), só do id.
        ItemFila itemDaPrimeiraConsulta = item(2, 20, 90.0, null);
        ItemFila mesmoItemOutraInstancia = item(2, 20, 90.0, null);
        ItemFila outroItemDaEspecialidade = item(1, 10, 30.0, null);
        when(itemFilaRepository.findByEncaminhamentoId(20L)).thenReturn(Optional.of(itemDaPrimeiraConsulta));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia"))
                .thenReturn(List.of(outroItemDaEspecialidade, mesmoItemOutraInstancia));

        FilaItemResponse resposta = filaService.buscarPorEncaminhamento(20L);

        assertThat(resposta.encaminhamentoId()).isEqualTo(20L);
        assertThat(resposta.posicao()).isEqualTo(1); // score 90 > 30, fica na frente
    }

    @Test
    void buscarPorEncaminhamentoInexistenteNaFilaLancaEntityNotFound() {
        when(itemFilaRepository.findByEncaminhamentoId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filaService.buscarPorEncaminhamento(999L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void adicionarNaFilaCriaNovoItemQuandoNaoExiste() {
        when(itemFilaRepository.findByEncaminhamentoId(anyLong())).thenReturn(Optional.empty());

        ItemFila resultado = filaService.adicionarNaFila(50L, "Cardiologia", 40.0, 1L);

        assertThat(resultado.getEncaminhamentoId()).isEqualTo(50L);
        assertThat(resultado.getScoreAtual()).isEqualTo(40.0);
    }

    @Test
    void retirarDaFilaMarcaASaidaSemApagarOItem() {
        ItemFila item = item(1, 10, 30.0, 2);
        item.setJustificativaOverride("prioridade clínica");
        when(itemFilaRepository.findByEncaminhamentoId(10L)).thenReturn(Optional.of(item));

        filaService.retirarDaFila(10L);

        assertThat(item.getSaiuDaFilaEm()).isNotNull();
        assertThat(item.getJustificativaOverride()).isEqualTo("prioridade clínica");
        verify(itemFilaRepository).save(item);
        verify(itemFilaRepository, never()).delete(any());
    }

    @Test
    void retirarDaFilaNaoMexeEmQuemJaSaiuOuNuncaEntrou() {
        ItemFila jaSaiu = item(1, 10, 30.0, null);
        jaSaiu.setSaiuDaFilaEm(java.time.LocalDateTime.of(2026, 9, 20, 10, 0));
        when(itemFilaRepository.findByEncaminhamentoId(10L)).thenReturn(Optional.of(jaSaiu));
        when(itemFilaRepository.findByEncaminhamentoId(20L)).thenReturn(Optional.empty());

        filaService.retirarDaFila(10L);
        filaService.retirarDaFila(20L);

        assertThat(jaSaiu.getSaiuDaFilaEm()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 20, 10, 0));
        verify(itemFilaRepository, never()).save(any());
    }

    @Test
    void quemJaSaiuDaFilaNaoTemPosicaoNemAjusteManual() {
        ItemFila jaSaiu = item(1, 10, 30.0, null);
        jaSaiu.setSaiuDaFilaEm(java.time.LocalDateTime.of(2026, 9, 20, 10, 0));
        when(itemFilaRepository.findByEncaminhamentoId(10L)).thenReturn(Optional.of(jaSaiu));
        when(itemFilaRepository.findById(1L)).thenReturn(Optional.of(jaSaiu));

        assertThatThrownBy(() -> filaService.buscarPorEncaminhamento(10L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> filaService.aplicarOverride(1L, new OverrideRequest(1, "motivo")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void aplicarOverrideSemJustificativaLancaRegraDeNegocioSemSalvar() {
        when(itemFilaRepository.findById(1L)).thenReturn(Optional.of(item(1, 10, 30.0, null)));

        assertThatThrownBy(() -> filaService.aplicarOverride(1L, new OverrideRequest(1, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(itemFilaRepository, never()).save(any());
    }

    @Test
    void itemQueSumiuDaFilaOrdenadaEntreAsConsultasDa404() {
        // O item foi encontrado pelo encaminhamento, mas a lista da especialidade (consulta separada) já não o traz.
        when(itemFilaRepository.findByEncaminhamentoId(10L)).thenReturn(Optional.of(item(1, 10, 30.0, null)));
        when(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull("Cardiologia"))
                .thenReturn(List.of(item(2, 20, 90.0, null)));

        assertThatThrownBy(() -> filaService.buscarPorEncaminhamento(10L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não encontrado na fila ordenada");
    }
}
