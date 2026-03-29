# Developer / Agent Notes — Super Mario Bros 3

This file captures architectural decisions and conventions that are not obvious from
the code alone. Read it before making structural changes to the project.

---

## Code style — `.editorconfig` (MANDATORY)

A `.editorconfig` file at the repository root defines the **canonical style** for
this project. Every agent **must** comply with it at all times:

* **Indentation** — 4 spaces (no tabs), continuation indent 8 spaces.
* **Imports** — grouped as: non-java/javax first → blank line → `javax.*` →
  `java.*` → blank line → static imports; alphabetical within each group.
* **`final` everywhere** — all method parameters and all local variables that are
  not reassigned must be declared `final`.
* **No alignment spacing** — never use more than one space between tokens to
  vertically align field declarations, variable declarations, or assignment
  operators. Use exactly one space on each side of `=`.
* **Line length** — hard limit of 120 characters.
* **Trailing whitespace** — none (editor enforces on save).
* **File endings** — LF line endings, one trailing newline, no extra blank lines
  after the closing `}` of the top-scene type.
* **Braces** — K&R style (`{` on the same line, never on a new line).

Before committing any Java file, verify that it conforms to every rule above.
When in doubt, consult `.editorconfig` for the authoritative settings.

---
