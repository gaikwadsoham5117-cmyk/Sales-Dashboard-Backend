package com.example.SalesDashboard.user.exception.handlers;

import com.example.SalesDashboard.agent.exception.*;
import com.example.SalesDashboard.framework.dto.ErrorResponse;
import com.example.SalesDashboard.framework.exception.EmailNotFoundException;
import com.example.SalesDashboard.organization.exception.*;
import com.example.SalesDashboard.subscription.exception.*;
import com.example.SalesDashboard.tally.Company.exception.*;
import com.example.SalesDashboard.tally.Sales.exception.*;
import com.example.SalesDashboard.user.exception.InactiveAccountException;
import com.example.SalesDashboard.user.exception.InvalidCredentialsException;
import com.example.SalesDashboard.user.exception.InvalidFormatPasswordException;
import com.example.SalesDashboard.user.exception.UserAlreadyExistException;
import com.example.SalesDashboard.user.exception.UserNotAuthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =========================================================
    // VALIDATION  (@Valid on request bodies)
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation failed", errors));
    }

    // =========================================================
    // BAD REQUEST PARAMETERS
    //
    // A query param / path variable value that cannot be
    // converted to the type the controller expects.
    //
    // e.g. ?to=2026-04-3 cannot become a LocalDate (needs
    // yyyy-MM-dd, so 2026-04-03), or ?page=abc cannot become
    // an int. This happens BEFORE the controller method runs,
    // so it never reaches any of our own validation code.
    // =========================================================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "the expected type";
        String message = "Invalid value for '" + ex.getName() + "': '"
                + ex.getValue() + "'. Expected " + requiredType
                + (requiredType.equals("LocalDate") ? " in yyyy-MM-dd format" : "") + ".";
        log.warn("Bad request parameter: {}", message);
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =========================================================
    // MISSING REQUIRED PARAMETER
    //
    // A required @RequestParam that was not sent at all.
    // =========================================================

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing.";
        log.warn("Missing request parameter: {}", message);
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =========================================================
    // USER
    // =========================================================

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistException(UserAlreadyExistException ex) {
        log.warn("User registration failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidFormatPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormatPasswordException(InvalidFormatPasswordException ex) {
        log.warn("Invalid format password: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        log.warn("Login failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAccountException(InactiveAccountException ex) {
        log.warn("Login blocked: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(UserNotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUserNotAuthenticatedException(UserNotAuthenticatedException ex) {
        log.warn("Unauthenticated request blocked: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        log.warn("Request failed with status {}: {}", status, ex.getReason());
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                ex.getReason() != null ? ex.getReason() : "Request could not be completed"
        );
        return ResponseEntity.status(status).body(errorResponse);
    }

    // =========================================================
    // AGENT
    // =========================================================

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAgentNotFoundException(AgentNotFoundException ex) {
        log.warn("Agent not found: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(AgentUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAgentUserNotFoundException(AgentUserNotFoundException ex) {
        log.warn("Agent request user not found: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(AgentOrganizationNotAssignedException.class)
    public ResponseEntity<ErrorResponse> handleAgentOrganizationNotAssignedException(AgentOrganizationNotAssignedException ex) {
        log.warn("Agent request without organization: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(AgentIdRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAgentIdRequiredException(AgentIdRequiredException ex) {
        log.warn("Agent id missing: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(AgentOfflineException.class)
    public ResponseEntity<ErrorResponse> handleAgentOfflineException(AgentOfflineException ex) {
        log.warn("Agent offline: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAgentTimeoutException(AgentTimeoutException ex) {
        log.warn("Agent timeout: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.GATEWAY_TIMEOUT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(errorResponse);
    }

    @ExceptionHandler(AgentCommunicationException.class)
    public ResponseEntity<ErrorResponse> handleAgentCommunicationException(AgentCommunicationException ex) {
        log.warn("Agent communication failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(AgentResponseException.class)
    public ResponseEntity<ErrorResponse> handleAgentResponseException(AgentResponseException ex) {
        log.warn("Agent returned error: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    // =========================================================
    // ORGANIZATION
    // =========================================================

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNotFoundException(OrganizationNotFoundException ex) {
        log.warn("Organization not found: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(OrganizationAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationAlreadyExistException(OrganizationAlreadyExistException ex) {
        log.warn("Organization creation failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(OrganizationNameRequiredException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNameRequiredException(OrganizationNameRequiredException ex) {
        log.warn("Organization name missing: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidSubscriptionStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubscriptionStatusException(InvalidSubscriptionStatusException ex) {
        log.warn("Invalid subscription status: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =========================================================
    // SUBSCRIPTION
    // =========================================================

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFoundException(SubscriptionNotFoundException ex) {
        log.warn("Subscription not found: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(SubscriptionPlanNameRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionPlanNameRequiredException(SubscriptionPlanNameRequiredException ex) {
        log.warn("Subscription plan name missing: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidMaxUsersException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMaxUsersException(InvalidMaxUsersException ex) {
        log.warn("Invalid max users: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =========================================================
    // TALLY COMPANY
    // =========================================================

    @ExceptionHandler(CompanyRequestPreparationException.class)
    public ResponseEntity<ErrorResponse> handleCompanyRequestPreparationException(CompanyRequestPreparationException ex) {
        log.error("Tally company request preparation failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(CompanyEmptyResponseException.class)
    public ResponseEntity<ErrorResponse> handleCompanyEmptyResponseException(CompanyEmptyResponseException ex) {
        log.warn("Empty Tally company response: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(CompanyAgentStatusException.class)
    public ResponseEntity<ErrorResponse> handleCompanyAgentStatusException(CompanyAgentStatusException ex) {
        log.warn("Tally Agent returned failure status: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(CompanyResponseParseException.class)
    public ResponseEntity<ErrorResponse> handleCompanyResponseParseException(CompanyResponseParseException ex) {
        log.warn("Tally company response could not be parsed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    // =========================================================
    // TALLY SALES
    // =========================================================

    @ExceptionHandler(SalesCompanyNameRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSalesCompanyNameRequiredException(SalesCompanyNameRequiredException ex) {
        log.warn("Sales company name missing: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(SalesDateRangeRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSalesDateRangeRequiredException(SalesDateRangeRequiredException ex) {
        log.warn("Sales date range missing: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidSalesDateRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSalesDateRangeException(InvalidSalesDateRangeException ex) {
        log.warn("Invalid sales date range: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(SalesDateRangeTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleSalesDateRangeTooLargeException(SalesDateRangeTooLargeException ex) {
        log.warn("Sales date range too large: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(SalesRequestPreparationException.class)
    public ResponseEntity<ErrorResponse> handleSalesRequestPreparationException(SalesRequestPreparationException ex) {
        log.warn("Tally sales request preparation failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(SalesEmptyResponseException.class)
    public ResponseEntity<ErrorResponse> handleSalesEmptyResponseException(SalesEmptyResponseException ex) {
        log.warn("Empty Tally sales response: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(SalesInvalidResponseException.class)
    public ResponseEntity<ErrorResponse> handleSalesInvalidResponseException(SalesInvalidResponseException ex) {
        log.warn("Invalid Tally sales response: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(SalesTallyErrorException.class)
    public ResponseEntity<ErrorResponse> handleSalesTallyErrorException(SalesTallyErrorException ex) {
        log.warn("Tally reported a sales error: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(SalesAgentStatusException.class)
    public ResponseEntity<ErrorResponse> handleSalesAgentStatusException(SalesAgentStatusException ex) {
        log.warn("Tally Agent returned failure status: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    // =========================================================
    // FRAMEWORK
    // =========================================================

    @ExceptionHandler(EmailNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEmailNotFoundException(EmailNotFoundException ex) {
        log.warn("Email not found: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
}