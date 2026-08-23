package com.example.productionmvp.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFoundException(EntityNotFoundException ex) {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(OperationDependencyException.class)
    public ResponseEntity<Map<String, Object>> handleOperationDependencyException(OperationDependencyException ex) {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PostCapacityExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePostCapacityExceededException(PostCapacityExceededException ex) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InsufficientMaterialException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientMaterialException(InsufficientMaterialException ex) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT);
    }

    // Without these three, the catch-all below turned every mistyped URL, wrong HTTP verb and
    // malformed request body into a 500 "An unexpected error occurred" - which reads as "the
    // server is broken" to the caller and buries genuine 500s in the log among routine client
    // mistakes. A required field missing from the body (e.g. plannedQuantity) hit this path as
    // a NullPointerException and told the manager nothing about what was actually wrong.
    @ExceptionHandler({
        org.springframework.web.servlet.resource.NoResourceFoundException.class,
        org.springframework.web.servlet.NoHandlerFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(Exception ex) {
        return buildErrorResponse(ex, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler({
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        // A blank path segment (".../ /queue") arrives as a *missing* path variable rather than
        // an unconvertible one, and an unparseable date in /defects/since/{date} blows up inside
        // LocalDateTime.parse. Both are malformed requests, not server faults.
        org.springframework.web.bind.MissingPathVariableException.class,
        java.time.format.DateTimeParseException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    // "The given id must not be null" means a request omitted an id the handler passed straight
    // into findById — the caller's omission, and it should read as one.
    //
    // Everything else this exception covers is our own misuse of the JPA API, which is a server
    // fault. Mapping the whole type to 400 once turned a genuine bug (a fetch join illegally
    // reused in a Spring Data count query) into a tidy "the request is missing an identifier",
    // and it took a second investigation to find because the response looked like the caller's
    // fault. Only the null-id case is reported as a client error; the rest fall through to the
    // catch-all below and are logged with their stack trace.
    @ExceptionHandler(org.springframework.dao.InvalidDataAccessApiUsageException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDataAccess(
            org.springframework.dao.InvalidDataAccessApiUsageException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("must not be null")) {
            return buildErrorResponse(
                new IllegalArgumentException("У запиті бракує обов'язкового ідентифікатора."),
                HttpStatus.BAD_REQUEST);
        }
        return handleGeneralException(ex);
    }

    // Oversized text and unique-key clashes surface here. The underlying message names columns
    // and constraints, which is both unhelpful to the caller and more than they should see, so
    // it is logged and replaced rather than passed through.
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {
        logger.warn("Rejected a request that violated a database constraint", ex);
        return buildErrorResponse(
            new IllegalArgumentException("Дані не відповідають обмеженням бази (задовге значення або дублікат)."),
            HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        // Unlike the other handlers above, this branch means something wasn't anticipated —
        // log the real exception with its stack trace so it's actually diagnosable, since the
        // client only ever sees the generic message below.
        logger.error("Unhandled exception while processing request", ex);
        return buildErrorResponse(new Exception("An unexpected error occurred. Please try again later.", ex), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(Exception ex, HttpStatus status) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", status.getReasonPhrase());
        errorDetails.put("message", ex.getMessage());
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("status", status.value());
        
        return new ResponseEntity<>(errorDetails, status);
    }
}
