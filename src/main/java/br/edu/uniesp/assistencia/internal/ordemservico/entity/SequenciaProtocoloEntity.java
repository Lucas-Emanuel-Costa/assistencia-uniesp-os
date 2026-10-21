package br.edu.uniesp.assistencia.internal.ordemservico.entity;

import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(name = "sequencias_protocolo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SequenciaProtocoloEntity {
    @Id
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Column(name = "proximo_valor", nullable = false)
    private int proximoValor;

    public String proximoProtocolo() {
        if (proximoValor > 0xFFFF) {
            throw new RegraNegocioException("Limite diário de protocolos de Ordem de Serviço atingido.");
        }
        return "OS-" + dataReferencia.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format(Locale.ROOT, "%04X", proximoValor++);
    }
}
