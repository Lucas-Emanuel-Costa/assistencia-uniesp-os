package br.edu.uniesp.assistencia.internal.ordemservico.service;

import br.edu.uniesp.assistencia.internal.cliente.entity.ClienteEntity;
import br.edu.uniesp.assistencia.internal.cliente.repository.ClienteRepository;
import br.edu.uniesp.assistencia.internal.equipamento.entity.EquipamentoEntity;
import br.edu.uniesp.assistencia.internal.equipamento.repository.EquipamentoRepository;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoRequest;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoResponse;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.OrdemServicoEntity;
import br.edu.uniesp.assistencia.internal.ordemservico.mapper.OrdemServicoMapper;
import br.edu.uniesp.assistencia.internal.ordemservico.repository.OrdemServicoRepository;
import br.edu.uniesp.assistencia.shared.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private final OrdemServicoRepository ordemServicoRepository;
    private final ClienteRepository clienteRepository;
    private final EquipamentoRepository equipamentoRepository;
    private final GeradorProtocoloService geradorProtocoloService;

    @Transactional
    public CriarOrdemServicoResponse abrirOrdemServico(CriarOrdemServicoRequest request) {
        ClienteEntity cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", request.clienteId()));
        EquipamentoEntity equipamento = equipamentoRepository.findById(request.equipamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento", request.equipamentoId()));

        LocalDateTime dataAbertura = LocalDateTime.now();
        String protocolo = geradorProtocoloService.gerar(dataAbertura.toLocalDate());
        // Uma regra violada também desfaz a reserva do protocolo nesta transação.
        OrdemServicoEntity ordemServicoEntity = OrdemServicoMapper.paraEntidade(
                request, protocolo, cliente, equipamento, dataAbertura);
        ordemServicoRepository.save(ordemServicoEntity);
        return OrdemServicoMapper.paraResposta(ordemServicoEntity);
    }

}
