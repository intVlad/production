package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pallet_movements")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PalletMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id")
    private Pallet pallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_post_id")
    private Post fromPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_post_id")
    private Post toPost;

    private LocalDateTime movedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moved_by_worker_id")
    private Worker movedBy;

    public PalletMovement() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Pallet getPallet() { return pallet; }
    public void setPallet(Pallet pallet) { this.pallet = pallet; }

    public Post getFromPost() { return fromPost; }
    public void setFromPost(Post fromPost) { this.fromPost = fromPost; }

    public Post getToPost() { return toPost; }
    public void setToPost(Post toPost) { this.toPost = toPost; }

    public LocalDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(LocalDateTime movedAt) { this.movedAt = movedAt; }

    public Worker getMovedBy() { return movedBy; }
    public void setMovedBy(Worker movedBy) { this.movedBy = movedBy; }
}
