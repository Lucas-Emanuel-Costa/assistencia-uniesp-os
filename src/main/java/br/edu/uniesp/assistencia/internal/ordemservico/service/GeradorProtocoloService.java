package br.edu.uniesp.assistencia.internal.ordemservico.service;

import br.edu.uniesp.assistencia.internal.ordemservico.repository.SequenciaProtocoloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GeradorProtocoloService {
    private final SequenciaProtocoloRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String gerar(LocalDate data) {
        repository.iniciarDia(data);
        // O bloqueio é mantido até o commit da OS e de seu histórico.
        return repository.buscarComBloqueio(data).proximoProtocolo();
    }
}
