package br.edu.uniesp.assistencia.internal.ordemservico;

import br.edu.uniesp.assistencia.internal.cliente.entity.ClienteEntity;
import br.edu.uniesp.assistencia.internal.equipamento.entity.EquipamentoEntity;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.OrdemServicoEntity;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.StatusOrdemServico;
import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class OrdemServicoEntityTest {
    @Test
    void dominioRecusaClienteInativoSemDependerDoService() {
        var cliente = cliente(1L);
        cliente.inativar();
        assertThatThrownBy(() -> abrir(cliente, equipamento(cliente)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é permitido abrir Ordem de Serviço para um cliente inativo.");
    }

    @Test
    void dominioRecusaEquipamentoDeOutroClienteSemDependerDoService() {
        assertThatThrownBy(() -> abrir(cliente(1L), equipamento(cliente(2L))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O equipamento informado não pertence ao cliente indicado.");
    }

    @Test
    void vinculoComparaIdEmVezDeIdentidadeDoObjeto() {
        var ordem = abrir(cliente(1L), equipamento(cliente(1L)));
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdemServico.RECEBIDA);
        assertThat(ordem.getHistorico()).hasSize(1);
        assertThat(ordem.getHistorico().getFirst().getOrdemServico()).isSameAs(ordem);
        assertThatThrownBy(() -> ordem.getHistorico().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void descricaoObrigatoriaTambemEProtegidaNoDominio() {
        var cliente = cliente(1L);
        assertThatThrownBy(() -> new OrdemServicoEntity("OS-20261015-A1B2", cliente,
                equipamento(cliente), "   ", data()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void protocoloPrecisaTerFormatoEDataDaAbertura() {
        var cliente = cliente(1L);
        assertThatThrownBy(() -> new OrdemServicoEntity("OS-20261016-A1B2", cliente,
                equipamento(cliente), "Não liga.", data()))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new OrdemServicoEntity("OS-20261015-ZZZZ", cliente,
                equipamento(cliente), "Não liga.", data()))
                .isInstanceOf(RegraNegocioException.class);
    }

    private OrdemServicoEntity abrir(ClienteEntity cliente, EquipamentoEntity equipamento) {
        return new OrdemServicoEntity("OS-20261015-A1B2", cliente, equipamento, "Não liga.", data());
    }

    private LocalDateTime data() {
        return LocalDateTime.of(2026, 10, 15, 20, 30);
    }

    private ClienteEntity cliente(Long id) {
        var cliente = new ClienteEntity("Ana", "11111111111", "ana@example.com");
        ReflectionTestUtils.setField(cliente, "id", id);
        return cliente;
    }

    private EquipamentoEntity equipamento(ClienteEntity cliente) {
        var equipamento = new EquipamentoEntity("Notebook", "Dell", "Inspiron", "SN1");
        equipamento.associarCliente(cliente);
        return equipamento;
    }
}
