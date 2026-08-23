package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import java.util.Set;
import java.util.HashSet;

@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Worker {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String role;
    private String position;

    @Enumerated(EnumType.STRING)
    private SystemRole systemRole = SystemRole.WORKER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Post post;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String pinHash;

    @Column(unique = true)
    private String qrBadgeCode;

    private Long totalWorkedMinutes = 0L;

    // ТЗ §2.1: "доступні операції" - which operations this worker is qualified to perform.
    // Informational/descriptive like Operation.requiredQualification, not enforced as a hard
    // assignment gate anywhere - ТЗ doesn't describe a rule that blocks task pickup for an
    // unqualified worker, only that the system should record this per worker.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "worker_qualified_operations",
        joinColumns = @JoinColumn(name = "worker_id"),
        inverseJoinColumns = @JoinColumn(name = "operation_id")
    )
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "assembly", "productModel", "dependsOnOperation", "requiredMaterials"})
    private Set<Operation> qualifiedOperations = new HashSet<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public SystemRole getSystemRole() { return systemRole != null ? systemRole : SystemRole.WORKER; }
    public void setSystemRole(SystemRole systemRole) { this.systemRole = systemRole; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Section getSection() { return section; }
    public void setSection(Section section) { this.section = section; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Post getPost() { return post; }
    public void setPost(Post post) { this.post = post; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    // Same reasoning as pinHash: this is a bearer credential (POST /auth/login/qr accepts it
    // with no other proof of identity), so it must never round-trip through a JSON response -
    // otherwise anyone who can read one worker's record (e.g. GET /api/workers) can log in as
    // any other worker whose badge code they can see.
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getQrBadgeCode() { return qrBadgeCode; }
    public void setQrBadgeCode(String qrBadgeCode) { this.qrBadgeCode = qrBadgeCode; }
    public Long getTotalWorkedMinutes() { return totalWorkedMinutes; }
    public void setTotalWorkedMinutes(Long totalWorkedMinutes) { this.totalWorkedMinutes = totalWorkedMinutes; }
    public Set<Operation> getQualifiedOperations() { return qualifiedOperations; }
    public void setQualifiedOperations(Set<Operation> qualifiedOperations) { this.qualifiedOperations = qualifiedOperations; }

    // Plain-string convenience getters for the workers table (ТЗ §2.1 lists section/post as
    // worker attributes to show) - deliberately NOT exposing the raw section/post objects,
    // since Section.getWorkers() back-references every Worker in it, which would recurse
    // straight into a StackOverflow the moment Jackson tried to serialize this entity.
    public String getSectionName() { return section != null ? section.getName() : null; }
    public String getPostName() { return post != null ? post.getName() : null; }
}
