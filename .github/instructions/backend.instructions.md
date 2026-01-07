# Backend Development Instructions - DeliveryVN Microservices

## Project Overview
DeliveryVN là một hệ thống giao hàng thực phẩm được xây dựng theo kiến trúc microservices với Spring Boot. Dự án bao gồm các service:
- **api-gateway**: API Gateway quản lý routing và security
- **auth-service**: Xác thực và phân quyền
- **user-service**: Quản lý người dùng
- **restaurant-service**: Quản lý nhà hàng và menu (reference service)
- **order-service**: Quản lý đơn hàng
- **delivery-service**: Quản lý giao hàng
- **shipper-service**: Quản lý shipper
- **notification-service**: Gửi thông báo
- **search-service**: Tìm kiếm
- **saga-orchestrator-service**: Orchestration pattern cho distributed transactions

## Architectural Standards (Based on Restaurant Service)

### 1. Project Structure
Mỗi service PHẢI tuân theo cấu trúc thư mục chuẩn:
```
src/main/java/com/delivery/{service_name}/
├── config/              # Configuration classes
├── controller/          # REST Controllers
├── dto/                 # Data Transfer Objects
│   ├── request/         # Request DTOs
│   └── response/        # Response DTOs
├── entity/              # JPA Entities
├── exception/           # Custom exceptions và handlers
├── mapper/              # MapStruct mappers
├── payload/             # Common response wrappers
├── repository/          # JPA Repositories
├── service/             # Service interfaces
│   └── impl/            # Service implementations
└── common/              # Common utilities
    └── constants/       # Constants classes
```

### 2. Naming Conventions

#### Package Naming
- Service package: `com.delivery.{service_name}` (snake_case)
- Ví dụ: `com.delivery.restaurant_service`, `com.delivery.order_service`

#### Class Naming
- Controllers: `{Entity}Controller` (ví dụ: `RestaurantController`)
- Services: `{Entity}Service` interface và `{Entity}ServiceImpl` implementation
- Entities: `{Entity}` (ví dụ: `Restaurant`, `MenuItem`)
- DTOs: `{Action}{Entity}Request/Response` (ví dụ: `CreateRestaurantRequest`)
- Repositories: `{Entity}Repository`
- Exceptions: `{Purpose}Exception` (ví dụ: `ResourceNotFoundException`)

### 3. Dependencies và Configuration

#### Required Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Mapping -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>1.5.5.Final</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.32</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Security -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-core</artifactId>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### Java Version
- **Java 17** (tất cả services phải sử dụng Java 17)
- Spring Boot **3.5.3**

### 4. Configuration Standards

#### Application Properties
```properties
# Service name và port
spring.application.name={service-name}
server.port=808X  # Unique port cho mỗi service

# Database configuration (PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/{service_name}_db
spring.datasource.username=postgres
spring.datasource.password=123456
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Security logging (for debugging)
logging.level.org.springframework.security=DEBUG
```

### 5. API Standards

#### Base Response Format
**LUÔN** sử dụng `BaseResponse<T>` wrapper cho tất cả API responses:
```java
public class BaseResponse<T> {
    private final int status;      // 1 = success, 0 = failure
    private final String message;  // Success/error message
    private final T data;          // Response data
    
    public BaseResponse(int status, T data, String message) {
        this.status = status;
        this.message = message;
        this.data = data;
    }
    
    public BaseResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.message = status == 1 ? "Thành công" : "Thất bại";
    }
}
```

#### HTTP Headers
Sử dụng constants cho headers:
```java
public class HttpHeaderConstants {
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_ROLE = "X-Role";
    public static final String AUTHORIZATION = "Authorization";
}
```

#### API Path Constants
```java
public class ApiPathConstants {
    public static final String {ENTITIES} = "/api/{entities}";
    // Ví dụ: public static final String RESTAURANTS = "/api/restaurants";
}
```

#### Controller Pattern
```java
@RestController
@RequestMapping(ApiPathConstants.{ENTITIES})
public class {Entity}Controller {
    
    @Autowired
    private {Entity}Service {entity}Service;
    
    @PostMapping
    public ResponseEntity<BaseResponse<{Entity}Response>> create(
            @RequestBody Create{Entity}Request request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        {Entity}Response response = {entity}Service.create{Entity}(request, creatorId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<{Entity}Response>> getById(@PathVariable Long id) {
        {Entity}Response response = {entity}Service.get{Entity}ById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<{Entity}Response>> update(
            @PathVariable Long id,
            @RequestBody Update{Entity}Request request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {
        {Entity}Response response = {entity}Service.update{Entity}(id, request, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {
        {entity}Service.delete{Entity}(id, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }
}
```

### 6. Entity Standards

#### JPA Entity Pattern

```java
@Getter
@Setter
@Entity
@Table(name = "{table_name}")
public class {Entity} {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;
    
    // Other fields...
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

### 7. Exception Handling

#### Global Exception Handler
**LUÔN** implement `GlobalExceptionHandler` cho mỗi service:
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleAll(Exception ex) {
        ex.printStackTrace(); // log để debug
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse<>(0, null, "Đã xảy ra lỗi nội bộ."));
    }
}
```

### 8. Dependency Injection Standards

#### Constructor Injection (REQUIRED)
**LUÔN** sử dụng Constructor Injection thay vì `@Autowired` field injection:

```java
@Service
public class {Entity}ServiceImpl implements {Entity}Service {
    
    private final {Entity}Repository {entity}Repository;
    private final {Entity}Mapper {entity}Mapper;
    
    public {Entity}ServiceImpl({Entity}Repository {entity}Repository, {Entity}Mapper {entity}Mapper) {
        this.{entity}Repository = {entity}Repository;
        this.{entity}Mapper = {entity}Mapper;
    }
    
    // Implementation methods...
}
```

#### Lợi ích Constructor Injection:
1. **Immutable dependencies** - Fields được khai báo `final`
2. **Better testability** - Dễ dàng mock dependencies trong unit tests
3. **Fail-fast** - Phát hiện lỗi dependency ngay khi khởi tạo  
4. **No reflection needed** - Không cần reflection để inject
5. **Required dependencies** - Đảm bảo tất cả dependencies được provide

#### ❌ KHÔNG sử dụng Field Injection:
```java
// ❌ BAD - Field injection
@Autowired
private {Entity}Repository {entity}Repository;

@Autowired  
private {Entity}Mapper {entity}Mapper;
```

#### ✅ SỬ DỤNG Constructor Injection:
```java
// ✅ GOOD - Constructor injection
private final {Entity}Repository {entity}Repository;
private final {Entity}Mapper {entity}Mapper;

public {Entity}ServiceImpl({Entity}Repository {entity}Repository, {Entity}Mapper {entity}Mapper) {
    this.{entity}Repository = {entity}Repository;
    this.{entity}Mapper = {entity}Mapper;
}
```

### 9. Service Layer Pattern

#### Service Interface
```java
public interface {Entity}Service {
    {Entity}Response create{Entity}(Create{Entity}Request request, Long creatorId, String role);
    {Entity}Response update{Entity}(Long id, Update{Entity}Request request, Long creatorId);
    void delete{Entity}(Long id, Long creatorId);
    {Entity}Response get{Entity}ById(Long id);
    List<{Entity}Response> getAll{Entities}();
}
```

#### Service Implementation
```java
@Service
public class {Entity}ServiceImpl implements {Entity}Service {
    
    private final {Entity}Repository {entity}Repository;
    private final {Entity}Mapper {entity}Mapper;
    
    public {Entity}ServiceImpl({Entity}Repository {entity}Repository, {Entity}Mapper {entity}Mapper) {
        this.{entity}Repository = {entity}Repository;
        this.{entity}Mapper = {entity}Mapper;
    }
    
    // Implementation methods...
}
```

### 10. Database Standards

#### Port Assignment
- api-gateway: 8080
- auth-service: 8081
- user-service: 8082
- restaurant-service: 8083
- order-service: 8084
- delivery-service: 8085
- shipper-service: 8086
- notification-service: 8087
- search-service: 8088
- saga-orchestrator-service: 8089

#### Database Naming
- Mỗi service có database riêng: `{service_name}_db`
- Ví dụ: `restaurant_db`, `order_db`, `user_db`

### 11. Security Standards

#### Role-based Access Control
Sử dụng constants cho roles:
```java
public class RoleConstants {
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String RESTAURANT_OWNER = "SHOP_OWNER";
    public static final String SHIPPER = "SHIPPER";
}
```

### 12. Development Guidelines

#### Code Quality
1. **LUÔN** sử dụng Constructor Injection thay vì `@Autowired` field injection
2. **LUÔN** sử dụng MapStruct cho mapping giữa Entity và DTO
3. **LUÔN** validate input trong Controller layer
4. **LUÔN** handle exceptions với GlobalExceptionHandler
5. **LUÔN** sử dụng Constants classes thay vì hardcode strings
6. **LUÔN** follow naming conventions
7. **LUÔN** sử dụng BaseResponse wrapper
8. **LUÔN** sử dụng Lombok annotations (@Getter, @Setter, @Builder) để giảm boilerplate code

#### Best Practices
1. Service methods PHẢI throw meaningful exceptions
2. Controllers chỉ xử lý HTTP concerns, business logic trong Service
3. Repository chỉ chứa data access logic
4. Sử dụng @Transactional cho operations có multiple database calls
5. Log appropriately cho debugging

#### Testing
1. Mỗi service PHẢI có unit tests
2. Sử dụng H2 database cho testing
3. Test coverage tối thiểu 70%

## Migration Checklist

Khi migrate service khác theo chuẩn restaurant-service:

### ✅ Project Structure
- [ ] Tạo đúng package structure
- [ ] Move classes vào đúng packages

### ✅ Dependencies 
- [ ] Update pom.xml theo template
- [ ] Add missing dependencies
- [ ] Ensure Java 17 và Spring Boot 3.5.3

### ✅ Configuration
- [ ] Update application.properties
- [ ] Set correct port number
- [ ] Configure database connection

### ✅ Code Standards
- [ ] Implement BaseResponse wrapper
- [ ] Create Constants classes
- [ ] Implement GlobalExceptionHandler
- [ ] Update Controller patterns
- [ ] Update Service patterns
- [ ] Update Entity annotations

### ✅ Testing
- [ ] Add H2 dependency for testing
- [ ] Create unit tests
- [ ] Verify integration tests

## Lưu ý quan trọng

1. **KHÔNG** break existing functionality khi refactor
2. **LUÔN** backup trước khi thay đổi lớn
3. **LUÔN** test thoroughly sau mỗi change
4. **TUÂN THỦ** nghiêm ngặt naming conventions
5. **SỬ DỤNG** restaurant-service làm reference implementation

---

**Restaurant-service được sử dụng làm reference implementation cho tất cả services khác. Khi có thắc mắc về implementation, hãy tham khảo restaurant-service.**