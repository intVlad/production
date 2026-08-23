package com.example.productionmvp.controller;

import com.example.productionmvp.model.Post;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.Section;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.SectionRepository;
import com.example.productionmvp.service.ResourceConstraintService;
import com.example.productionmvp.service.PrioritizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = "*")
public class PostController {
    private final PostRepository postRepository;
    private final SectionRepository sectionRepository;
    private final ResourceConstraintService resourceConstraintService;
    private final PrioritizationService prioritizationService;

    public PostController(PostRepository postRepository, SectionRepository sectionRepository, 
                          ResourceConstraintService resourceConstraintService, PrioritizationService prioritizationService) {
        this.postRepository = postRepository;
        this.sectionRepository = sectionRepository;
        this.resourceConstraintService = resourceConstraintService;
        this.prioritizationService = prioritizationService;
    }

    @GetMapping
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Post> getPostById(@PathVariable UUID id) {
        return postRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/queue")
    public ResponseEntity<List<Task>> getPostQueue(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceConstraintService.getPostQueue(id));
    }

    @GetMapping("/{id}/load")
    public ResponseEntity<Map<String, Object>> getPostLoad(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceConstraintService.getPostLoad(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Post> createPost(@RequestBody com.example.productionmvp.dto.PostRequestDTO body) {
        Post post = new Post();
        if (body.getName() != null) post.setName(body.getName());
        if (body.getMaxCapacity() != null) post.setMaxCapacity(body.getMaxCapacity());
        
        if (body.getSectionId() != null) {
            Section section = sectionRepository.findById(body.getSectionId()).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Дільницю не знайдено"));
            post.setSection(section);
        }
        
        return ResponseEntity.ok(postRepository.save(post));
    }

    @GetMapping("/{id}/available-tasks")
    public ResponseEntity<List<Task>> getAvailableTasks(@PathVariable UUID id, @RequestParam UUID workerId) {
        return ResponseEntity.ok(prioritizationService.getAvailableTasksForPost(id, workerId));
    }
}
