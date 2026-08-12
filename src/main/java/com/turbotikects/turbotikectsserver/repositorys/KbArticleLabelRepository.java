package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.KbArticleLabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface KbArticleLabelRepository extends JpaRepository<KbArticleLabelEntity, Long> {
    List<KbArticleLabelEntity> findByKbArticleId(Long kbArticleId);
    List<KbArticleLabelEntity> findByKbArticleIdIn(List<Long> kbArticleIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM KbArticleLabelEntity l WHERE l.kbArticleId = :kbArticleId")
    void deleteByKbArticleId(@Param("kbArticleId") Long kbArticleId);
}
