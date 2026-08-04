# Архітектура та UML Діаграми Проєкту (Production MVP)

Цей документ містить ключові діаграми, що описують загальну архітектуру застосунку та структуру бази даних.

## 1. Архітектура Системи (System Architecture)

Система складається з Frontend (на Vanilla JS/HTML/CSS), розміщеного на Vercel, та Backend (на Spring Boot), розміщеного на Render. Як сховище даних використовується зовнішня хмарна база PostgreSQL (Neon).

```mermaid
graph TD
    Client[Користувач / Браузер] -->|HTTP запити (CORS)| Frontend[Frontend (Vercel)]
    Frontend -->|REST API (JSON)| Backend[Backend (Render - Spring Boot)]
    
    subgraph Cloud Infrastructure
        Backend -->|JDBC| DB[(PostgreSQL - Neon)]
    end
    
    classDef client fill:#f9f,stroke:#333,stroke-width:2px;
    classDef cloud fill:#bbf,stroke:#333,stroke-width:2px;
    class Client client;
    class Backend,DB cloud;
```

## 2. Діаграма Сутностей Бази Даних (ER Diagram)

Основні моделі даних (Entity) та зв'язки між ними:

```mermaid
erDiagram
    ProductModel ||--o{ Stage : "має"
    ProductModel ||--o{ ProductInstance : "визначає"
    
    Stage ||--o| Stage : "залежить_від (dependsOn)"
    Stage ||--o{ Task : "генерує"
    
    ProductInstance ||--o{ Task : "має"
    ProductInstance ||--o{ HistoryEvent : "лог_подій"
    
    Worker ||--o{ Task : "виконує (assigned_worker)"
    Worker ||--o{ TimeLog : "логіює_час"
    Worker ||--o{ HistoryEvent : "ініціює_дії"
    
    WorkArea ||--o{ Task : "місце_виконання"
    
    Task ||--o{ TimeLog : "має"
    Task ||--o{ HistoryEvent : "лог_подій"

    ProductModel {
        Long id PK
        String name
        String description
    }
    
    ProductInstance {
        Long id PK
        String serialNumber
        String status "PLANNED, IN_PROGRESS, COMPLETED"
        Long product_model_id FK
    }
    
    Stage {
        Long id PK
        String name
        Integer orderIndex
        Long depends_on_stage_id FK
        Long product_model_id FK
    }
    
    Task {
        Long id PK
        String status "PENDING, IN_PROGRESS, BLOCKED, COMPLETED"
        String missingMaterials
        LocalDateTime createdAt
        LocalDateTime dueDate
        Long assigned_worker_id FK
        Long product_instance_id FK
        Long stage_id FK
        Long work_area_id FK
    }
    
    Worker {
        Long id PK
        String name
        String role "MANAGER, WORKER, QA"
    }
    
    WorkArea {
        Long id PK
        String name
        String location
    }
    
    TimeLog {
        Long id PK
        LocalDateTime startTime
        LocalDateTime endTime
        Long durationSeconds
        Long task_id FK
        Long worker_id FK
    }
    
    HistoryEvent {
        Long id PK
        String action
        LocalDateTime timestamp
        Long product_instance_id FK
        Long stage_id FK
        Long task_id FK
        Long worker_id FK
    }
```
