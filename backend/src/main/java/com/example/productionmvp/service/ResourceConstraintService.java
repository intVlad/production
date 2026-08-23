package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.exception.PostCapacityExceededException;
import com.example.productionmvp.model.Post;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResourceConstraintService {

    private final PostRepository postRepository;
    private final TaskRepository taskRepository;

    public ResourceConstraintService(PostRepository postRepository, TaskRepository taskRepository) {
        this.postRepository = postRepository;
        this.taskRepository = taskRepository;
    }

    public boolean checkPostCapacity(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
        return post.getCurrentLoad() < post.getMaxCapacity();
    }

    // findByIdLocked (not findById): concurrent starts on different tasks at the same post
    // were a lost-update race - both transactions read the same stale currentLoad, both saw
    // room, both committed, and the post silently ended up overcommitted. The pessimistic lock
    // forces the second transaction to wait and re-read the post's real, post-increment load.
    @Transactional
    public void occupyPost(UUID postId) {
        Post post = postRepository.findByIdLocked(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
        if (post.getCurrentLoad() >= post.getMaxCapacity()) {
            throw new PostCapacityExceededException("Post is at full capacity: " + post.getName());
        }
        post.setCurrentLoad(post.getCurrentLoad() + 1);
        postRepository.save(post);
    }

    @Transactional
    public void releasePost(UUID postId) {
        Post post = postRepository.findByIdLocked(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
        if (post.getCurrentLoad() > 0) {
            post.setCurrentLoad(post.getCurrentLoad() - 1);
            postRepository.save(post);
        }
    }

    public List<Task> getPostQueue(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
        
        return taskRepository.findAll().stream()
                .filter(t -> t.getPost() != null && t.getPost().getId().equals(post.getId()))
                .filter(t -> t.getStatus() == TaskStatus.READY || t.getStatus() == TaskStatus.ASSIGNED || t.getStatus() == TaskStatus.CREATED)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getPostLoad(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
                
        int queueSize = getPostQueue(postId).size();
        
        Map<String, Object> loadInfo = new HashMap<>();
        loadInfo.put("current", post.getCurrentLoad());
        loadInfo.put("max", post.getMaxCapacity());
        loadInfo.put("available", post.getMaxCapacity() - post.getCurrentLoad());
        loadInfo.put("queueSize", queueSize);
        return loadInfo;
    }
}
