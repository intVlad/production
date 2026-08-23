package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "outsource_records")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OutsourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String partner;
    private String workType;
    private LocalDateTime sentDate;
    private LocalDateTime expectedReturnDate;
    private LocalDateTime actualReturnDate;

    @Enumerated(EnumType.STRING)
    private OutsourceStatus status = OutsourceStatus.IN_TRANSIT;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    private Worker receivedBy;

    @ManyToMany
    @JoinTable(
        name = "outsource_items",
        joinColumns = @JoinColumn(name = "outsource_record_id"),
        inverseJoinColumns = @JoinColumn(name = "assembly_instance_id")
    )
    private List<AssemblyInstance> items = new ArrayList<>();

    public OutsourceRecord() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPartner() { return partner; }
    public void setPartner(String partner) { this.partner = partner; }

    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }

    public LocalDateTime getSentDate() { return sentDate; }
    public void setSentDate(LocalDateTime sentDate) { this.sentDate = sentDate; }

    public LocalDateTime getExpectedReturnDate() { return expectedReturnDate; }
    public void setExpectedReturnDate(LocalDateTime expectedReturnDate) { this.expectedReturnDate = expectedReturnDate; }

    public LocalDateTime getActualReturnDate() { return actualReturnDate; }
    public void setActualReturnDate(LocalDateTime actualReturnDate) { this.actualReturnDate = actualReturnDate; }

    public OutsourceStatus getStatus() { return status; }
    public void setStatus(OutsourceStatus status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public List<AssemblyInstance> getItems() { return items; }
    public void setItems(List<AssemblyInstance> items) { this.items = items; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Worker getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Worker receivedBy) { this.receivedBy = receivedBy; }
}
