package br.edu.uniesp.assistencia.internal.ordemservico.repository;

import br.edu.uniesp.assistencia.internal.ordemservico.entity.SequenciaProtocoloEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SequenciaProtocoloRepository extends JpaRepository<SequenciaProtocoloEntity, LocalDate> {
    @Modifying
    @Query(value = "INSERT INTO sequencias_protocolo (data_referencia, proximo_valor) "
            + "VALUES (:data, 0) ON CONFLICT DO NOTHING", nativeQuery = true)
    void iniciarDia(@Param("data") LocalDate data);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SequenciaProtocoloEntity s WHERE s.dataReferencia = :data")
    SequenciaProtocoloEntity buscarComBloqueio(@Param("data") LocalDate data);
}
