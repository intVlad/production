package com.example.productionmvp.dto;

import java.util.List;
import java.util.Map;

public class DashboardResponseDTO {
    private long productsInProduction;
    private long productsReady;
    private Map<String, Long> productsByStage;
    
    private List<Map<String, Object>> activeTasks;
    private long activeTasksCount;
    private long overdueTasks;
    private long blockedTasks;
    
    private long activeBatches;
    private long activeOutsource;
    private long overdueOutsource;
    
    private long activeWorkers;
    
    private double totalHoursSpent;
    private double normativeHours;
    
    private List<Map<String, Object>> postLoads;
    
    private long materialDeficits;
    private long defectsToday;
    private long activeSeries;
    
    private List<Map<String, Object>> recentHistory;

    public DashboardResponseDTO() {}

    public long getProductsInProduction() { return productsInProduction; }
    public void setProductsInProduction(long productsInProduction) { this.productsInProduction = productsInProduction; }
    public long getProductsReady() { return productsReady; }
    public void setProductsReady(long productsReady) { this.productsReady = productsReady; }
    public Map<String, Long> getProductsByStage() { return productsByStage; }
    public void setProductsByStage(Map<String, Long> productsByStage) { this.productsByStage = productsByStage; }
    
    public List<Map<String, Object>> getActiveTasks() { return activeTasks; }
    public void setActiveTasks(List<Map<String, Object>> activeTasks) { this.activeTasks = activeTasks; }
    public long getActiveTasksCount() { return activeTasksCount; }
    public void setActiveTasksCount(long activeTasksCount) { this.activeTasksCount = activeTasksCount; }
    public long getOverdueTasks() { return overdueTasks; }
    public void setOverdueTasks(long overdueTasks) { this.overdueTasks = overdueTasks; }
    public long getBlockedTasks() { return blockedTasks; }
    public void setBlockedTasks(long blockedTasks) { this.blockedTasks = blockedTasks; }
    
    public long getActiveBatches() { return activeBatches; }
    public void setActiveBatches(long activeBatches) { this.activeBatches = activeBatches; }
    public long getActiveOutsource() { return activeOutsource; }
    public void setActiveOutsource(long activeOutsource) { this.activeOutsource = activeOutsource; }
    public long getOverdueOutsource() { return overdueOutsource; }
    public void setOverdueOutsource(long overdueOutsource) { this.overdueOutsource = overdueOutsource; }
    
    public long getActiveWorkers() { return activeWorkers; }
    public void setActiveWorkers(long activeWorkers) { this.activeWorkers = activeWorkers; }
    
    public double getTotalHoursSpent() { return totalHoursSpent; }
    public void setTotalHoursSpent(double totalHoursSpent) { this.totalHoursSpent = totalHoursSpent; }
    public double getNormativeHours() { return normativeHours; }
    public void setNormativeHours(double normativeHours) { this.normativeHours = normativeHours; }
    
    public List<Map<String, Object>> getPostLoads() { return postLoads; }
    public void setPostLoads(List<Map<String, Object>> postLoads) { this.postLoads = postLoads; }
    
    public long getMaterialDeficits() { return materialDeficits; }
    public void setMaterialDeficits(long materialDeficits) { this.materialDeficits = materialDeficits; }
    public long getDefectsToday() { return defectsToday; }
    public void setDefectsToday(long defectsToday) { this.defectsToday = defectsToday; }
    public long getActiveSeries() { return activeSeries; }
    public void setActiveSeries(long activeSeries) { this.activeSeries = activeSeries; }
    
    public List<Map<String, Object>> getRecentHistory() { return recentHistory; }
    public void setRecentHistory(List<Map<String, Object>> recentHistory) { this.recentHistory = recentHistory; }
}
