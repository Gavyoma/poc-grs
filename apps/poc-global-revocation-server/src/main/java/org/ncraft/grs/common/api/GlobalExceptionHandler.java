/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ncraft.grs.common.api;

import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.config.AppDocsProperties;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final AppDocsProperties appdocsProperties;

    public GlobalExceptionHandler(AppDocsProperties appdocsProperties) {
        this.appdocsProperties = appdocsProperties;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = createBaseProblemDetail(
                HttpStatus.BAD_REQUEST,
                ApiErrorType.VALIDATION_FAILED,
                "One or more fields failed validation.",
                request
        );

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        problem.setProperty("invalid_params", fieldErrors);

        log.warn("Validation failed. Errors: {}", fieldErrors);
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {

        ProblemDetail problem = createBaseProblemDetail(
                HttpStatus.BAD_REQUEST,
                ApiErrorType.BAD_REQUEST,
                ex.getMessage(),
                request
        );

        log.warn("Illegal argument provided. Message: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllUncaughtException(Exception ex, WebRequest request) {

        ProblemDetail problem = createBaseProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorType.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred. Please contact support with the trace_id.",
                request
        );

        log.error("Unhandled exception occurred.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ProblemDetail createBaseProblemDetail(HttpStatus status, ApiErrorType errorType, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(errorType.getDefaultTitle());
        problem.setType(URI.create(appdocsProperties.errorBaseUrl() + errorType.getPath()));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problem.setProperty("timestamp", Instant.now());

        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = "UNKNOWN_TRACE";
        }
        problem.setProperty("trace_id", traceId);

        return problem;
    }
}
