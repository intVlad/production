package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Pallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String qrCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_product_id")
    private ProductInstance ownerProduct;

    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_post_id")
    private Post currentPost;

    @Enumerated(EnumType.STRING)
    private PalletStatus status = PalletStatus.EMPTY;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany
    @JoinTable(
        name = "pallet_assembly_instances",
        joinColumns = @JoinColumn(name = "pallet_id"),
        inverseJoinColumns = @JoinColumn(name = "assembly_instance_id")
    )
    private List<AssemblyInstance> assemblyInstances = new ArrayList<>();

    @OneToMany(mappedBy = "pallet", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<PalletMovement> movements = new ArrayList<>();

    public Pallet() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public ProductInstance getOwnerProduct() { return ownerProduct; }
    public void setOwnerProduct(ProductInstance ownerProduct) { this.ownerProduct = ownerProduct; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Post getCurrentPost() { return currentPost; }
    public void setCurrentPost(Post currentPost) { this.currentPost = currentPost; }

    public PalletStatus getStatus() { return status; }
    public void setStatus(PalletStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<AssemblyInstance> getAssemblyInstances() { return assemblyInstances; }
    public void setAssemblyInstances(List<AssemblyInstance> assemblyInstances) { this.assemblyInstances = assemblyInstances; }

    public List<PalletMovement> getMovements() { return movements; }
    public void setMovements(List<PalletMovement> movements) { this.movements = movements; }
}
