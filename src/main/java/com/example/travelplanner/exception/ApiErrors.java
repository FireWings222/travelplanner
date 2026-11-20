package com.example.travelplanner.exception;

import com.example.travelplanner.integration.TravelProviders;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.example.travelplanner.controller")
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "입력값의 형식과 필수 항목을 확인해 주세요.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "여행 조건 또는 요금 입력을 확인해 주세요.");
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(NotFound.class)
    ProblemDetail missing(NotFound exception) { return problem(HttpStatus.NOT_FOUND, exception.getMessage()); }

    @ExceptionHandler(TravelProviders.Unavailable.class)
    ProblemDetail unavailable(TravelProviders.Unavailable exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "여행 데이터를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflict(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "다른 요청이 저장 내용을 변경했습니다. 일정 목록에서 다시 열어 주세요.");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
