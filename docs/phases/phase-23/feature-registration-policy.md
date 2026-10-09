# Phase 23 — Feature Registration Policy

## Registration

1. Structural validation (IDs, required fields).
2. Duplicate detection (extension and feature IDs).
3. Owning extension must be registered.
4. Namespace must be declared by the extension.
5. Dependency cycles rejected (extension and feature level).
6. DISABLED extensions cannot register.

## Validation

- Identifier formats enforced at construction.
- Value constraints validated (min ≤ max).
- Default values must satisfy constraints.

## Review

Descriptors carry verification status and trust level. Promotion from
DESCRIPTIVE to VERIFIED requires evidence; the registry records the
trust level at registration.

## Activation

Only ACTIVE extensions resolve. Activation requires passing structural
validation.

## Deprecation

`deprecate()`: lifecycle → DEPRECATED; historical records preserved;
excluded from resolution.

## Disablement

`disable()`: lifecycle → DISABLED; excluded from resolution; features
remain registered but unresolvable.
