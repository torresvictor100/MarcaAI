package com.marcaai.fila;

import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.config.ConflitoException;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FilaService {

    private final ItemFilaRepository itemFilaRepository;

    @Transactional
    public ItemFila adicionarNaFila(Long encaminhamentoId, String especialidadeOuExame, double score, Long profissionalId) {
        ItemFila item = itemFilaRepository.findByEncaminhamentoId(encaminhamentoId)
                .orElse(ItemFila.builder()
                        .encaminhamentoId(encaminhamentoId)
                        .especialidadeOuExame(especialidadeOuExame)
                        .profissionalId(profissionalId)
                        .build());
        item.setScoreAtual(score);
        item.setSaiuDaFilaEm(null);
        return itemFilaRepository.save(item);
    }

    /** Paciente atendido sai da fila; as posições dos demais são recalculadas na próxima consulta. */
    @Transactional
    public void retirarDaFila(Long encaminhamentoId) {
        itemFilaRepository.findByEncaminhamentoId(encaminhamentoId)
                .filter(item -> item.getSaiuDaFilaEm() == null)
                .ifPresent(item -> {
                    item.setSaiuDaFilaEm(LocalDateTime.now());
                    itemFilaRepository.save(item);
                });
    }

    /** Posição/score de um único encaminhamento na fila da própria especialidade/exame — usado pelo paciente. */
    public FilaItemResponse buscarPorEncaminhamento(Long encaminhamentoId) {
        ItemFila item = itemFilaRepository.findByEncaminhamentoId(encaminhamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Encaminhamento " + encaminhamentoId + " ainda não está na fila"));
        if (item.getSaiuDaFilaEm() != null) {
            throw new RecursoNaoEncontradoException("Encaminhamento " + encaminhamentoId + " já foi atendido e saiu da fila");
        }
        List<ItemFila> ordenados = ordenarComOverride(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull(item.getEspecialidadeOuExame()));
        int posicao = posicaoDoItem(ordenados, item.getId());
        if (posicao == 0) {
            throw new RecursoNaoEncontradoException("Encaminhamento " + encaminhamentoId + " não encontrado na fila ordenada");
        }
        return FilaItemResponse.of(item, posicao);
    }

    /**
     * Posição (1-based) de um item numa lista já ordenada, comparando por id — nunca por {@code indexOf}
     * (igualdade por referência, já que {@code ItemFila} não tem {@code equals}/{@code hashCode} próprios).
     * Necessário sempre que o item e a lista vierem de consultas separadas: fora de uma mesma transação/
     * persistence context, o Hibernate pode devolver instâncias Java diferentes para a mesma linha, e
     * {@code indexOf} erraria a posição (ou nem encontraria o item) nesse caso. Devolve 0 se não encontrado.
     */
    private int posicaoDoItem(List<ItemFila> ordenados, Long itemId) {
        for (int i = 0; i < ordenados.size(); i++) {
            if (ordenados.get(i).getId().equals(itemId)) {
                return i + 1;
            }
        }
        return 0;
    }

    public List<FilaItemResponse> listarPorEspecialidade(String especialidadeOuExame) {
        List<ItemFila> ordenados = ordenarComOverride(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull(especialidadeOuExame));
        List<FilaItemResponse> resposta = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            resposta.add(FilaItemResponse.of(ordenados.get(i), i + 1));
        }
        return resposta;
    }

    public List<FilaItemResponse> listarPorProfissional(Long profissionalId) {
        Map<String, List<ItemFila>> itensDoProfissionalPorEspecialidade = itemFilaRepository.findByProfissionalIdAndSaiuDaFilaEmIsNull(profissionalId)
                .stream()
                .collect(Collectors.groupingBy(ItemFila::getEspecialidadeOuExame));

        List<FilaItemResponse> resposta = new ArrayList<>();
        for (var entry : itensDoProfissionalPorEspecialidade.entrySet()) {
            List<ItemFila> todosDaEspecialidade = ordenarComOverride(
                    itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull(entry.getKey()));
            for (ItemFila item : entry.getValue()) {
                int posicao = posicaoDoItem(todosDaEspecialidade, item.getId());
                resposta.add(FilaItemResponse.of(item, posicao));
            }
        }
        resposta.sort(Comparator.comparing(FilaItemResponse::especialidadeOuExame).thenComparingInt(FilaItemResponse::posicao));
        return resposta;
    }

    @Transactional
    public FilaItemResponse aplicarOverride(Long itemId, OverrideRequest request) {
        ItemFila item = itemFilaRepository.findById(itemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item de fila não encontrado: " + itemId));
        if (item.getSaiuDaFilaEm() != null) {
            throw new ConflitoException("Paciente já foi atendido e saiu da fila; não há posição para ajustar");
        }

        if (request.justificativa() == null || request.justificativa().isBlank()) {
            throw new RegraDeNegocioException("Ajuste manual exige justificativa preenchida");
        }

        item.setOverrideManual(true);
        item.setPosicaoOverride(request.posicao());
        item.setJustificativaOverride(request.justificativa());
        itemFilaRepository.save(item);

        List<ItemFila> ordenados = ordenarComOverride(itemFilaRepository.findByEspecialidadeOuExameIgnoreCaseAndSaiuDaFilaEmIsNull(item.getEspecialidadeOuExame()));
        int posicaoFinal = posicaoDoItem(ordenados, item.getId());
        return FilaItemResponse.of(item, posicaoFinal);
    }

    /**
     * Itens sem override são ordenados por score decrescente. Itens com override manual são inseridos
     * na posição alvo pedida pela secretaria; os demais preenchem o restante das posições na ordem natural.
     */
    private List<ItemFila> ordenarComOverride(List<ItemFila> itens) {
        LinkedList<ItemFila> naturais = itens.stream()
                .filter(i -> i.getPosicaoOverride() == null)
                .sorted(Comparator.comparingDouble(ItemFila::getScoreAtual).reversed())
                .collect(Collectors.toCollection(LinkedList::new));

        List<ItemFila> comOverride = itens.stream()
                .filter(i -> i.getPosicaoOverride() != null)
                .sorted(Comparator.comparingInt(ItemFila::getPosicaoOverride))
                .toList();

        int tamanho = itens.size();
        Map<Integer, ItemFila> overridesPorPosicao = new LinkedHashMap<>();
        for (ItemFila item : comOverride) {
            int posicaoAlvo = Math.min(Math.max(item.getPosicaoOverride(), 1), tamanho);
            if (overridesPorPosicao.putIfAbsent(posicaoAlvo, item) != null) {
                // Colisão: outro override já pediu essa mesma posição-alvo. Em vez de descartar o item
                // (um paciente nunca pode simplesmente sumir da fila), ele entra na frente da fila natural —
                // não garante a posição exata pedida, mas preserva a prioridade manual e a integridade da lista.
                naturais.addFirst(item);
            }
        }

        List<ItemFila> resultado = new ArrayList<>(tamanho);
        for (int posicao = 1; posicao <= tamanho; posicao++) {
            ItemFila fixo = overridesPorPosicao.get(posicao);
            if (fixo != null) {
                resultado.add(fixo);
            } else if (!naturais.isEmpty()) {
                resultado.add(naturais.poll());
            }
        }
        resultado.addAll(naturais);
        return resultado;
    }
}
