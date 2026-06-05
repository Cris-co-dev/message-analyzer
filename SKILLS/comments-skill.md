---
name: comments-skill
description: "Use this skill when asked to document, comment, or add Javadoc to a Java/Spring REST API project. Triggers: requests to 'add comments', 'document the code', 'write Javadoc', 'improve documentation', or any task involving Java/.java files in a Spring Boot/Spring MVC context. Covers controllers, services, repositories, DTOs, configuration classes, and exception handlers. Do NOT use for non-Java languages or for generating external API docs (e.g., Swagger UI config) unless Javadoc is also involved."
---

# Java/Spring REST API — Code Documentation Guide

## Guiding Principles

- **Comment intent, not mechanics.** Describe *why* code exists, not *what* it does if the code is already self-explanatory.
- **Javadoc for public API surface.** Every `public` class, method, and field exposed outside its package needs a Javadoc block.
- **Inline comments sparingly.** Use `//` comments only for non-obvious logic, workarounds, or business-rule references.
- **Keep comments current.** Outdated comments are worse than no comments — update them alongside the code.

---

## Layer-by-Layer Rules

### Controllers (`@RestController`)

```java
/**
 * Handles HTTP requests for user account management.
 *
 * <p>All endpoints require authentication unless annotated with {@code @PermitAll}.
 * Validation errors return {@code 400 Bad Request}; auth failures return {@code 401}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /**
     * Retrieves a paginated list of active users.
     *
     * @param page zero-based page index (default 0)
     * @param size number of records per page (default 20, max 100)
     * @return {@link Page} of {@link UserDTO} with HTTP 200,
     *         or HTTP 204 if no users exist
     */
    @GetMapping
    public ResponseEntity<Page<UserDTO>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { ... }
}
```

**Rules:**
- Document every endpoint: purpose, path variables, query params, possible HTTP status codes.
- Note security constraints (`@PreAuthorize`, roles, token requirements) in the class-level Javadoc.
- Do **not** repeat what `@Operation` (Swagger) already documents — keep Javadoc and OpenAPI annotations complementary, not redundant.

---

### Services (`@Service`)

```java
/**
 * Business logic for user lifecycle operations.
 *
 * <p>Coordinates between {@link UserRepository} and external identity providers.
 * All write operations are transactional; read operations use read-only transactions
 * for performance.
 */
@Service
public class UserService {

    /**
     * Creates a new user and sends a verification email.
     *
     * <p>Throws {@link DuplicateEmailException} if the email is already registered.
     * The verification token expires in 24 hours.
     *
     * @param request validated creation payload
     * @return the persisted {@link User} entity with generated ID
     * @throws DuplicateEmailException if {@code request.email} is already in use
     */
    public User createUser(CreateUserRequest request) { ... }
}
```

**Rules:**
- Document transactional boundaries (`@Transactional`) and their implications.
- List all checked and meaningful unchecked exceptions in `@throws`.
- Note external service calls (email, payment, etc.) so callers understand side effects.

---

### Repositories (`@Repository`)

```java
/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Custom queries use JPQL unless a native query is explicitly required for
 * performance — mark native queries with an inline comment explaining why.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds users whose accounts have not been verified and whose tokens
     * have expired before the given threshold.
     *
     * @param expiry cutoff timestamp; tokens created before this are considered expired
     * @return list of unverified users eligible for cleanup
     */
    @Query("SELECT u FROM User u WHERE u.verified = false AND u.tokenExpiry < :expiry")
    List<User> findUnverifiedBefore(@Param("expiry") LocalDateTime expiry);
}
```

**Rules:**
- Document custom `@Query` methods — include the filtering criteria in plain English.
- Skip Javadoc on inherited CRUD methods (findById, save, etc.) unless behavior is overridden.
- Note index dependencies if a query's performance relies on a specific DB index.

---

### DTOs / Request & Response Objects

```java
/**
 * Payload for creating a new user account.
 *
 * <p>All fields are validated on arrival at the controller layer.
 * Passwords are never stored or logged; they are hashed in {@link UserService}.
 */
public record CreateUserRequest(

    /** Primary email address; must be unique across all accounts. */
    @Email @NotBlank String email,

    /** Raw password; minimum 8 characters, hashed before persistence. */
    @Size(min = 8) String password,

    /** Display name shown in the UI; does not need to be unique. */
    @NotBlank String displayName
) {}
```

**Rules:**
- Use field-level Javadoc (`/** ... */`) for every field that carries a business constraint.
- Note fields that are never persisted, logged, or serialized (e.g., raw passwords).
- For response DTOs, mention which fields may be `null` and under what conditions.

---

### Exception Handlers (`@ControllerAdvice`)

```java
/**
 * Centralised exception-to-HTTP-response mapping for the REST API.
 *
 * <p>Produces RFC 7807 Problem Detail responses ({@code application/problem+json}).
 * Unexpected exceptions are logged at ERROR level and return HTTP 500 without
 * exposing internal stack traces to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation failures from {@code @Valid} on request bodies.
     *
     * @param ex the binding exception containing field-level error details
     * @return HTTP 400 with a map of field names to error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) { ... }
}
```

---

### Configuration Classes (`@Configuration`)

```java
/**
 * Security filter chain configuration.
 *
 * <p>Enforces JWT authentication on all {@code /api/**} routes.
 * Public routes (health check, auth endpoints) are listed explicitly in
 * {@code publicMatchers()} — add new public endpoints there, not here.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig { ... }
```

**Rules:**
- Explain the overall purpose of the configuration and what it affects at runtime.
- Document non-obvious bean names or ordering constraints (`@Order`, `@Primary`).
- Point to companion classes when configuration is split across multiple files.

---

## Inline Comment Patterns

| Situation | Example |
|-----------|---------|
| Business rule reference | `// Rule BR-42: free tier capped at 5 projects` |
| Workaround / tech debt | `// TODO(#1234): remove after Spring Boot 3.3 upgrade` |
| Non-obvious algorithm step | `// Shift right by 1 to divide by 2 without float conversion` |
| Security-sensitive path | `// SECURITY: never log 'token' — contains raw credential` |

**Avoid:**
```java
// Get the user by ID          ← states the obvious
User user = userRepo.findById(id);

// increment counter           ← mechanical noise
count++;
```

---

## Javadoc Quick Reference

| Tag | When to use |
|-----|-------------|
| `@param` | Every method parameter, one line each |
| `@return` | Any non-void method; describe the type *and* what it represents |
| `@throws` | Checked exceptions always; unchecked when callers need to handle them |
| `@see` | Link to related classes or methods for context |
| `{@link ClassName}` | Inline reference to another type within prose |
| `@since` | Public APIs — note the version the method was introduced |
| `@deprecated` | Mark with reason and migration path; pair with `@Deprecated` annotation |

---

## What NOT to Document

- Auto-generated getters/setters on plain POJOs (use Lombok; skip Javadoc).
- Private helper methods with obvious names (`buildUrl`, `toUpperCase`).
- `@Override` methods where the parent Javadoc is sufficient — add only if behavior diverges.
- Test methods — use descriptive method names (`should_returnBadRequest_when_emailIsMissing`) instead.
