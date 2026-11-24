package com.example.travelplanner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class PagesController {
    private final String kakaoMapJavaScriptKey;

    /** 실행 환경에서 카카오맵 JavaScript 키를 읽습니다. */
    public PagesController(@Value("${KAKAO_MAP_JS_KEY:}") String kakaoMapJavaScriptKey) {
        this.kakaoMapJavaScriptKey = kakaoMapJavaScriptKey.strip();
    }

    /** 공통 Thymeleaf 레이아웃에서 지도 SDK 설정에 쓸 브라우저 키를 반환합니다. */
    @ModelAttribute("kakaoMapJavaScriptKey")
    public String kakaoMapJavaScriptKey() { return kakaoMapJavaScriptKey; }

    /** 루트 주소를 여행 조건을 입력하는 index 템플릿에 연결합니다. */
    /** 검색 결과 주소를 구간별 교통수단을 비교하는 search 템플릿에 연결합니다. */
    /** 일정 주소를 날짜별 관광지와 저장 목록을 보여주는 itinerary 템플릿에 연결합니다. */
    /** 일일투어 주소를 투어 검색과 비교 상품 선택을 제공하는 tours 템플릿에 연결합니다. */
    /** 비용 비교 주소를 자유여행과 투어를 보여주는 compare 템플릿에 연결합니다. */
    @GetMapping("/") public String home() { return "index"; }
    @GetMapping("/search") public String search() { return "search"; }
    @GetMapping("/itinerary") public String itinerary() { return "itinerary"; }
    @GetMapping("/tours") public String tours() { return "tours"; }
    @GetMapping("/compare") public String compare() { return "compare"; }
}
