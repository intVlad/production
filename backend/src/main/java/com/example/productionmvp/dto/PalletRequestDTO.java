package com.example.productionmvp.dto;

import java.util.UUID;

public class PalletRequestDTO {
    private UUID productId;
    private UUID productInstanceId;
    private String category;
    private UUID assemblyInstanceId;
    private UUID toPostId;
    private UUID movedByWorkerId;

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getProductInstanceId() { return productInstanceId; }
    public void setProductInstanceId(UUID productInstanceId) { this.productInstanceId = productInstanceId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public UUID getAssemblyInstanceId() { return assemblyInstanceId; }
    public void setAssemblyInstanceId(UUID assemblyInstanceId) { this.assemblyInstanceId = assemblyInstanceId; }

    public UUID getToPostId() { return toPostId; }
    public void setToPostId(UUID toPostId) { this.toPostId = toPostId; }

    public UUID getMovedByWorkerId() { return movedByWorkerId; }
    public void setMovedByWorkerId(UUID movedByWorkerId) { this.movedByWorkerId = movedByWorkerId; }
}
