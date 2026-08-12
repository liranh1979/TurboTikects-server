package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.KbArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbArticleRepository extends JpaRepository<KbArticleEntity, Long> {

    List<KbArticleEntity> findByVisibilityOrderByCreatedAtDesc(String visibility);

    List<KbArticleEntity> findAllByOrderByCreatedAtDesc();

    @Query(value = "SELECT * FROM kb_articles WHERE MATCH(title, body) AGAINST (:q IN BOOLEAN MODE) " +
                   "ORDER BY MATCH(title, body) AGAINST (:q IN BOOLEAN MODE) DESC LIMIT :limit",
           nativeQuery = true)
    List<KbArticleEntity> fullTextSearch(@Param("q") String query, @Param("limit") int limit);
}
