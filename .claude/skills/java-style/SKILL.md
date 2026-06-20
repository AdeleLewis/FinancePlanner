---
name: java-style
description: Apply consistent Java code style (indentation, naming, member ordering, final usage, exception messages). Triggers when creating or editing .java files.
---

# Java Style Guide

- Use 4 spaces for indentation, never tabs
- Max line length: 120 characters
- Class names: PascalCase. Methods/variables: camelCase. Constants: UPPER_SNAKE_CASE
- Keep local variable names concise
- Always use `final` for parameters and local variables that aren't reassigned
- Wrap throws with descriptive messages — never `throw new RuntimeException()` bare
- Order class members: static fields → instance fields → constructors → public methods → private methods
- One blank line between methods and variable definitions
- Do not use `this.` for variables (prefixes prevent shadowing)