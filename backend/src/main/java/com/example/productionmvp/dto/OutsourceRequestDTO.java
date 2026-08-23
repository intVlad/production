package com.example.productionmvp.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class OutsourceRequestDTO {
    private String partner;
    private String workType;
    private List<UUID> assemblyInstanceIds;
    private LocalDate expectedReturnDate;
    private UUID receivedByWorkerId;

    public String getPartner() { return partner; }
    public void setPartner(String partner) { this.partner = partner; }

    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }

    public List<UUID> getAssemblyInstanceIds() { return assemblyInstanceIds; }
    public void setAssemblyInstanceIds(List<UUID> assemblyInstanceIds) { this.assemblyInstanceIds = assemblyInstanceIds; }

    public LocalDate getExpectedReturnDate() { return expectedReturnDate; }
    public void setExpectedReturnDate(LocalDate expectedReturnDate) { this.expectedReturnDate = expectedReturnDate; }

    public UUID getReceivedByWorkerId() { return receivedByWorkerId; }
    public void setReceivedByWorkerId(UUID receivedByWorkerId) { this.receivedByWorkerId = receivedByWorkerId; }
}
