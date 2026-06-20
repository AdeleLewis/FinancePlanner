---
name: java-testing
description: Write effective Java tests with JUnit 5, AssertJ, and Mockito. Triggers when creating or editing test classes (*Test.java, *Tests.java, *IT.java) or when adding tests for Java production code.
---

# Java Testing

## Framework defaults

- **JUnit 5** (`org.junit.jupiter.api.*`) — never JUnit 4 in new code
- **AssertJ** (`org.assertj.core.api.Assertions.assertThat`) for assertions — fluent, better failure messages
- **Mockito** for mocks/stubs; use `@ExtendWith(MockitoExtension.class)` not `MockitoAnnotations.openMocks`
- Static-import `assertThat`, `mock`, `when`, `verify`, `any`, etc. — never qualify them

## Naming

- Test classes: `<ClassUnderTest>Test` for unit, `<Feature>IT` for integration
- Test methods: `methodUnderTest_condition_expectedResult` or `should_expectedResult_when_condition` — pick one style per module and stay consistent
- No `test` prefix on method names — the `@Test` annotation already says that

## Structure

Every test follows **Arrange / Act / Assert** with a blank line between each block:

```java
@Test
void calculateTotal_withDiscount_appliesPercentageOff() {
    final Cart cart = new Cart(List.of(item(10.00), item(20.00)));

    final BigDecimal total = cart.calculateTotal(Discount.of(10));

    assertThat(total).isEqualByComparingTo("27.00");
}
```

- One logical assertion per test (multiple `assertThat` on the same object is fine)
- Use `@DisplayName` only when the method name can't carry the meaning
- Use `@Nested` to group tests for the same method or scenario

## Assertions

- `assertThat(x).isEqualTo(y)` — never `assertEquals(y, x)` (argument order trap)
- For collections: `.containsExactly(...)`, `.containsExactlyInAnyOrder(...)`, `.hasSize(n)` — never assert size and contents in separate calls
- For exceptions: `assertThatThrownBy(() -> ...).isInstanceOf(Foo.class).hasMessageContaining("...")` — never try/catch/fail
- For `BigDecimal`: `.isEqualByComparingTo("1.00")` — `isEqualTo` checks scale too and surprises people
- Avoid `assertTrue`/`assertFalse` on complex expressions — extract to a meaningful AssertJ chain

## Mockito

- Prefer real objects and fakes over mocks; mock only at architectural seams (HTTP clients, repositories, clocks)
- Never mock value objects, DTOs, or the class under test
- `when(...).thenReturn(...)` for stubs; reserve `verify(...)` for interactions that have no observable side effect
- Don't both stub and verify the same call — pick one
- Use `@Mock` and `@InjectMocks` over manual `mock()` when wiring more than one collaborator
- Argument captors over `eq(...)` chains when you want to assert on what was passed

## Parameterized tests

- `@ParameterizedTest` + `@CsvSource` for small inline tables
- `@MethodSource` when arguments need construction
- Name them: `@ParameterizedTest(name = "{0} → {1}")` so failures are readable

## What NOT to test

- Getters, setters, `toString`, `equals`/`hashCode` (unless you wrote them by hand with logic)
- Third-party library behavior — test your integration, not their code
- Logging output (unless logging *is* the contract)
- Private methods directly — test them through the public API; if that's hard, the design is wrong

## Test isolation

- No shared mutable state between tests — no static fields holding data, no `@TestInstance(PER_CLASS)` unless you have a reason
- No ordering dependencies — tests must pass in any order (don't use `@TestMethodOrder` to paper over coupling)
- Clean up resources in `@AfterEach`, not `@AfterAll`, unless setup is genuinely expensive
- Integration tests that touch a database use a real database (Testcontainers), not an in-memory substitute that behaves differently in prod