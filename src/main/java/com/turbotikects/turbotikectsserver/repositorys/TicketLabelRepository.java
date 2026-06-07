package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketLabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketLabelRepository extends JpaRepository<TicketLabelEntity, Long> {

    boolean existsByLabelKey(String labelKey);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketLabelEntity t WHERE t.id IN :ids")
    void deleteAllByIdIn(List<Long> ids);
}
