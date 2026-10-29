package com.mockstudio.common;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Times {

    private Times() {
    }

    /** 与 DATETIME(3) 精度一致，避免数据库舍入导致的时间回退。 */
    public static Instant now(Clock clock) {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
