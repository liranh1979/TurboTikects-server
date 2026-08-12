package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "kb_article_labels")
@Data
public class KbArticleLabelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_article_id", nullable = false)
    private Long kbArticleId;

    @Column(name = "label_id", nullable = false)
    private Long labelId;
}
