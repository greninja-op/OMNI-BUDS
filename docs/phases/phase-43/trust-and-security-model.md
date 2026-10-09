# Phase 43 — Trust and Security Model

---

## 1. Security Architecture

Community protocol integrations are strictly bounded and untrusted by default.

### 1.1 The Operation Pipeline
Community adapters participate in the established pipeline:
`IDENTIFY -> CHECK COMPATIBILITY -> DISCOVER CAPABILITY -> PLAN -> AUTHORIZE -> EXECUTE -> RECONCILE -> VERIFY`

The host application retains sole ownership of:
- Transport connection and lifecycle.
- Permission verification and enforcement.
- Centralized `DeviceAccessPolicy` evaluation.
- Scheduling, retry policies, and timeouts.
- Diagnostic redaction and log safety.

---

## 2. Prohibited Behaviors

1. **No Raw Transport Access:** Community adapters have no mechanism to write arbitrary packets outside authorized operations.
2. **No Dynamic Classloading:** No reflection, dynamic loading of `.dex` files, or runtime execution of remote bytecode.
3. **No Network Access:** Core protocol adapters are platform-independent Kotlin and cannot initiate network requests.
4. **No Filesystem / Secret Access:** Adapters receive purely explicit function arguments and cannot access application databases, settings, or Android Keystore secrets.
5. **Unknown Devices Remain Read-Only:** If device matching does not produce an exact evidenced match, writes remain permanently disabled.
