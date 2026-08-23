package com.example.productionmvp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;
    private Integer normativeTimeMinutes;
    private String requiredQualification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Post post;

    private String equipment;
    private String tools;
    private Double materialQuantityPerUnit;
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    private OperationType type = OperationType.INDIVIDUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "operations"})
    private Assembly assembly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_model_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "assemblies"})
    private ProductModel productModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_operation_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "dependsOnOperation", "assembly", "productModel", "requiredMaterials"})
    private Operation dependsOnOperation;

    @ManyToMany
    @JoinTable(
        name = "operation_materials",
        joinColumns = @JoinColumn(name = "operation_id"),
        inverseJoinColumns = @JoinColumn(name = "material_id")
    )
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Set<Material> requiredMaterials = new HashSet<>();

    public Operation() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getNormativeTimeMinutes() { return normativeTimeMinutes; }
    public void setNormativeTimeMinutes(Integer normativeTimeMinutes) { this.normativeTimeMinutes = normativeTimeMinutes; }

    public String getRequiredQualification() { return requiredQualification; }
    public void setRequiredQualification(String requiredQualification) { this.requiredQualification = requiredQualification; }

    public Section getSection() { return section; }
    public void setSection(Section section) { this.section = section; }

    public Post getPost() { return post; }
    public void setPost(Post post) { this.post = post; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }

    public Double getMaterialQuantityPerUnit() { return materialQuantityPerUnit; }
    public void setMaterialQuantityPerUnit(Double materialQuantityPerUnit) { this.materialQuantityPerUnit = materialQuantityPerUnit; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public OperationType getType() { return type; }
    public void setType(OperationType type) { this.type = type; }

    public Assembly getAssembly() { return assembly; }
    public void setAssembly(Assembly assembly) { this.assembly = assembly; }

    public ProductModel getProductModel() { return productModel; }
    public void setProductModel(ProductModel productModel) { this.productModel = productModel; }

    public Operation getDependsOnOperation() { return dependsOnOperation; }
    public void setDependsOnOperation(Operation dependsOnOperation) { this.dependsOnOperation = dependsOnOperation; }

    public Set<Material> getRequiredMaterials() { return requiredMaterials; }
    public void setRequiredMaterials(Set<Material> requiredMaterials) { this.requiredMaterials = requiredMaterials; }
}
