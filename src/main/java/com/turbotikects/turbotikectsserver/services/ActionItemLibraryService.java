package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.SaveActionItemLibraryRequestDto;
import com.turbotikects.turbotikectsserver.entitys.ActionItemLibraryEntity;
import com.turbotikects.turbotikectsserver.repositorys.ActionItemLibraryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

// Reusable, cross-template catalog of workflow action items — see WorkflowDesignerModal's "Add
// from Library" picker (copies an entry's type/typeConfig into a fresh node, independent of this
// row afterward) and AiWorkflowBuilderPage (now saves its drafts here instead of into one template).
@Service
public class ActionItemLibraryService {

    private static final Set<String> VALID_TYPES = Set.of("task", "external_api", "mcp_tool");

    private final ActionItemLibraryRepository repo;

    public ActionItemLibraryService(ActionItemLibraryRepository repo) {
        this.repo = repo;
    }

    public List<ActionItemLibraryEntity> getAll(String type) {
        return type == null || type.isBlank()
                ? repo.findAllByOrderByUpdatedAtDesc()
                : repo.findByTypeOrderByUpdatedAtDesc(type);
    }

    @Transactional
    public ActionItemLibraryEntity create(SaveActionItemLibraryRequestDto req) {
        validate(req);
        ActionItemLibraryEntity entity = new ActionItemLibraryEntity();
        apply(entity, req);
        return repo.save(entity);
    }

    @Transactional
    public ActionItemLibraryEntity update(Long id, SaveActionItemLibraryRequestDto req) {
        ActionItemLibraryEntity entity = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        validate(req);
        apply(entity, req);
        return repo.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repo.deleteById(id);
    }

    private void validate(SaveActionItemLibraryRequestDto req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (!VALID_TYPES.contains(req.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be one of " + VALID_TYPES);
        }
    }

    private void apply(ActionItemLibraryEntity entity, SaveActionItemLibraryRequestDto req) {
        entity.setName(req.getName().trim());
        entity.setType(req.getType());
        entity.setTypeConfig(req.getTypeConfig());
        entity.setSource("ai".equals(req.getSource()) ? "ai" : "manual");
    }
}
