package com.example.productionmvp.repository;

import com.example.productionmvp.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findBySectionId(UUID sectionId);

    // Serializes concurrent occupyPost/releasePost on the same post (e.g. several workers
    // tapping "Start" on different tasks at the same post at once) so the capacity check-then-
    // increment can't lose an update - without this, concurrent transactions all read the same
    // stale currentLoad, all see room, and all commit, silently overcommitting the post.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdLocked(@Param("id") UUID id);

    @Query("SELECT p FROM Post p WHERE p.currentLoad < p.maxCapacity")
    List<Post> findByCurrentLoadLessThanMaxCapacity();

    @Query("SELECT p FROM Post p WHERE p.currentLoad < p.maxCapacity")
    List<Post> findAvailablePosts();

    Optional<Post> findByName(String name);
}
