# Phase 35 — Specifications

## InputValidator

- MAX_MESSAGE_BYTES = 65,536; MAX_COLLECTION_SIZE = 1,024;
  MAX_NESTING_DEPTH = 16; MAX_IMPORT_BYTES = 16 MiB.
- ValidationResult: Valid | Invalid(reason).
- validateMessageLength/CollectionSize/NestingDepth/ImportSize.
- safeAdd(a, b): Long? — null on overflow.
- validateFrame(declaredLength, bufferSize, offset): checks offset
  range, length bound, overflow-safe end, buffer overrun.

## LogRedactor

- redact(text): String — redacts MAC addresses and token/secret
  patterns; never throws.
- message(template, vararg args): formats with redacted args.
