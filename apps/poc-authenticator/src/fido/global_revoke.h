#ifndef GLOBAL_REVOKE_H_
#define GLOBAL_REVOKE_H_

#include "cbor.h"
#include "pico/stdlib.h"
#include "mbedtls/ecdsa.h"

CborError encode_grs_extension(CborEncoder *mapEncoder, mbedtls_ecdsa_context *ekey);

#endif  // GLOBAL_REVOKE_H_