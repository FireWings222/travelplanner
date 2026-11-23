package com.example.travelplanner.integration;

import com.example.travelplanner.dto.Travel.*;
import java.util.List;

public final class TravelProviders {
    /** 외부 데이터 제공자 인터페이스를 모아 둔 클래스의 직접 생성을 막습니다. */
    private TravelProviders() {}

    public interface Catalog {
        /** 카탈로그 구현이 도시 검색에 사용할 전체 여행지 목록을 반환하는 계약입니다. */
        List<Destination> destinations();
        /** 카탈로그 구현이 일정과 투어에 사용할 전체 관광지 목록을 제공하는 계약입니다. */
        List<Attraction> attractions();
        /** 카탈로그 구현이 여행지의 기준 지점과 좌표를 제공하는 계약입니다. */
        Place center(String destination);
    }

    public interface Routes {
        /** 경로 제공자가 도시 간 여정의 이동 구간과 구간별 교통수단 후보를 반환하는 계약입니다. */
        List<List<Segment>> options(String origin, String destination, Place from, Place to);
        /** 경로 제공자가 관광지 등 두 지점 사이의 교통수단 후보를 반환하는 계약입니다. */
        List<Segment> local(Place from, Place to);
    }

    public interface Tours {
        /** 투어 서비스가 조회 결과를 필터링할 수 있도록 제공자의 플랫폼을 알려주는 계약입니다. */
        Platform platform();
        /** 여러 플랫폼을 합친 제공자에 한해 검색 가능한 플랫폼을 따로 지정할 수 있습니다. */
        default boolean supports(Platform requested) { return platform() == requested; }
        /** 투어 제공자가 지정 도시의 일일투어 후보를 반환하는 계약입니다. */
        List<Tour> search(String destination);
    }

    public static class Unavailable extends RuntimeException {
        /** 외부 제공자 호출이 실패했을 때 서비스나 API 오류 처리에 전달할 예외를 만듭니다. */
        public Unavailable(String message, Throwable cause) { super(message, cause); }
    }
}
