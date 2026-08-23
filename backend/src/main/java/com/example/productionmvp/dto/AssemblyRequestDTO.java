package com.example.productionmvp.dto;

import java.util.UUID;

public class AssemblyRequestDTO {
    private UUID productModelId;
    private String code;
    private String name;
    private String category;
    private String parts;
    private Integer normativeTimeMinutes;

    public UUID getProductModelId() { return productModelId; }
    public void setProductModelId(UUID productModelId) { this.productModelId = productModelId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getParts() { return parts; }
    public void setParts(String parts) { this.parts = parts; }

    public Integer getNormativeTimeMinutes() { return normativeTimeMinutes; }
    public void setNormativeTimeMinutes(Integer normativeTimeMinutes) { this.normativeTimeMinutes = normativeTimeMinutes; }
}
