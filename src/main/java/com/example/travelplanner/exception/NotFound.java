package com.example.travelplanner.exception;

public class NotFound extends RuntimeException {
    /** 요청한 일정, 투어 또는 노선이 없다는 사유를 API 오류 처리에 전달합니다. */
    public NotFound(String message) { super(message); }
}
