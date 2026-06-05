---
name: unit-test-skill
description: >
  Use this skill whenever the user wants to write, review, generate, or improve unit tests
  for a Spring Boot project. Triggers include: requests to "add tests", "write unit tests",
  "improve test coverage", "test this service/controller/repository", "generate tests for",
  "check edge cases", or any mention of JUnit, Mockito, MockMvc, @SpringBootTest,
  @WebMvcTest, @DataJpaTest, or test coverage tools like JaCoCo. Also trigger when the
  user shares Spring Boot code (controllers, services, repositories, components) and asks
  for code review — always suggest and offer to write tests as part of the review.
  Use proactively: if code is shared without tests, recommend this skill immediately.
---

# Spring Boot Unit Test Skill

A comprehensive guide for scanning a Spring Boot project and producing thorough,
production-grade unit and integration tests covering all layers and edge cases.

---

## Step 1 — Project Scan

Before writing a single test, scan the project to understand its structure.

```bash
# Get the full project tree (exclude build artifacts)
find . -type f -name "*.java" | grep -v "/build/" | grep -v "/target/" | sort

# Check existing test coverage
find . -path "*/test/java/**/*.java" | sort

# Identify the Spring Boot entry point and profiles
grep -r "@SpringBootApplication" --include="*.java" -l
grep -r "@Profile" --include="*.java" -l

# Detect dependencies (testing libs, security, JPA, etc.)
cat pom.xml | grep -A2 "<dependency>" | grep "artifactId"
# or for Gradle:
cat build.gradle | grep -E "testImplementation|implementation"
```

Map every class to its **layer** before proceeding:

| Layer | Annotation(s) | Recommended test slice |
|---|---|---|
| Controller / REST | `@RestController`, `@Controller` | `@WebMvcTest` |
| Service | `@Service` | Plain JUnit + Mockito |
| Repository | `@Repository`, `extends JpaRepository` | `@DataJpaTest` |
| Component / Util | `@Component`, no annotation | Plain JUnit |
| Security config | `@EnableWebSecurity` | `@WebMvcTest` + `@WithMockUser` |
| Scheduled / Events | `@Scheduled`, `@EventListener` | Plain JUnit + Mockito |
| Exception handlers | `@ControllerAdvice` | `@WebMvcTest` or unit |

---

## Step 2 — Test Strategy by Layer

### 2.1 Service Layer (Pure Unit Tests)

Use **Mockito** — no Spring context needed. This keeps tests fast.

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private OrderService orderService;

    // Happy path
    @Test
    void createOrder_validRequest_returnsCreatedOrder() { ... }

    // Edge cases — always cover these:
    @Test
    void createOrder_nullRequest_throwsIllegalArgumentException() { ... }

    @Test
    void createOrder_repositoryThrowsException_propagatesException() { ... }

    @Test
    void createOrder_paymentDeclined_throwsPaymentException() { ... }

    // Boundary values
    @Test
    void createOrder_zeroQuantity_throwsValidationException() { ... }

    @Test
    void createOrder_maxQuantityExceeded_throwsValidationException() { ... }
}
```

**Checklist for every service method:**
- [ ] Happy path with realistic data
- [ ] Null inputs (for each nullable parameter)
- [ ] Empty collections / blank strings
- [ ] Boundary values (0, -1, MAX_INT, empty string vs `" "`)
- [ ] Repository / downstream dependency throws a runtime exception
- [ ] Repository / downstream dependency throws a checked exception
- [ ] Correct arguments are passed to mocked dependencies (`verify(...)`)
- [ ] Return value is correctly mapped / transformed

---

### 2.2 Controller Layer (`@WebMvcTest`)

Load only the web slice — no full context, no real services.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrder_validPayload_returns201() throws Exception {
        OrderResponse response = new OrderResponse(1L, "CREATED");
        given(orderService.create(any())).willReturn(response);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.status").value("CREATED"));
    }

    // Validation errors
    @Test
    void createOrder_missingRequiredField_returns400() throws Exception { ... }

    @Test
    void createOrder_malformedJson_returns400() throws Exception { ... }

    // Service errors mapped to HTTP
    @Test
    void createOrder_serviceThrowsNotFoundException_returns404() throws Exception { ... }

    @Test
    void createOrder_serviceThrowsConflictException_returns409() throws Exception { ... }

    // Security
    @Test
    @WithMockUser(roles = "ADMIN")
    void createOrder_adminRole_allowed() throws Exception { ... }

    @Test
    @WithAnonymousUser
    void createOrder_unauthenticated_returns401() throws Exception { ... }
}
```

**Checklist for every controller endpoint:**
- [ ] Valid request → correct 2xx status + response body shape
- [ ] Missing required fields → 400 + validation message
- [ ] Invalid field types (string where int expected) → 400
- [ ] Service throws domain exception → correct 4xx mapping
- [ ] Service throws unexpected exception → 500
- [ ] Authentication / authorization for each role
- [ ] Path variable not found (GET /resource/{id} with non-existent id)
- [ ] Large payload / boundary sizes if relevant

---

### 2.3 Repository Layer (`@DataJpaTest`)

Runs against an in-memory H2 database (or Testcontainers for closer fidelity).

```java
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByCustomerId_existingCustomer_returnsOrders() {
        Customer customer = em.persist(buildCustomer());
        em.persist(buildOrder(customer));
        em.flush();

        List<Order> result = orderRepository.findByCustomerId(customer.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void findByCustomerId_noOrders_returnsEmptyList() { ... }

    @Test
    void save_duplicateUniqueField_throwsDataIntegrityViolationException() { ... }

    // Custom JPQL / native queries
    @Test
    void findActiveOrdersOlderThan_noMatches_returnsEmptyList() { ... }
}
```

**For Testcontainers (closer to prod):**
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryIT {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

---

### 2.4 Integration Tests (`@SpringBootTest`)

Use sparingly — only for critical flows that cross multiple layers.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class OrderFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullOrderLifecycle_createsAndConfirmsOrder() throws Exception {
        // 1. Create order
        // 2. Verify it persisted
        // 3. Confirm order
        // 4. Verify state change
    }
}
```

---

## Step 3 — Universal Edge Cases Checklist

Apply these to **every class**, regardless of layer:

### Null & Empty Inputs
```java
assertThrows(IllegalArgumentException.class, () -> service.process(null));
assertThrows(IllegalArgumentException.class, () -> service.process(""));
assertThrows(IllegalArgumentException.class, () -> service.process("   "));
```

### Boundary Values
```java
// Numeric boundaries
service.process(0);       // zero
service.process(-1);      // negative
service.process(Integer.MAX_VALUE);  // overflow risk

// String boundaries
service.process("a".repeat(255));  // max length
service.process("a".repeat(256));  // over limit
```

### Concurrent / Thread Safety
```java
// For singletons with shared mutable state
@RepeatedTest(100)
void process_concurrentCalls_noRaceCondition() throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    // submit tasks, await, assert consistent results
}
```

### Exception Handling
```java
// Verify error messages are descriptive
Exception ex = assertThrows(OrderNotFoundException.class,
    () -> service.findById(999L));
assertThat(ex.getMessage()).contains("999");

// Verify stack is not exposed to clients (controller tests)
mockMvc.perform(get("/api/orders/999"))
    .andExpect(status().isNotFound())
    .andExpect(jsonPath("$.stackTrace").doesNotExist());
```

### Date / Time
```java
// Always inject Clock — never use LocalDateTime.now() directly in production code
@Test
void isExpired_pastDate_returnsTrue() {
    Clock fixedClock = Clock.fixed(Instant.parse("2024-06-01T00:00:00Z"), ZoneOffset.UTC);
    service = new OrderService(repository, fixedClock);
    ...
}
```

---

## Step 4 — Test Quality Standards

### Naming Convention
```
methodName_stateUnderTest_expectedBehavior()
```
Examples:
- `findById_existingId_returnsOrder()`
- `findById_nonExistentId_throwsNotFoundException()`
- `save_duplicateEmail_throwsDataIntegrityViolation()`

### AAA Structure (Arrange / Act / Assert)
```java
@Test
void calculateDiscount_premiumMember_applies20PercentDiscount() {
    // Arrange
    Member member = Member.builder().tier(Tier.PREMIUM).build();
    Order order = Order.builder().total(BigDecimal.valueOf(100)).build();

    // Act
    BigDecimal result = discountService.calculate(member, order);

    // Assert
    assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(80));
}
```

### Assertion Best Practices
```java
// Use AssertJ (more readable than JUnit assertions)
assertThat(result).isNotNull();
assertThat(result.getItems()).hasSize(3).extracting("name").contains("Widget");
assertThat(result.getCreatedAt()).isAfter(beforeTest);

// For exceptions, always assert the message too
assertThatThrownBy(() -> service.delete(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("id must not be null");
```

### Mockito Verification
```java
// Verify interactions — not just return values
verify(orderRepository, times(1)).save(orderCaptor.capture());
Order savedOrder = orderCaptor.getValue();
assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);

// Verify no unwanted interactions
verifyNoMoreInteractions(emailService);
```

---

## Step 5 — Coverage & Reporting

### JaCoCo Configuration (`pom.xml`)
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <limits>
                            <!-- Enforce minimum coverage -->
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Run & Inspect
```bash
# Run all tests with coverage
./mvnw clean verify

# Open coverage report
open target/site/jacoco/index.html

# Quick summary in terminal
./mvnw jacoco:report && cat target/site/jacoco/index.html | grep -A5 "Total"
```

### Coverage Targets

| Layer | Line Coverage | Branch Coverage |
|---|---|---|
| Service | ≥ 90% | ≥ 85% |
| Controller | ≥ 85% | ≥ 80% |
| Repository (custom queries) | ≥ 80% | ≥ 75% |
| Utility / Helper classes | ≥ 95% | ≥ 90% |

---

## Step 6 — Common Spring Boot Pitfalls & How to Test Them

### `@Transactional` Rollback
```java
@DataJpaTest  // @Transactional by default — rolls back after each test
@Test
void save_then_findById_withinTransaction() { ... }

// If you need to test actual commit behavior:
@Test
@Commit
void save_verifyPersistedAfterCommit() { ... }
```

### `@Cacheable`
```java
@Test
void findById_calledTwice_hitsRepositoryOnce() {
    given(repository.findById(1L)).willReturn(Optional.of(order));

    service.findById(1L);
    service.findById(1L);  // should hit cache

    verify(repository, times(1)).findById(1L);
}
```

### `@Async`
```java
@SpringBootTest
class EmailServiceTest {
    @Autowired EmailService emailService;

    @Test
    void sendWelcomeEmail_asyncExecution_completesWithinTimeout()
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Void> future = emailService.sendWelcome(user);
        future.get(5, TimeUnit.SECONDS);  // fail fast if stuck
        // assert side effects
    }
}
```

### `@Scheduled`
```java
@ExtendWith(MockitoExtension.class)
class ReportSchedulerTest {
    @Mock ReportService reportService;
    @InjectMocks ReportScheduler scheduler;

    @Test
    void generateDailyReport_invokesServiceOnce() {
        scheduler.generateDailyReport();
        verify(reportService, times(1)).generate(any(LocalDate.class));
    }
}
```

### Validation (`@Valid`, Bean Validation)
```java
// Test validation constraints directly
@Test
void createOrderRequest_blankProductName_failsValidation() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();

    CreateOrderRequest request = new CreateOrderRequest(null, -1, "");
    Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);

    assertThat(violations).isNotEmpty();
    assertThat(violations).extracting(v -> v.getPropertyPath().toString())
        .contains("productName", "quantity");
}
```

---

## Step 7 — Output Checklist Before Finishing

Before presenting tests to the user, verify:

- [ ] Every public method in every service has at least one test
- [ ] Every controller endpoint is tested via `MockMvc`
- [ ] Every custom repository query has a `@DataJpaTest`
- [ ] All `@ExceptionHandler` / `@ControllerAdvice` handlers are covered
- [ ] All edge cases from Step 3 are addressed per class
- [ ] Tests are independent — no shared mutable state between tests
- [ ] No `Thread.sleep()` in tests (use `Awaitility` or `CompletableFuture.get(timeout)`)
- [ ] All test data uses builder patterns or factory methods — no magic strings scattered across tests
- [ ] Tests pass with `./mvnw test` in isolation (no ordering dependency)
- [ ] Coverage thresholds in JaCoCo pass

---

## Quick Reference — Annotations

| Goal | Annotation / Tool |
|---|---|
| Pure unit test | `@ExtendWith(MockitoExtension.class)` |
| Web layer only | `@WebMvcTest(MyController.class)` |
| JPA layer only | `@DataJpaTest` |
| Full context | `@SpringBootTest` |
| Mock a Spring bean | `@MockBean` |
| Spy a Spring bean | `@SpyBean` |
| Authenticated user | `@WithMockUser`, `@WithAnonymousUser` |
| Custom security user | `@WithUserDetails` |
| Parameterized tests | `@ParameterizedTest` + `@MethodSource` / `@CsvSource` |
| Repeated test | `@RepeatedTest(n)` |
| Capture arguments | `ArgumentCaptor<T>` |
| Time control | `Clock` injection + `Clock.fixed(...)` |
| Async assertion | `Awaitility.await().atMost(5, SECONDS).until(...)` |
