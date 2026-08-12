package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.KbArticleDetailDto;
import com.turbotikects.turbotikectsserver.dto.KbArticleListDto;
import com.turbotikects.turbotikectsserver.dto.KbSuggestResultDto;
import com.turbotikects.turbotikectsserver.dto.SaveKbArticleRequestDto;
import com.turbotikects.turbotikectsserver.dto.TicketLabelDto;
import com.turbotikects.turbotikectsserver.entitys.KbArticleEntity;
import com.turbotikects.turbotikectsserver.entitys.KbArticleLabelEntity;
import com.turbotikects.turbotikectsserver.entitys.KbCategoryEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketKbLinkEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.KbArticleLabelRepository;
import com.turbotikects.turbotikectsserver.repositorys.KbArticleRepository;
import com.turbotikects.turbotikectsserver.repositorys.KbCategoryRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketKbLinkRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KbArticleService {

    private static final int SUGGEST_LIMIT = 3;
    private static final int SEARCH_LIMIT = 20;

    private final KbArticleRepository articleRepo;
    private final KbCategoryRepository categoryRepo;
    private final UserRepository userRepo;
    private final TicketKbLinkRepository ticketKbLinkRepo;
    private final KbArticleLabelRepository articleLabelRepo;
    private final TicketLabelService ticketLabelService;

    public KbArticleService(KbArticleRepository articleRepo, KbCategoryRepository categoryRepo,
                             UserRepository userRepo, TicketKbLinkRepository ticketKbLinkRepo,
                             KbArticleLabelRepository articleLabelRepo, TicketLabelService ticketLabelService) {
        this.articleRepo = articleRepo;
        this.categoryRepo = categoryRepo;
        this.userRepo = userRepo;
        this.ticketKbLinkRepo = ticketKbLinkRepo;
        this.articleLabelRepo = articleLabelRepo;
        this.ticketLabelService = ticketLabelService;
    }

    public List<KbArticleListDto> getLinkedArticles(Long ticketId) {
        Map<Long, TicketLabelDto> labelsById = labelLookup();
        return ticketKbLinkRepo.findByTicketId(ticketId).stream()
                .map(link -> articleRepo.findById(link.getArticleId()).orElse(null))
                .filter(a -> a != null)
                .map(a -> toListDto(a, resolveLabels(a.getId(), labelsById)))
                .collect(Collectors.toList());
    }

    public void linkArticle(Long ticketId, Long articleId) {
        if (ticketKbLinkRepo.findByTicketIdAndArticleId(ticketId, articleId).isPresent()) return;
        if (!articleRepo.existsById(articleId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
        }
        TicketKbLinkEntity link = new TicketKbLinkEntity();
        link.setTicketId(ticketId);
        link.setArticleId(articleId);
        ticketKbLinkRepo.save(link);
    }

    public void unlinkArticle(Long ticketId, Long articleId) {
        ticketKbLinkRepo.deleteByTicketIdAndArticleId(ticketId, articleId);
    }

    public List<KbArticleListDto> getAll(boolean includeInternal) {
        List<KbArticleEntity> articles = includeInternal
                ? articleRepo.findAllByOrderByCreatedAtDesc()
                : articleRepo.findByVisibilityOrderByCreatedAtDesc("public");
        Map<Long, TicketLabelDto> labelsById = labelLookup();
        return articles.stream().map(a -> toListDto(a, resolveLabels(a.getId(), labelsById))).collect(Collectors.toList());
    }

    public KbArticleDetailDto getById(Long id, boolean includeInternal) {
        KbArticleEntity entity = articleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
        if (!includeInternal && !"public".equals(entity.getVisibility())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Article is not public");
        }
        entity.setViewCount(entity.getViewCount() + 1);
        entity = articleRepo.save(entity);
        return toDetailDto(entity, resolveLabels(entity.getId(), labelLookup()));
    }

    public List<KbArticleListDto> search(String q, boolean includeInternal) {
        if (q == null || q.isBlank()) return List.of();
        List<KbArticleEntity> results = articleRepo.fullTextSearch(q, SEARCH_LIMIT);
        Map<Long, TicketLabelDto> labelsById = labelLookup();
        return results.stream()
                .filter(a -> includeInternal || "public".equals(a.getVisibility()))
                .map(a -> toListDto(a, resolveLabels(a.getId(), labelsById)))
                .collect(Collectors.toList());
    }

    public List<KbSuggestResultDto> suggest(String q, boolean includeInternal) {
        if (q == null || q.isBlank()) return List.of();
        return articleRepo.fullTextSearch(q, SUGGEST_LIMIT * 2).stream()
                .filter(a -> includeInternal || "public".equals(a.getVisibility()))
                .limit(SUGGEST_LIMIT)
                .map(a -> {
                    KbSuggestResultDto dto = new KbSuggestResultDto();
                    dto.setId(a.getId());
                    dto.setTitle(a.getTitle());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public KbArticleDetailDto create(SaveKbArticleRequestDto dto, Integer authorId) {
        KbArticleEntity entity = new KbArticleEntity();
        applyRequest(entity, dto);
        entity.setAuthorId(authorId);
        entity = articleRepo.save(entity);
        setLabels(entity.getId(), dto.getLabelIds());
        return toDetailDto(entity, resolveLabels(entity.getId(), labelLookup()));
    }

    @Transactional
    public KbArticleDetailDto update(Long id, SaveKbArticleRequestDto dto) {
        KbArticleEntity entity = articleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
        applyRequest(entity, dto);
        entity = articleRepo.save(entity);
        setLabels(entity.getId(), dto.getLabelIds());
        return toDetailDto(entity, resolveLabels(entity.getId(), labelLookup()));
    }

    public void delete(Long id) {
        if (!articleRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
        }
        articleRepo.deleteById(id);
    }

    public void recordFeedback(Long id, boolean helpful) {
        KbArticleEntity entity = articleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
        if (helpful) {
            entity.setHelpfulCount(entity.getHelpfulCount() + 1);
        } else {
            entity.setNotHelpfulCount(entity.getNotHelpfulCount() + 1);
        }
        articleRepo.save(entity);
    }

    /** Replaces an article's label assignments wholesale — simplest correct approach for a
     *  small per-article tag set, same delete-then-insert shape used elsewhere in this app for
     *  small join-table rewrites. */
    private void setLabels(Long articleId, List<Long> labelIds) {
        articleLabelRepo.deleteByKbArticleId(articleId);
        if (labelIds == null || labelIds.isEmpty()) return;
        for (Long labelId : labelIds) {
            KbArticleLabelEntity link = new KbArticleLabelEntity();
            link.setKbArticleId(articleId);
            link.setLabelId(labelId);
            articleLabelRepo.save(link);
        }
    }

    private Map<Long, TicketLabelDto> labelLookup() {
        return ticketLabelService.getAll().stream().collect(Collectors.toMap(TicketLabelDto::getId, l -> l));
    }

    private List<TicketLabelDto> resolveLabels(Long articleId, Map<Long, TicketLabelDto> labelsById) {
        return articleLabelRepo.findByKbArticleId(articleId).stream()
                .map(link -> labelsById.get(link.getLabelId()))
                .filter(l -> l != null)
                .collect(Collectors.toList());
    }

    private void applyRequest(KbArticleEntity entity, SaveKbArticleRequestDto dto) {
        entity.setTitle(dto.getTitle());
        entity.setBody(dto.getBody());
        entity.setCategoryId(dto.getCategoryId());
        entity.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "internal");
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepo.findById(categoryId).map(KbCategoryEntity::getName).orElse(null);
    }

    private KbArticleListDto toListDto(KbArticleEntity e, List<TicketLabelDto> labels) {
        KbArticleListDto dto = new KbArticleListDto();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setCategoryId(e.getCategoryId());
        dto.setCategoryName(categoryName(e.getCategoryId()));
        dto.setLabels(labels);
        dto.setVisibility(e.getVisibility());
        dto.setViewCount(e.getViewCount());
        dto.setHelpfulCount(e.getHelpfulCount());
        dto.setNotHelpfulCount(e.getNotHelpfulCount());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private KbArticleDetailDto toDetailDto(KbArticleEntity e, List<TicketLabelDto> labels) {
        KbArticleDetailDto dto = new KbArticleDetailDto();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setBody(e.getBody());
        dto.setCategoryId(e.getCategoryId());
        dto.setCategoryName(categoryName(e.getCategoryId()));
        dto.setLabels(labels);
        dto.setVisibility(e.getVisibility());
        dto.setViewCount(e.getViewCount());
        dto.setHelpfulCount(e.getHelpfulCount());
        dto.setNotHelpfulCount(e.getNotHelpfulCount());
        if (e.getAuthorId() != null) {
            dto.setAuthorName(userRepo.findById(e.getAuthorId().longValue())
                    .map(UserEntity::getDisplayName).orElse(null));
        }
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }
}
