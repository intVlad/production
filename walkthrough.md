# Production MVP: Project Walkthrough and Documentation

## 1. Executive Summary
This document serves as the comprehensive guide and formal documentation for the Production MVP (Minimum Viable Product). The system was designed and developed to automate and digitize manufacturing processes on the factory floor. It provides a centralized dashboard for management to oversee production routes, and a mobile-friendly interface for floor workers to claim tasks, log time, and report material shortages in real-time.

## 2. Completed Scope of Work
During the development of this MVP, the following core features and infrastructures were successfully implemented:

- **Entity Modeling & Database Schema:** Designed a robust relational database schema including `ProductModel`, `ProductInstance`, `Stage`, `Task`, `Worker`, `WorkArea`, `TimeLog`, and `HistoryEvent`.
- **Task Lifecycle Management:** Implemented logic for state transitions (PENDING, IN_PROGRESS, BLOCKED, COMPLETED), ensuring tasks cannot be started out of order based on stage dependencies.
- **Real-Time Problem Reporting:** Added the ability for workers to block a task due to missing materials, which immediately alerts the manager via the dashboard.
- **Dynamic Routing via QR Codes:** Implemented client-side QR code generation. When a product is launched into production, a unique QR code is generated. Scanning this code directs the worker to the exact task interface for that specific product instance.
- **Cloud Infrastructure & Deployment:** 
  - Backend API (Spring Boot) containerized via Docker and deployed to Render.
  - Relational Database migrated from local H2 to cloud-hosted PostgreSQL (Neon).
  - Frontend client (Vanilla JS/HTML/CSS) deployed globally via Vercel.
  - Implemented dynamic environment variable resolution to seamlessly handle API routing between local development and production environments.

## 3. Architectural and Logical Decisions

### 3.1. System Architecture
The application employs a decoupled, client-server architecture to ensure scalability and ease of deployment:
- **Frontend Layer:** Built with Vanilla JavaScript, HTML5, and CSS3 to ensure a lightweight footprint and rapid prototyping without the overhead of heavy frameworks. Deployed on Vercel for high-availability static hosting.
- **Backend Layer:** Developed using Java 17 and Spring Boot 3. Provides a RESTful API communicating via JSON. Chosen for its enterprise-level stability, built-in security features, and robust data management via Spring Data JPA.
- **Database Layer:** PostgreSQL hosted on Neon. Chosen for its ACID compliance, relational integrity, and seamless integration with Hibernate ORM.

### 3.2. Logical Decisions
- **Event Sourcing (HistoryEvent):** Instead of merely overwriting the status of a task, every state change is logged in a `HistoryEvent` table. This provides a complete audit trail for stakeholders to review the timeline of a product's manufacturing lifecycle.
- **Stateless Authentication Preparation:** While strict JWT authentication is scoped for future phases, the data model and API endpoints are structured to accept explicit `workerId` parameters, making the future transition to token-based identity injection seamless.
- **Concurrency Handling:** The `TaskExecutionService` relies on database-level constraints and atomic status checks to prevent race conditions (e.g., two workers attempting to claim the same task simultaneously).

## 4. Live Environment Links
- **Frontend Application:** https://production-mvp.vercel.app
- **Backend API (Base URL):** https://production-mvp.onrender.com
- **GitHub Repository:** https://github.com/intVlad/production-mvp

## 5. Demonstration Scenario

The following scenario is designed to demonstrate the end-to-end capabilities of the system to stakeholders.

### Step 1: Manager Configuration
- **Action:** The manager accesses the dashboard and creates a new `Product Model` (e.g., "Office Desk").
- **Action:** The manager defines sequential production stages (e.g., "Cutting", followed by "Assembly").
- **Result:** The system saves the model and its dependencies, establishing the manufacturing route.

### Step 2: Production Launch
- **Action:** The manager selects the created model and clicks "Launch into Production".
- **Result:** The backend generates a new `Product Instance` and queues the initial tasks. The frontend generates and displays a unique QR code representing the route sheet for this specific instance.

### Step 3: Worker Task Execution
- **Action:** A floor worker scans the QR code using a mobile device, which opens the worker interface.
- **Action:** The worker claims the first available task ("Cutting") by clicking "Start".
- **Result:** The task status changes to IN_PROGRESS. A `TimeLog` is initiated, and a `HistoryEvent` is recorded.

### Step 4: Issue Simulation (Blocker)
- **Action:** The worker encounters a shortage of raw materials and clicks "Report Problem / Block", specifying the missing material.
- **Result:** The task status changes to BLOCKED. The manager's dashboard immediately highlights the blocked task in red, indicating intervention is required.

### Step 5: Resolution and Completion
- **Action:** The manager resolves the material shortage. The worker clicks "Resume / Unblock".
- **Action:** The worker clicks "Complete".
- **Result:** The current task is marked COMPLETED. The subsequent dependent task ("Assembly") is automatically unlocked and becomes PENDING for the next available worker. The manager's dashboard reflects the successful progression of the product instance.

## 6. Future Roadmap
Based on current audits, the following items are scheduled for future iterations:
- Implementation of WebSocket or Server-Sent Events (SSE) to replace HTTP polling for real-time dashboard updates.
- Full JWT/OAuth2 authentication implementation for strict role-based access control (RBAC).
- Database indexing optimizations for `HistoryEvent` and `TimeLog` tables to maintain performance as data scales.
