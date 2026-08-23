package com.example.productionmvp.dto;

import java.util.Map;
import java.util.UUID;

public class BatchDistributeRequestDTO {
    private Map<UUID, Integer> distribution;

    public Map<UUID, Integer> getDistribution() { return distribution; }
    public void setDistribution(Map<UUID, Integer> distribution) { this.distribution = distribution; }
}
