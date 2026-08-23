package com.example.productionmvp.dto;

import com.example.productionmvp.model.SystemRole;

import java.util.Set;
import java.util.UUID;

public class WorkerRequestDTO {
    private String name;
    private String role;
    private String position;
    private SystemRole systemRole;
    private UUID sectionId;
    private String pin;
    private Set<UUID> qualifiedOperationIds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public SystemRole getSystemRole() { return systemRole; }
    public void setSystemRole(SystemRole systemRole) { this.systemRole = systemRole; }

    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public Set<UUID> getQualifiedOperationIds() { return qualifiedOperationIds; }
    public void setQualifiedOperationIds(Set<UUID> qualifiedOperationIds) { this.qualifiedOperationIds = qualifiedOperationIds; }
}
