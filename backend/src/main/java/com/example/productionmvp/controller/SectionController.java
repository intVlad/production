package com.example.productionmvp.controller;

import com.example.productionmvp.model.Section;
import com.example.productionmvp.model.Post;
import com.example.productionmvp.repository.SectionRepository;
import com.example.productionmvp.repository.PostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sections")
@CrossOrigin(origins = "*")
public class SectionController {
    private final SectionRepository sectionRepository;
    private final PostRepository postRepository;

    public SectionController(SectionRepository sectionRepository, PostRepository postRepository) {
        this.sectionRepository = sectionRepository;
        this.postRepository = postRepository;
    }

    @GetMapping
    public ResponseEntity<List<Section>> getAllSections() {
        return ResponseEntity.ok(sectionRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Section> getSectionById(@PathVariable UUID id) {
        return sectionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Section> createSection(@RequestBody com.example.productionmvp.dto.SectionRequestDTO body) {
        Section section = new Section();
        if (body.getName() != null) section.setName(body.getName());
        if (body.getLocation() != null) section.setLocation(body.getLocation());
        if (body.getArea() != null) section.setArea(body.getArea());
        return ResponseEntity.ok(sectionRepository.save(section));
    }

    @GetMapping("/{id}/posts")
    public ResponseEntity<List<Post>> getPostsInSection(@PathVariable UUID id) {
        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> p.getSection() != null && p.getSection().getId().equals(id))
                .toList();
        return ResponseEntity.ok(posts);
    }
}
