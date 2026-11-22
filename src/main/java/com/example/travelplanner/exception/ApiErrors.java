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
    /** 서비스가 던진 잘못된 입력 오류를 HTTP 400과 사용자용 메시지로 변환합니다. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** 필수 요청값 누락과 자료형 오류를 HTTP 400 응답으로 변환합니다. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "입력값의 형식과 필수 항목을 확인해 주세요.");
    }

    /** 입력 검증 오류를 필드별로 묶어 프런트엔드에 HTTP 400 응답으로 전달합니다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "여행 조건 또는 요금 입력을 확인해 주세요.");
        problem.setProperty("fields", fields);
        return problem;
    }

    /** 찾을 수 없는 여행 데이터 요청을 HTTP 404로 변환합니다. */
    @ExceptionHandler(NotFound.class)
    ProblemDetail missing(NotFound exception) { return problem(HttpStatus.NOT_FOUND, exception.getMessage()); }

    /** 외부 데이터 제공 실패를 HTTP 503과 재시도 안내로 변환합니다. */
    @ExceptionHandler(TravelProviders.Unavailable.class)
    ProblemDetail unavailable(TravelProviders.Unavailable exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "여행 데이터를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    /** 다른 저장 요청이 먼저 일정을 수정했을 때 HTTP 409로 목록을 다시 열도록 안내합니다. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflict(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "다른 요청이 저장 내용을 변경했습니다. 일정 목록에서 다시 열어 주세요.");
    }

    /** API 오류 응답에서 공통으로 사용하는 HTTP 상태와 메시지를 구성합니다. */
    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
