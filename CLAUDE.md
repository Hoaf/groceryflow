# GroceryFlow — Claude Instructions

## Tổng quan
Ứng dụng quản lý bán hàng cho tiệm tạp hóa gia đình.
- **Chủ tiệm**: Web dashboard — nhập hàng, xem thống kê
- **Nhân viên**: Mobile app — scan barcode, bán hàng

## Mục tiêu học tập
- Microservices với Spring Boot 4
- Kafka + Outbox Pattern + Saga Pattern
- Distributed Lock với Redis
- React web + React Native mobile
- Deploy với Docker Compose → K3s (sau này)

---

## Tech Stack

| Layer | Tech |
|-------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5, Spring Cloud 2025.x |
| Database | MySQL 8 |
| Cache + Lock | Redis 7 |
| Message Queue | Kafka 3 |
| Gateway | Spring Cloud Gateway |
| Web | React 18 + Vite + TypeScript + TailwindCSS |
| Mobile | React Native + Expo + TypeScript |
| Container | Docker + Docker Compose |

---

## Services & Ports

| Service | Port | Database |
|---------|------|----------|
| api-gateway | 8080 | — |
| user-service | 8081 | groceryflow_users |
| product-service | 8082 | groceryflow_products |
| order-service | 8083 | groceryflow_orders |
| import-service | 8084 | groceryflow_imports |
| report-service | 8085 | groceryflow_reports |
| notification-service | 8086 | — |
| audit-service | 8087 | groceryflow_audit |

---

## Kiến trúc

```
Mobile App (React Native)     Web App (React)
         │                         │
         └──────────┬──────────────┘
                    ▼
         API Gateway (8080)
         - Routing
         - JWT Verify
         - Rate Limiting
                    │
     ┌──────────────┼──────────────┐
     ▼              ▼              ▼
user-service  product-service  order-service
(auth + user) (sản phẩm+kho)  (đơn hàng)
     │              │              │
     └──────────────┴──────────────┘
                    │
              Kafka Events
                    │
     ┌──────────────┼──────────────┐
     ▼              ▼              ▼
import-service report-service notification-service
(nhập hàng)   (thống kê)    (cảnh báo)
                                   │
                              audit-service
                              (lịch sử)
```

---

## Kafka Topics

| Topic | Producer | Consumer |
|-------|----------|----------|
| order.created | order-service | report-service, notification-service, audit-service |
| order.confirmed | order-service | audit-service |
| order.cancelled | order-service | product-service (hoàn kho), audit-service |
| stock.deducted | product-service | audit-service |
| stock.deduct.failed | product-service | order-service (saga compensate) |
| stock.low | product-service | notification-service |
| import.completed | import-service | notification-service, audit-service |

---

## Consistency Strategy

| Chức năng | Loại | Cách implement |
|-----------|------|----------------|
| Đăng nhập / JWT | Strong | ACID (user-service) |
| Tạo order | Strong | ACID (order-service) |
| Trừ tồn kho | Strong | Distributed Lock Redis + ACID |
| Order ↔ Product (cross-service) | Eventual | Saga Choreography + Kafka |
| Nhập hàng + cộng kho | Strong | ACID (import-service) |
| Cập nhật doanh thu | Eventual | Kafka + Outbox Pattern |
| Cảnh báo tồn kho thấp | Eventual | Kafka |
| Lịch sử thao tác | Eventual | Kafka |

---

## Coding Conventions

### Package structure (mỗi service)
```
com.groceryflow.<service-name>
├── controller
├── service
├── repository
├── model (entity)
├── dto
│   ├── request
│   └── response
├── event (Kafka events)
├── config
└── exception
```

### Quy tắc bắt buộc
- Dùng Lombok: @Data, @Builder, @RequiredArgsConstructor, @Slf4j
- Không expose Entity trực tiếp — luôn dùng DTO
- Mọi response wrap trong ApiResponse<T>
- Mọi Entity có created_at, updated_at
- UUID cho primary key các entity quan trọng
- Flyway cho database migration

### ApiResponse format
```java
ApiResponse<T> {
    boolean success;
    String message;
    T data;
    LocalDateTime timestamp;
}
```

### Database naming
- Table: snake_case (order_items, product_categories)
- Column: snake_case (created_at, product_name)
- Index: idx_<table>_<column> (idx_orders_user_id)

---

## Outbox Pattern (bắt buộc cho mọi Kafka publish)
```
Thay vì publish Kafka trực tiếp,
ghi vào bảng outbox_events cùng transaction với business data.
Background job đọc outbox và publish lên Kafka.
```

## Idempotency (bắt buộc cho mọi Kafka consumer)
```
Mỗi consumer check processed_events trước khi xử lý.
Lưu eventId vào processed_events sau khi xử lý xong.
```

---

## Lộ trình Implementation

### PHASE 1 — Foundation (Tuần 1)
```
Step 1.1: Maven multi-module project structure
Step 1.2: Docker Compose (MySQL, Redis, Kafka, Zookeeper)
Step 1.3: API Gateway — routing + JWT filter
Step 1.4: User Service — entity, repository, migration
Step 1.5: User Service — register + login + JWT
Step 1.6: User Service — refresh token + logout (blacklist Redis)
```

### PHASE 2 — Core Services (Tuần 2-3)
```
Step 2.1: Product Service — entity, repository, migration
Step 2.2: Product Service — CRUD API + barcode lookup
Step 2.3: Product Service — Redis cache
Step 2.4: Product Service — Distributed Lock (Redisson)
Step 2.5: Order Service — entity, repository, migration
Step 2.6: Order Service — Outbox table + background publisher
Step 2.7: Order Service — tạo order API
Step 2.8: Order Service — Saga: gọi Product Service trừ kho
Step 2.9: Order Service — Saga: compensating transaction
```

### PHASE 3 — Import & Kafka (Tuần 4)
```
Step 3.1: Import Service — entity, repository, migration
Step 3.2: Import Service — tạo phiếu nhập + cộng kho (ACID)
Step 3.3: Import Service — Outbox + publish import.completed
Step 3.4: Notification Service — Kafka consumer setup
Step 3.5: Notification Service — nhận stock.low, import.completed
Step 3.6: Audit Service — nhận mọi events, lưu lịch sử
```

### PHASE 4 — Report (Tuần 5)
```
Step 4.1: Report Service — entity, migration
Step 4.2: Report Service — consume order.created
Step 4.3: Report Service — API doanh thu theo ngày/tuần/tháng
Step 4.4: Report Service — API sản phẩm bán chạy
Step 4.5: Report Service — API lợi nhuận (giá bán - giá nhập)
```

### PHASE 5 — React Web (Tuần 6-7)
```
Step 5.1: Setup React + Vite + TypeScript + TailwindCSS
Step 5.2: Axios instance + React Query setup
Step 5.3: Login page + JWT storage
Step 5.4: Dashboard — doanh thu hôm nay, cảnh báo kho
Step 5.5: Trang sản phẩm — danh sách + CRUD
Step 5.6: Trang nhập hàng — tạo phiếu + lịch sử
Step 5.7: Trang báo cáo — biểu đồ doanh thu, lợi nhuận
Step 5.8: Trang nhân viên — quản lý tài khoản
```

### PHASE 6 — React Native (Tuần 8-9)
```
Step 6.1: Setup Expo + TypeScript + React Navigation
Step 6.2: Login screen + JWT storage (SecureStore)
Step 6.3: Màn hình bán hàng — giỏ hàng
Step 6.4: Scan barcode (expo-barcode-scanner)
Step 6.5: Thanh toán — tiền mặt / chuyển khoản
Step 6.6: Lịch sử ca làm việc
Step 6.7: Push notification (expo-notifications)
```

### PHASE 7 — Polish & Deploy (Tuần 10-11)
```
Step 7.1: Unit tests — service layer mỗi service
Step 7.2: Integration tests — API endpoints
Step 7.3: Docker Compose final — tất cả services
Step 7.4: Cloudflare Tunnel setup
Step 7.5: README.md — hướng dẫn cài đặt cho gia đình
```

### PHASE 8 — K3s (Tuần 12-13, optional)
```
Step 8.1: Cài K3s trên laptop Linux
Step 8.2: Convert docker-compose → K8s Deployment yaml
Step 8.3: K8s Service + ConfigMap + Secret
Step 8.4: Ingress thay thế expose port
Step 8.5: Health check + Rolling update
```

---

## Cách dùng file này với Claude Code

Mỗi lần làm việc, chỉ implement đúng 1 Step.
Ví dụ prompt:
```
Implement Step 1.4: User Service — entity, repository, migration
Theo đúng conventions trong CLAUDE.md
```

Sau mỗi Step:
```bash
mvn compile        # check compile
mvn test           # check tests
docker-compose up  # check service start
```

---

## Quy tắc giải thích (QUAN TRỌNG)

Project này dùng để **học MSA, data consistency, Kafka, K8s, Docker Compose, Redis**.
Khi implement mỗi step, Claude **phải**:

1. **Giải thích tại sao** lại dùng approach này — không chỉ viết code
2. **Nêu các cách khác** có thể implement (alternatives)
3. **Pros/Cons** của từng cách — để người học hiểu trade-off
4. **Giải thích cho người mới** — không giả định người đọc đã biết khái niệm

Ví dụ format giải thích:
```
## Tại sao dùng Outbox Pattern?
Vấn đề: nếu ghi DB thành công nhưng publish Kafka thất bại → data inconsistent
Cách 1: Ghi DB + publish Kafka trong cùng try/catch → không đảm bảo atomic
Cách 2: Outbox Pattern → ghi DB + outbox cùng 1 transaction → background job publish
→ Chọn Cách 2 vì: ...pros/cons...
```
