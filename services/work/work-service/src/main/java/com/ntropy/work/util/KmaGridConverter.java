package com.ntropy.work.util;

/**
 * 위경도(WGS84) → 기상청 단기예보 격자좌표(nx/ny) 변환.
 * 기상청이 공개한 LCC(Lambert Conformal Conic) 투영 변환식을 그대로 구현한 것으로,
 * 상수값(RE/GRID/SLAT1/SLAT2/OLON/OLAT/XO/YO)은 기상청 공식 값이라 임의 변경하면 안 된다.
 */
public final class KmaGridConverter {

    private static final double RE = 6371.00877; // 지구 반경(km)
    private static final double GRID = 5.0;       // 격자 간격(km)
    private static final double SLAT1 = 30.0;     // 표준위도 1
    private static final double SLAT2 = 60.0;     // 표준위도 2
    private static final double OLON = 126.0;     // 기준점 경도
    private static final double OLAT = 38.0;      // 기준점 위도
    private static final double XO = 43;          // 기준점 X좌표(GRID 단위)
    private static final double YO = 136;         // 기준점 Y좌표(GRID 단위)
    private static final double DEGRAD = Math.PI / 180.0;

    private KmaGridConverter() {
    }

    public record Grid(int nx, int ny) {
    }

    public static Grid toGrid(double latitude, double longitude) {
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + latitude * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = longitude * DEGRAD - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new Grid(nx, ny);
    }
}
