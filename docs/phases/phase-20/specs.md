# Phase 20 — Specifications

## ProtocolTrace
traceId: String, formatVersion: Int (=1), createdAtMillis: Long,
sourceType: TraceSourceType, transport: TransportKind,
protocolId: String?, protocolVersion: String?, deviceModel: String?,
firmware: String?, sessionId: String?, events: List<TraceEvent> (≤10000),
redaction: RedactionMetadata, provenance: String,
limitations: List<String>.

## TraceEvent
eventId: String, sequence: Long (monotonic), relativeMillis: Long?,
direction: TraceDirection, category: String, payload: ByteArray? (≤64KiB),
declaredLength: Int? (must match), correlationId: String?, redacted: Boolean.

## TraceSourceType
SYNTHETIC_FIXTURE, DOCUMENTED_EXAMPLE, SANITIZED_CAPTURE, IMPORTED_TRACE,
GENERATED_TEST_CASE.

## TraceDirection
HOST_TO_DEVICE, DEVICE_TO_HOST, OBSERVATION, UNKNOWN.

## LabParser
parserId: String, protocolId: String?, minInputBytes: Int,
maxInputBytes: Int, parse(ByteArray): ParseOutcome.

## ParseOutcome
Parsed(message, bytesConsumed) / Incomplete(bytesNeeded?) /
UnsupportedFormat / Malformed(reason) / UnsupportedVersion(version?) /
LimitExceeded.

## StructuredMessage
messageType: String, semanticMeaning: String? (null = unknown),
direction, headerFields/payloadFields/unknownFields: Map<String,String>,
totalLength: Int, sequenceNumber: Long?, checksumValid: Boolean?,
sourceEventId: String?.

## CorrelationEngine.correlate(trace): CorrelationResult
Pairs on explicit correlation IDs; ambiguous → unmatched (preserved).
Never invents responses.

## SchemaRegistry
register(schema): Registered/Updated/Rejected. Newer version wins;
conflicts rejected.

## LabSchema
protocolId, messageType, version: Int (≥1), compatibleFirmware: Set<String>?,
fieldDefinitions, encodingRules, framingRules, semanticMeaning: String?,
evidenceRef, verificationStatus: VerificationLevel, limitations.

## FixtureGenerator.fromEvent(...)
Deterministic; isSynthetic derived from source type; never mislabeled.

## ParserTestRunner.run(parser, fixtures): TestRunReport
Deterministic; per-fixture expected vs actual.

## EvidenceWorkflow
requirementFor(from, to): String? — explicit evidence per transition;
no level-skipping. canPromote(from, to, hasRealDeviceEvidence): Boolean —
hardware statuses require real evidence.

## LabLimits
MAX_TRACE_FILE_BYTES=4MiB, MAX_EVENTS=10000, MAX_PAYLOAD_BYTES=64KiB,
MAX_TOTAL_DECODED_BYTES=16MiB, MAX_MESSAGE_BYTES=4KiB,
MAX_CONCURRENT_TRACES=4.
