---
name: general-practices
description: General Java best practices — imports, try-with-resources, StringBuilder, Apache Commons utilities, empty/singleton collections, null object pattern, method references. Triggers on Java edits.
---

# General Practices

- Do NOT use wildcard imports
- Remove any unused imports
- Use try-with-resources where possible
- Use `StringBuilder` for string concatenation (set initial capacity if possible)
- Use collections with initial capacity if minimum size can be estimated
- Use `StringUtils` and `CollectionsUtils` from Apache Commons where applicable
- Use `Collections.emptyList()`, `Collections.emptyMap()`, `Collections.emptySet()` for empty collections
- Use `Collections.singletonList()`, `Collections.singletonMap()`, `Collections.singleton` for single-element collections
- Use trove4j collections for memory efficiency based on requirement
- Prefer null object (object providing 'do nothing' behavior) pattern over returning `null` if used in multiple places
- Prefer method references over lambdas where possible for readability (e.g. `this::methodName` instead of `x -> this.methodName(x)`)