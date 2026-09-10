package com.example.SalesDashboard.user.exception.handlers;

import com.example.SalesDashboard.framework.dto.ErrorResponse;
import com.example.SalesDashboard.user.exception.InactiveAccountException;
import com.example.SalesDashboard.user.exception.InvalidCredentialsException;
import com.example.SalesDashboard.user.exception.InvalidFormatPasswordException;
import com.example.SalesDashboard.user.exception.UserAlreadyExistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

   private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

   @ExceptionHandler(UserAlreadyExistException.class)
   public ResponseEntity<ErrorResponse> handleUserAlreadyExistException(UserAlreadyExistException ex) {
       log.warn("User registration failed: {}", ex.getMessage());
       ErrorResponse errorResponse = new ErrorResponse("USER_ALREADY_REGISTERED", ex.getMessage());
       return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
   }

   @ExceptionHandler(InvalidFormatPasswordException.class)
   public ResponseEntity<ErrorResponse> handleInvalidFormatPasswordException(InvalidFormatPasswordException ex) {
       log.warn("Invalid format password: {}", ex.getMessage());
       ErrorResponse errorResponse = new ErrorResponse("INVALID_FORMAT_PASSWORD_REGISTERED", ex.getMessage());
       return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
   }

   @ExceptionHandler(InvalidCredentialsException.class)
   public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex) {
       log.warn("Login failed: {}", ex.getMessage());
       ErrorResponse errorResponse = new ErrorResponse("INVALID_CREDENTIALS", ex.getMessage());
       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
   }

   @ExceptionHandler(InactiveAccountException.class)
   public ResponseEntity<ErrorResponse> handleInactiveAccountException(InactiveAccountException ex) {
       log.warn("Login blocked: {}", ex.getMessage());
       ErrorResponse errorResponse = new ErrorResponse("ACCOUNT_INACTIVE", ex.getMessage());
       return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
   }
}