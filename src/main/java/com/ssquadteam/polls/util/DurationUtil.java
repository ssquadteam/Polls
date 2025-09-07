package com.ssquadteam.polls.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationUtil {

    public static final long DEFAULT_DURATION_SECONDS = 3600; // 1 hour

    private static final Pattern PATTERN = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);

    public static Long parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim().toLowerCase(Locale.ROOT);
        Matcher m = PATTERN.matcher(raw);
        if (!m.matches()) return null;
        long days = parseGroup(m, 1);
        long hours = parseGroup(m, 2);
        long minutes = parseGroup(m, 3);
        long seconds = parseGroup(m, 4);
        long total = days * 86400 + hours * 3600 + minutes * 60 + seconds;
        return total == 0 ? null : total;
    }

    private static long parseGroup(Matcher m, int idx) {
        String g = m.group(idx);
        return g == null || g.isBlank() ? 0 : Long.parseLong(g);
    }
}
