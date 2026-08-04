package com.example.productionmvp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM worker", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        // Insert Worker with hardcoded ID
        java.util.UUID workerId = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        jdbcTemplate.update("INSERT INTO worker (id, name, role) VALUES (?, 'John Doe', 'Technician')", workerId);

        java.util.UUID workerId2 = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222");
        jdbcTemplate.update("INSERT INTO worker (id, name, role) VALUES (?, 'Jane Smith', 'Engineer')", workerId2);
        
        // Insert Product Model
        java.util.UUID modelId = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO product_model (id, name, description) VALUES (?, 'Widget 3000', 'High-tech widget')", modelId);
        
        // Insert Stage 1
        java.util.UUID stage1Id = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO stage (id, product_model_id, name, order_index) VALUES (?, ?, 'Assembly', 1)", stage1Id, modelId);

        // Insert Stage 2 to test state progression
        java.util.UUID stage2Id = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO stage (id, product_model_id, name, order_index) VALUES (?, ?, 'Testing', 2)", stage2Id, modelId);
        
        // Insert Product Instance
        java.util.UUID instanceId = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO product_instance (id, product_model_id, serial_number, status) VALUES (?, ?, 'SN-001', 'PENDING')", instanceId, modelId);
        
        // Insert Work Area
        java.util.UUID areaId = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO work_area (id, name, location) VALUES (?, 'Station 1', 'Zone A')", areaId);
        
        // Insert Task with hardcoded ID
        java.util.UUID taskId = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        jdbcTemplate.update("INSERT INTO task (id, product_instance_id, stage_id, work_area_id, status, missing_materials, created_at, due_date) VALUES (?, ?, ?, ?, 'PENDING', false, CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))", taskId, instanceId, stage1Id, areaId);
        
        // --- ADD SECOND PRODUCT AND TASK ---
        java.util.UUID instanceId2 = java.util.UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO product_instance (id, product_model_id, serial_number, status) VALUES (?, ?, 'SN-002', 'PENDING')", instanceId2, modelId);
        
        java.util.UUID taskId2 = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
        jdbcTemplate.update("INSERT INTO task (id, product_instance_id, stage_id, work_area_id, status, missing_materials, created_at, due_date) VALUES (?, ?, ?, ?, 'PENDING', false, CURRENT_TIMESTAMP, DATEADD('DAY', -1, CURRENT_TIMESTAMP))", taskId2, instanceId2, stage1Id, areaId);
        // -----------------------------------

        // Insert Materials
        java.util.UUID mat1Id = java.util.UUID.fromString("33333333-3333-3333-3333-333333333331");
        jdbcTemplate.update("INSERT INTO material (id, name, sku) VALUES (?, 'Plastic Frame', 'PL-001')", mat1Id);
        java.util.UUID mat2Id = java.util.UUID.fromString("33333333-3333-3333-3333-333333333332");
        jdbcTemplate.update("INSERT INTO material (id, name, sku) VALUES (?, 'Metal Screws (Pack)', 'MS-002')", mat2Id);
        java.util.UUID mat3Id = java.util.UUID.fromString("33333333-3333-3333-3333-333333333333");
        jdbcTemplate.update("INSERT INTO material (id, name, sku) VALUES (?, 'Display Panel', 'DP-003')", mat3Id);

        
        System.out.println("Mock data seeded successfully.");
        System.out.println("Use Task ID: 550e8400-e29b-41d4-a716-446655440000 in the UI.");
    }
}
