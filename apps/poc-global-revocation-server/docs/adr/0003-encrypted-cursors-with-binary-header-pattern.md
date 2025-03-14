# ADR 0002: Encrypted Cursors with the Binary Header Pattern

* **Status:** Accepted
* **Date:** 2024-12-17
* **Tags:** Spring Boot, Security, Pagination, Cryptography
* **Context References:** ADR 0001 (UUIDv7 Primary Keys)

## Context

Following **ADR 0001**, we are using UUIDv7 for database primary keys. Because UUIDv7 acts as our pagination cursor,
exposing it as plain text introduces a security vulnerability: it leaks the exact millisecond data is processed,
enabling timing attacks and exposing sensitive metadata leakage.

Furthermore, exposing raw database IDs allows attackers to map our database boundaries. We need a way to pass these
cursors securely.

While string concatenation (e.g., `v1:AES-GCM:Base64String`) is a common way to attach versioning to encrypted tokens,
it exposes our internal cryptographic choices to the public, creates unnecessary string parsing overhead in Java, and
increases the URL footprint.

## Decision

We will implement the **"Opaque Token"** pattern for all GET API cursors using **AES-256-GCM**.

To structure these tokens, we will use the **Binary Header Pattern**. Instead of appending readable strings, we will
inject configuration metadata directly into a raw array of bytes *before* Base64URL encoding.

The memory block will be structured exactly as follows:
`[ 1 Byte for Version ] + [ 12 Bytes for Random IV ] + [ Encrypted UUIDv7 ]`

This entire byte array is then Base64URL encoded into a single string which will be included in GET response.

## Consequences

### Positive

* **Semantic Security (IND-CPA):** AES-GCM requires a unique Initialization Vector (IV). By generating 12 random bytes
  per request and embedding it in the binary header, the encrypted cursor will change *every single time* a user
  requests the same page. The token becomes indistinguishable from random noise, preventing attackers from mapping
  pagination boundaries based on identical cursors.
* **Complete Obscurity:** The resulting token hides our backend architecture. Hackers cannot see `v1` or `AES-GCM` in
  the string; only server will know it.
* **Extreme Performance:** Reading a binary header (`byte version = decoded[0];`) accesses the exact first memory
  address in nanoseconds with zero extra memory allocation. This avoids the expensive CPU cycles and garbage collection
  triggered by Java `String.split(":")` operations on high-throughput endpoints.
* **Smaller URL:** The binary version header consumes exactly 1 byte, compared to the 3+ bytes required for string-based
  headers.

### Negative / Risks

* **Debugging Complexity:** Developers cannot manually inspect or decode the opaque cursor via base64URL decoding.
  Debugging requires passing the token through the application's decryption utility.