package com.example.productionmvp.dto;

import java.util.UUID;

public class PostRequestDTO {
    private String name;
    private Integer maxCapacity;
    private UUID sectionId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
}
