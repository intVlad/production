package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.exception.PostCapacityExceededException;
import com.example.productionmvp.model.Post;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceConstraintServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ResourceConstraintService resourceConstraintService;

    private Post post;
    private UUID postId;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        post = new Post();
        post.setId(postId);
        post.setName("Test Post");
        post.setMaxCapacity(5);
        post.setCurrentLoad(2);
    }

    @Test
    void checkPostCapacity_HasCapacity_ReturnsTrue() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        boolean result = resourceConstraintService.checkPostCapacity(postId);
        
        assertTrue(result);
        verify(postRepository).findById(postId);
    }

    @Test
    void checkPostCapacity_NoCapacity_ReturnsFalse() {
        post.setCurrentLoad(5);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        boolean result = resourceConstraintService.checkPostCapacity(postId);
        
        assertFalse(result);
    }
    
    @Test
    void checkPostCapacity_PostNotFound_ThrowsException() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        
        assertThrows(EntityNotFoundException.class, () -> {
            resourceConstraintService.checkPostCapacity(postId);
        });
    }

    @Test
    void occupyPost_HasCapacity_IncrementsLoadAndSaves() {
        when(postRepository.findByIdLocked(postId)).thenReturn(Optional.of(post));

        resourceConstraintService.occupyPost(postId);

        assertEquals(3, post.getCurrentLoad());
        verify(postRepository).save(post);
    }

    @Test
    void occupyPost_AtMaxCapacity_ThrowsException() {
        post.setCurrentLoad(5);
        when(postRepository.findByIdLocked(postId)).thenReturn(Optional.of(post));

        assertThrows(PostCapacityExceededException.class, () -> {
            resourceConstraintService.occupyPost(postId);
        });
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void occupyPost_PostNotFound_ThrowsException() {
        when(postRepository.findByIdLocked(postId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            resourceConstraintService.occupyPost(postId);
        });
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void releasePost_HasLoad_DecrementsLoadAndSaves() {
        when(postRepository.findByIdLocked(postId)).thenReturn(Optional.of(post));

        resourceConstraintService.releasePost(postId);

        assertEquals(1, post.getCurrentLoad());
        verify(postRepository).save(post);
    }

    @Test
    void releasePost_NoLoad_DoesNotSave() {
        post.setCurrentLoad(0);
        when(postRepository.findByIdLocked(postId)).thenReturn(Optional.of(post));

        resourceConstraintService.releasePost(postId);

        assertEquals(0, post.getCurrentLoad());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void releasePost_PostNotFound_ThrowsException() {
        when(postRepository.findByIdLocked(postId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            resourceConstraintService.releasePost(postId);
        });
        verify(postRepository, never()).save(any(Post.class));
    }
    
    @Test
    void getPostQueue_ValidPost_ReturnsFilteredTasks() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        Task task1 = new Task();
        task1.setId(UUID.randomUUID());
        task1.setPost(post);
        task1.setStatus(TaskStatus.READY);
        
        Task task2 = new Task();
        task2.setId(UUID.randomUUID());
        task2.setPost(post);
        task2.setStatus(TaskStatus.ASSIGNED);
        
        Task task3 = new Task();
        task3.setId(UUID.randomUUID());
        task3.setPost(post);
        task3.setStatus(TaskStatus.CREATED);
        
        Task task4 = new Task(); // Different status
        task4.setId(UUID.randomUUID());
        task4.setPost(post);
        task4.setStatus(TaskStatus.IN_PROGRESS);
        
        Post otherPost = new Post();
        otherPost.setId(UUID.randomUUID());
        Task task5 = new Task(); // Different post
        task5.setId(UUID.randomUUID());
        task5.setPost(otherPost);
        task5.setStatus(TaskStatus.READY);
        
        Task task6 = new Task(); // Null post
        task6.setId(UUID.randomUUID());
        task6.setStatus(TaskStatus.READY);
        
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2, task3, task4, task5, task6));
        
        List<Task> queue = resourceConstraintService.getPostQueue(postId);
        
        assertEquals(3, queue.size());
        assertTrue(queue.contains(task1));
        assertTrue(queue.contains(task2));
        assertTrue(queue.contains(task3));
    }

    @Test
    void getPostQueue_PostNotFound_ThrowsException() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        
        assertThrows(EntityNotFoundException.class, () -> {
            resourceConstraintService.getPostQueue(postId);
        });
    }

    @Test
    void getPostLoad_ValidPost_ReturnsLoadInfo() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        Task task1 = new Task();
        task1.setId(UUID.randomUUID());
        task1.setPost(post);
        task1.setStatus(TaskStatus.READY);
        
        when(taskRepository.findAll()).thenReturn(Collections.singletonList(task1));
        
        Map<String, Object> loadInfo = resourceConstraintService.getPostLoad(postId);
        
        assertEquals(2, loadInfo.get("current"));
        assertEquals(5, loadInfo.get("max"));
        assertEquals(3, loadInfo.get("available"));
        assertEquals(1, loadInfo.get("queueSize"));
    }

    @Test
    void getPostLoad_PostNotFound_ThrowsException() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        
        assertThrows(EntityNotFoundException.class, () -> {
            resourceConstraintService.getPostLoad(postId);
        });
    }
}
