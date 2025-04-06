# Secure and Privacy-Preserving Global Revocation for FIDO2-Based Systems

This repository contains the proof-of-concept implementation of global revocation for FIDO2 authenticator. It provides
supplementary material for our research paper, "Secure and Privacy-Preserving Global Revocation for FIDO2-based
Systems."

In this repository, we show how our concept can be implemented across all parts of the FIDO2 stack: on the authenticator
(hardware token), on the relying party server, and as an integration with the Global Revocation server.

Watch our proof-of-concept demo:

# Implementations

We have implemented following components.

| Directory                                                         | Description                                  |
|-------------------------------------------------------------------|----------------------------------------------|
| [poc-authenticator](apps/poc-authenticator)                       | Raspberry Pi Pico RP2040 as an Authenticator |
| [poc-global-revocation-server](apps/poc-global-revocation-server) | Global Revocation Server for FIDO2           | 
| [poc-relying-party](apps/poc-relying-party)                       | FIDO2 Relying Party                          |

<details>
  <summary><strong> 📸 Click to view screenshots POC Authenticator 📸 </strong></summary>

### Raspberry Pi Pico RP2040 as FIDO2 Authenticator with Global Revocation Display

  <img src="apps/poc-authenticator/docs/images/pico-with-display.webp" alt="Photo showing Raspberry Pi Pico RP2040 as FIDO2 Authenticator with Global Revocation Display" width="500">
</details>

<details>
  <summary><strong> 📸 Click to view screenshots POC Relying Party 📸 </strong></summary>

### Authenticator Response (v,w,c) Processed by the Relying Party

  <img src="apps/poc-relying-party/doc/images/authenticator-response-with-vwc.webp" alt="Screenshot showing Authenticator Response with (v,w,c) processed by Relying Party" width="800">

### Authenticator Response – (v,w,c) Included in Extension

  <img src="apps/poc-relying-party/doc/images/authenticator-response-with-vwc-extension.webp" alt="Screenshot showing Authenticator Response – (v,w,c) Included in Extension" width="800">

### Relying Party – Authenticator Already Revoked

   <img src="apps/poc-relying-party/doc/images/revoked-page.webp" alt="Screenshot showing Relying Party – Authenticator Already Revoked" width="800">
</details>

<details>
  <summary><strong> 📸 Click to view screenshots POC Global Revocation Server 📸 </strong></summary>

### Global Revocation Key Submission

  <img src="apps/poc-global-revocation-server/docs/images/submit-key.webp" alt="Page showing form to submit Global Revocation Key" width="400">

### Global Revocation Key validation

  <img src="apps/poc-global-revocation-server/docs/images/submit-key-duplicate.webp" alt="Page showing error during duplicate key submission" width="400">

   <img src="apps/poc-global-revocation-server/docs/images/submit-key-not-valid.webp" alt="Page showing error during not valid key" width="400">
</details>

 


