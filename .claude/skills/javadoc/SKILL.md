---
name: javadoc
description: Write clear, consistent Javadoc comments on public/protected Java APIs. Triggers when adding or modifying Javadoc, or when generating documentation for Java code.
---

# Javadoc Standards

## When to write Javadoc

Required:
- All public and protected classes, interfaces, enums, and records
- All public and protected methods (including constructors)

Not required:
- Private methods (use regular comments if needed)
- Overridden methods where `{@inheritDoc}` suffices
- Simple getters/setters with self-explanatory names
- Test classes and methods

## Structure

Every Javadoc block follows this order:
1. Summary sentence (one line, ends with a period)
2. Optional longer description (blank line after summary)
3. Block tags in this order: `@param`, `@return`, `@throws`, `@see`, `@since`, `@deprecated`

## Summary sentence rules

- First sentence is a complete sentence ending in a period — it's extracted for summary tables
- Start methods with a third-person verb: "Returns...", "Calculates...", "Validates..."
- Start classes with a noun phrase: "A thread-safe cache for...", "Represents a..."
- Never start with "This method..." or "This class..."
- Keep under 120 characters

## HTML usage

- Use `<p>` to separate paragraphs (no closing tag needed)
- Use `<ul><li>` for lists
- Use `<pre>{@code ... }</pre>` for multi-line code samples
- Avoid `<br>`, complex tables, or styling — keep it semantic

## What NOT to do

- Don't restate the method signature: `// sets the name` on `setName(String name)` adds nothing
- Don't write Javadoc that will go stale immediately (implementation details)
- Don't use first person ("I", "we") or second person ("you")
- Don't add `@author` tags unless the team standard requires it — version control tracks this
- Don't write multi-paragraph essays — link to design docs instead
- Don't document obvious exceptions like `NullPointerException` for every reference parameter
