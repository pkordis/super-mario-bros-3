# Testing Conventions (MANDATORY)

Rules every agent must follow when writing or modifying tests in this project.
These complement the general style rules in `.editorconfig` and `AGENTS.md`.

---

## 1. Arrange the body with `// Prepare // Execute // Verify` sections

Every test method body must be divided into three labelled sections, in this
order, using single-line comments exactly as written:

```java
@Test
@DisplayName("...")
void doesTheThing() {
    // Prepare
    final Widget widget = new Widget(...);

    // Execute
    final Result result = widget.doThing();

    // Verify
    assertThat(result.value()).isEqualTo(42);
}
```

- Use the exact labels `// Prepare`, `// Execute`, `// Verify`.
- When execution and verification are interleaved (e.g. asserting inside a loop
  across repeated calls), a combined `// Execute & Verify` section is allowed.
- Shared setup that is reused by multiple tests goes into private fixture helper
  methods (or `@BeforeEach`), not duplicated in each `// Prepare` block.

## 2. `@DisplayName` descriptions start with a capital letter

```java
@DisplayName("Straight-up jump at a block edge slides the player 1px")   // correct
@DisplayName("straight-up jump ...")                                      // WRONG
```

Write descriptions as readable sentences describing the asserted behaviour.

## 3. Only AssertJ assertions — never JUnit or Hamcrest

Use `org.assertj.core.api.Assertions.assertThat(...)` fluent assertions. Never
use `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertThrows`,
etc. from JUnit.

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;   // for floating-point tolerance

assertThat(actual).isEqualTo(expected);
assertThat(flag).isTrue();
assertThat(flag).isFalse();
assertThat(value).isCloseTo(35.0, within(1e-9));         // doubles/floats with tolerance
assertThatThrownBy(() -> foo()).isInstanceOf(IllegalStateException.class);
```

- Add an `.as("...")` description to non-obvious assertions so failures are
  self-explanatory. The `.as(...)` text also starts with a capital letter.
- For floating-point comparisons always use `isCloseTo(expected, within(tol))`
  (or `isEqualTo(expected, within(tol))`) rather than exact equality.

## 4. Use Mockito for collaborators where appropriate

Mock external/heavy collaborators (final classes are fine — Mockito's inline
mock maker is enabled) and use real objects for simple value/state holders.

```java
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final LevelScenePlayer player = mock(LevelScenePlayer.class);
when(player.getPosition()).thenReturn(realPosition); // real POJO for mutable state
when(player.isLarge()).thenReturn(true);
```

- Prefer real instances for plain state/data objects (e.g. `PlayerPosition`,
  `ActivePlayerState`) so mutations by the code under test are observable.
- Reserve mocks for boundaries and expensive/entangled dependencies.

## 5. Never use underscores in method names

Test (and all) method names are `camelCase`. No `snake_case`, no
`methodName_condition_expected` patterns. Encode intent in the method name and
the full sentence in `@DisplayName`.

```java
void cornerSlideHappensWithZeroHorizontalVelocity() { ... }   // correct
void corner_slide_happens_with_zero_velocity() { ... }        // WRONG
```

---

## Canonical reference

`src/test/java/house/x1337/app/smb3/game/collision/CollisionGridWallNudgeTest.java`
follows all of the above and can be used as a template for new tests.
