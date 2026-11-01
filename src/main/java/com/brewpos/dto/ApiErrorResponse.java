package com.brewpos.dto;

import java.time.LocalDateTime;

/** Consistent error body: timestamp, status, error, message, path. */
public record ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path) {
}
