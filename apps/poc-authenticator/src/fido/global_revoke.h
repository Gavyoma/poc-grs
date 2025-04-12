/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the LICENSE file in the root directory.
 */

#ifndef GLOBAL_REVOKE_H_
#define GLOBAL_REVOKE_H_

#include "cbor.h"
#include "pico/stdlib.h"
#include "mbedtls/ecdsa.h"

CborError encode_grs_extension(CborEncoder *mapEncoder, mbedtls_ecdsa_context *ekey);

#endif  // GLOBAL_REVOKE_H_