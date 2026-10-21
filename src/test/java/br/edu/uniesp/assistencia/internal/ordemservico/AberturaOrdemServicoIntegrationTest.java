package br.edu.uniesp.assistencia.internal.ordemservico;

import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoRequest;
import br.edu.uniesp.assistencia.internal.ordemservico.service.GeradorProtocoloService;
import br.edu.uniesp.assistencia.internal.ordemservico.service.OrdemServicoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AberturaOrdemServicoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrdemServicoService service;
    @Autowired GeradorProtocoloService gerador;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepararBanco() {
        jdbc.update("DELETE FROM historico_status");
        jdbc.update("DELETE FROM ordens_servico");
        jdbc.update("DELETE FROM sequencias_protocolo");
        jdbc.update("DELETE FROM equipamentos");
        jdbc.update("DELETE FROM clientes");
        jdbc.update("INSERT INTO clientes (id,nome,cpf,email,ativo) VALUES (1,'Ana','11111111111','ana@example.com',true)");
        jdbc.update("INSERT INTO clientes (id,nome,cpf,email,ativo) VALUES (2,'Bia','22222222222','bia@example.com',true)");
        jdbc.update("INSERT INTO clientes (id,nome,cpf,email,ativo) VALUES (3,'Caio','33333333333','caio@example.com',false)");
        jdbc.update("INSERT INTO equipamentos (id,tipo,marca,modelo,cliente_id) VALUES (10,'Notebook','Dell','Inspiron',1)");
        jdbc.update("INSERT INTO equipamentos (id,tipo,marca,modelo,cliente_id) VALUES (20,'Notebook','Lenovo','ThinkPad',3)");
    }

    @Test
    void abrirOrdemRetorna201EPersisteHistoricoDeAbertura() throws Exception {
        LocalDateTime inicio = LocalDateTime.now();
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(payload(1, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.numeroProtocolo", matchesPattern("OS-[0-9]{8}-[0-9A-F]{4}")))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.dataAbertura").isString());

        var ordem = jdbc.queryForMap("SELECT * FROM ordens_servico");
        var abertura = ((java.sql.Timestamp) ordem.get("data_abertura")).toLocalDateTime();
        assertThat(abertura).isBetween(inicio.minusSeconds(1), LocalDateTime.now().plusSeconds(1));
        assertThat(ordem.get("descricao_problema")).isEqualTo("Notebook não liga.");
        assertThat(ordem.get("numero_protocolo").toString()).startsWith(
                "OS-" + abertura.toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        var historico = jdbc.queryForMap("SELECT * FROM historico_status");
        assertThat(historico.get("ordem_servico_id")).isEqualTo(ordem.get("id"));
        assertThat(historico.get("status_anterior")).isNull();
        assertThat(historico.get("novo_status")).isEqualTo("RECEBIDA");
        assertThat(historico.get("responsavel")).isEqualTo("SISTEMA");
        assertThat(historico.get("observacao")).isEqualTo("Abertura da ordem de serviço.");
        assertThat(historico.get("data_alteracao")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"clienteId\":null,\"equipamentoId\":null,\"descricaoProblema\":null}",
            "{\"descricaoProblema\":\"   \"}"})
    void camposObrigatoriosRetornam400ComErrosPorCampo(String corpo) throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/ordens"))
                .andExpect(jsonPath("$.erros.clienteId").exists())
                .andExpect(jsonPath("$.erros.equipamentoId").exists())
                .andExpect(jsonPath("$.erros.descricaoProblema").exists());
        assertSemOrdens();
    }

    @Test
    void descricaoEmBrancoComIdsValidosRetorna400() throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"equipamentoId\":10,\"descricaoProblema\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.descricaoProblema").exists());
        assertSemOrdens();
    }

    @Test
    void clienteInexistenteRetorna404() throws Exception {
        assertErro(payload(999, 10), 404, "Cliente não encontrado para o identificador: 999");
    }

    @Test
    void equipamentoInexistenteRetorna404() throws Exception {
        assertErro(payload(1, 999), 404, "Equipamento não encontrado para o identificador: 999");
    }

    @Test
    void clienteInativoRetorna422() throws Exception {
        assertErro(payload(3, 20), 422, "Não é permitido abrir Ordem de Serviço para um cliente inativo.");
    }

    @Test
    void equipamentoDeOutroClienteRetorna422() throws Exception {
        assertErro(payload(2, 10), 422, "O equipamento informado não pertence ao cliente indicado.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"clienteId\":\"abc\"}", ""})
    void corpoInvalidoRetornaProblemDetail400(String corpo) throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/ordens"));
        assertSemOrdens();
    }

    @Test
    void requisicoesConcorrentesRecebemProtocolosExclusivos() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tarefas = IntStream.range(0, 18)
                    .mapToObj(i -> (Callable<String>) () -> service.abrirOrdemServico(
                            new CriarOrdemServicoRequest(1L, 10L, "Notebook não liga.")).numeroProtocolo())
                    .toList();
            var protocolos = new HashSet<String>();
            for (var resultado : executor.invokeAll(tarefas, 30, TimeUnit.SECONDS)) {
                assertThat(protocolos.add(resultado.get())).isTrue();
            }
            assertThat(protocolos).hasSize(18);
        }
        assertThat(contar("ordens_servico")).isEqualTo(18);
        assertThat(contar("historico_status")).isEqualTo(18);
    }

    @Test
    void falhaNaTransacaoDesfazOrdemHistoricoEReservaDeProtocolo() {
        var transacao = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            service.abrirOrdemServico(new CriarOrdemServicoRequest(1L, 10L, "Falha elétrica."));
            throw new IllegalStateException("Falha após persistir a abertura");
        })).isInstanceOf(IllegalStateException.class);
        assertSemOrdens();
        assertThat(contar("sequencias_protocolo")).isZero();
    }

    @Test
    void sequenciaReiniciaEmNovaDataSemReutilizarProtocolos() {
        var transacao = new TransactionTemplate(transactionManager);
        var hoje = LocalDate.of(2026, 10, 15);
        assertThat(transacao.<String>execute(s -> gerador.gerar(hoje))).isEqualTo("OS-20261015-0000");
        assertThat(transacao.<String>execute(s -> gerador.gerar(hoje))).isEqualTo("OS-20261015-0001");
        assertThat(transacao.<String>execute(s -> gerador.gerar(hoje.plusDays(1)))).isEqualTo("OS-20261016-0000");
    }

    @Test
    void esgotamentoDoSufixoNaoReutilizaProtocolos() throws Exception {
        jdbc.update("INSERT INTO sequencias_protocolo (data_referencia, proximo_valor) VALUES (?,65535)", LocalDate.now());
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(payload(1, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroProtocolo", endsWith("-FFFF")));
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(payload(1, 10)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value("Limite diário de protocolos de Ordem de Serviço atingido."));
        assertThat(contar("ordens_servico")).isEqualTo(1);
        assertThat(contar("historico_status")).isEqualTo(1);
    }

    private void assertErro(String corpo, int statusEsperado, String detalhe) throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(statusEsperado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusEsperado))
                .andExpect(jsonPath("$.detail").value(detalhe))
                .andExpect(jsonPath("$.instance").value("/ordens"));
        assertSemOrdens();
        assertThat(contar("sequencias_protocolo")).isZero();
    }

    private void assertSemOrdens() {
        assertThat(contar("ordens_servico")).isZero();
        assertThat(contar("historico_status")).isZero();
    }

    private long contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
    }

    private String payload(long cliente, long equipamento) {
        return "{\"clienteId\":" + cliente + ",\"equipamentoId\":" + equipamento
                + ",\"descricaoProblema\":\"Notebook não liga.\"}";
    }
}
