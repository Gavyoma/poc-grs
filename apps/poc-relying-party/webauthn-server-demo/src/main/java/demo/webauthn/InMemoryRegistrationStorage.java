/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 * All rights reserved.
 *
 * New features and modifications in this project are licensed under the same
 * terms as the original code below.
 *
 */
// Copyright (c) 2018, Yubico AB
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package demo.webauthn;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yubico.webauthn.AssertionResultV2;
import com.yubico.webauthn.CredentialRepositoryV2;
import com.yubico.webauthn.UsernameRepository;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import demo.webauthn.data.CredentialRegistration;
import demo.webauthn.exception.RevocationInputException;
import demo.webauthn.exception.RevocationOperationException;
import demo.webauthn.grs.dto.RevocationWDash;
import demo.webauthn.grs.dto.RevocationWc;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class InMemoryRegistrationStorage
        implements CredentialRepositoryV2<CredentialRegistration>, UsernameRepository {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryRegistrationStorage.class);
    private final Cache<String, Set<CredentialRegistration>> storage =
            CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(1, TimeUnit.DAYS).build();

    ////////////////////////////////////////////////////////////////////////////////
    // The following methods are required by the CredentialRepositoryV2 interface.

    /**
     * Decodes two Base64URL strings, concatenates them, and calculates the SHA-256 hash.
     *
     * @param base64Url1 The first Base64URL encoded string
     * @param base64Url2 The second Base64URL encoded string
     * @return The SHA-256 hash as a raw byte array
     */
    public static String hashTwoBase64UrlStrings(String base64Url1, String base64Url2) throws NoSuchAlgorithmException {

        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] bytes1 = decoder.decode(base64Url1);
        byte[] bytes2 = decoder.decode(base64Url2);

        byte[] combinedBytes = new byte[bytes1.length + bytes2.length];
        System.arraycopy(bytes1, 0, combinedBytes, 0, bytes1.length);
        System.arraycopy(bytes2, 0, combinedBytes, bytes1.length, bytes2.length);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] result = digest.digest(combinedBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }

    /// /////////////////////////////////////////////////////////////////////////////

    @Override
    public Set<CredentialRegistration> getCredentialDescriptorsForUserHandle(ByteArray userHandle) {
        return getRegistrationsByUserHandle(userHandle);
    }

    @Override
    public Optional<CredentialRegistration> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<CredentialRegistration> registrationMaybe =
                storage.asMap().values().stream()
                        .flatMap(Collection::stream)
                        .filter(
                                credReg ->
                                        credentialId.equals(credReg.getCredential().getCredentialId())
                                                && userHandle.equals(credReg.getUserHandle()))
                        .findAny();

        logger.debug(
                "lookup credential ID: {}, user handle: {}; result: {}",
                credentialId,
                userHandle,
                registrationMaybe);

        return registrationMaybe;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // The following methods are required by the UsernameRepository interface.

    @Override
    public boolean credentialIdExists(ByteArray credentialId) {
        return storage.asMap().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(reg -> reg.getCredential().getCredentialId().equals(credentialId));
    }

    /// /////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return getRegistrationsByUsername(username).stream()
                .findAny()
                .map(reg -> reg.getUserIdentity().getId());
    }

    ////////////////////////////////////////////////////////////////////////////////
    // The following methods are specific to this demo application.

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return getRegistrationsByUserHandle(userHandle).stream()
                .findAny()
                .map(CredentialRegistration::getUsername);
    }

    /// /////////////////////////////////////////////////////////////////////////////

    public boolean addRegistrationByUsername(String username, CredentialRegistration reg) {
        try {
            return storage.get(username, HashSet::new).add(reg);
        } catch (ExecutionException e) {
            logger.error("Failed to add registration", e);
            throw new RuntimeException(e);
        }
    }

    public Collection<CredentialRegistration> getRegistrationsByUsername(String username) {
        try {
            return storage.get(username, HashSet::new);
        } catch (ExecutionException e) {
            logger.error("Registration lookup failed", e);
            throw new RuntimeException(e);
        }
    }

    public Set<CredentialRegistration> getRegistrationsByUserHandle(ByteArray userHandle) {
        return storage.asMap().values().stream()
                .flatMap(Collection::stream)
                .filter(
                        credentialRegistration ->
                                userHandle.equals(credentialRegistration.getUserIdentity().getId()))
                .collect(Collectors.toSet());
    }

    public void updateSignatureCount(AssertionResultV2<CredentialRegistration> result) {
        CredentialRegistration registration =
                getRegistrationByUsernameAndCredentialId(
                        result.getCredential().getUsername(), result.getCredential().getCredentialId())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                String.format(
                                                        "Credential \"%s\" is not registered to user \"%s\"",
                                                        result.getCredential().getCredentialId(),
                                                        result.getCredential().getUsername())));

        Set<CredentialRegistration> regs = storage.getIfPresent(result.getCredential().getUsername());
        regs.remove(registration);
        regs.add(
                registration.withCredential(
                        registration.getCredential().toBuilder()
                                .signatureCount(result.getSignatureCount())
                                .build()));
    }

    public Optional<CredentialRegistration> getRegistrationByUsernameAndCredentialId(
            String username, ByteArray id) {
        try {
            return storage.get(username, HashSet::new).stream()
                    .filter(credReg -> id.equals(credReg.getCredential().getCredentialId()))
                    .findFirst();
        } catch (ExecutionException e) {
            logger.error("Registration lookup failed", e);
            throw new RuntimeException(e);
        }
    }

    public boolean removeRegistrationByUsername(
            String username, CredentialRegistration credentialRegistration) {
        try {
            return storage.get(username, HashSet::new).remove(credentialRegistration);
        } catch (ExecutionException e) {
            logger.error("Failed to remove registration", e);
            throw new RuntimeException(e);
        }
    }

    public boolean removeAllRegistrations(String username) {
        storage.invalidate(username);
        return true;
    }

    public boolean userExists(String username) {
        return !getRegistrationsByUsername(username).isEmpty();
    }

    public boolean isGlobalRevocationKeyRevoked(String username) {
        boolean result = false;
        Collection<CredentialRegistration> registrationsByUsername = getRegistrationsByUsername(username);
        if (!registrationsByUsername.isEmpty()) {
            result = registrationsByUsername.stream().findAny()
                    .map(CredentialRegistration::isGlobalRevocationKeyRevoked)
                    .orElseGet(() -> false);
            logger.debug("Global revocation: user: {}, isKeyRevoked: {}", username, result);
        }
        return result;
    }

    public void checkAndRevokeKeys(List<RevocationWDash> wDashes) {
        List<CredentialRegistration> foundKeysToBeRevoked = new ArrayList<>();
        List<CredentialRegistration> existingRegistrations = storage.asMap().values().stream()
                .flatMap(Collection::stream)
                .filter(e -> !e.isGlobalRevocationKeyRevoked())
                .collect(Collectors.toList());
        try {
            for (RevocationWDash wDash : wDashes) {
                if (StringUtils.isNotBlank(wDash.getWDash())) {
                    for (CredentialRegistration existingRegistration : existingRegistrations) {
                        ByteArray credentialPublicKeyCoseKeyBytes = existingRegistration.getPublicKeyCose();
                        byte[] credentialPublicKeyRawEs256Bytes = CoseKeyExtractor.getRawUncompressedEs256Key(credentialPublicKeyCoseKeyBytes);
                        String credentialPublicKeyBase64UrlRawEs256Bytes = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialPublicKeyRawEs256Bytes);
                        String vDash =
                                hashTwoBase64UrlStrings(credentialPublicKeyBase64UrlRawEs256Bytes, wDash.getWDash());
                        if (StringUtils.compareIgnoreCase(existingRegistration.getGlobalRevocationV(), vDash) == 0) {
                            logger.debug("###################################################################");
                            // TODO: DEMO ONLY: Emoji added for conference presentation. Remove emoji before production release.
                            logger.debug("✅ Credential revoked");
                            logger.debug("v: {}", existingRegistration.getGlobalRevocationV());
                            logger.debug("v': {}", vDash);
                            logger.debug("w': {}", wDash.getWDash());
                            logger.debug("###################################################################");
                            foundKeysToBeRevoked.add(existingRegistration);
                        } else {
                            logger.debug("###################################################################");
                            // TODO: DEMO ONLY: Emoji added for conference presentation. Remove emoji before production release.
                            logger.debug("❌ Credential not revoked");
                            logger.debug("v: {}", existingRegistration.getGlobalRevocationV());
                            logger.debug("v': {}", vDash);
                            logger.debug("w': {}", wDash.getWDash());
                            logger.debug("###################################################################");
                        }
                    }
                }
            }

            for (CredentialRegistration foundKey : foundKeysToBeRevoked) {
                Set<CredentialRegistration> regs = storage.getIfPresent(foundKey.getUsername());
                regs.remove(foundKey);
                CredentialRegistration reg =
                        CredentialRegistration.builder()
                                .userIdentity(foundKey.getUserIdentity())
                                .credentialNickname(foundKey.getCredentialNickname())
                                .registrationTime(foundKey.getRegistrationTime())
                                .credential(foundKey.getCredential())
                                .transports((SortedSet<AuthenticatorTransport>) foundKey.getTransports().orElse(null))
                                .attestationMetadata(foundKey.getAttestationMetadata())
                                .isGlobalRevocationKeyRevoked(true)
                                .build();
                regs.add(reg);
                log.debug("User Credential revoked: {}", foundKey.getUsername());
            }
        } catch (IllegalArgumentException e) {
            throw new RevocationInputException("Malformed COSE key or invalid parameter provided", e);
        } catch (IllegalStateException | SecurityException e) {
            throw new RevocationOperationException("Storage or security subsystem failed during revocation", e);
        } catch (Exception e) {
            throw new RevocationOperationException("Key revocation process failed unexpectedly", e);
        }
    }

    public List<RevocationWc> getAllCredentialRegistrationsWC() {
        return storage.asMap().values().stream()
                .flatMap(Collection::stream)
                .filter(e -> !e.isGlobalRevocationKeyRevoked())
                .map(cred -> new RevocationWc(
                        cred.getGlobalRevocationW(),
                        cred.getGlobalRevocationC()
                ))
                .collect(Collectors.toList());
    }


}
