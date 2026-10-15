package triplesix.babyfeed;

public final class FeedRules {
    public static String countdown(long remaining) {
        if (remaining <= 0) return "(đã tới giờ)";
        long minutes = (remaining + 59999) / 60000;
        return "("
                + (minutes >= 60 ? minutes / 60 + "h " : "")
                + (minutes % 60 > 0 ? minutes % 60 + "p " : "")
                + "tới)";
    }

    public static int interval(int hours, int minutes) {
        if (hours < 0 || minutes < 0 || minutes > 59)
            throw new IllegalArgumentException("Giờ không được âm, phút từ 0 đến 59.");
        return Math.addExact(Math.multiplyExact(hours, 60), minutes);
    }

    public static int duration(String input) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(?:(\\d{1,2})h)?(?:(\\d{1,3})p)?$")
                        .matcher(input.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", ""));
        if (!m.matches() || (m.group(1) == null && m.group(2) == null))
            throw new IllegalArgumentException("Nhập khoảng cách dạng 1h30p, 2h hoặc 45p.");
        int hours = m.group(1) == null ? 0 : Integer.parseInt(m.group(1)),
                minutes = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (hours > 0 && minutes >= 60)
            throw new IllegalArgumentException("Phần phút phải nhỏ hơn 60, ví dụ 1h30p.");
        int total = hours * 60 + minutes;
        next(0, total);
        return total;
    }

    public static String durationLabel(int minutes) {
        return minutes == 0
                ? "0p"
                : (minutes / 60 > 0 ? minutes / 60 + "h" : "")
                        + (minutes % 60 > 0 ? minutes % 60 + "p" : "");
    }

    public static int consumed(int prepared, int left) {
        if (prepared <= 0 || prepared > 2000 || left < 0 || left > prepared)
            throw new IllegalArgumentException("Lượng còn lại phải từ 0 đến lượng đã pha.");
        return prepared - left;
    }

    public static int preparedFromConsumed(int consumed, int discarded) {
        if (consumed < 0 || discarded < 0)
            throw new IllegalArgumentException("Lượng còn lại phải từ 0 đến lượng đã pha.");
        int prepared = Math.addExact(consumed, discarded);
        consumed(prepared, discarded);
        return prepared;
    }

    public static long next(long finished, int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Khoảng cách không được âm.");
        return Math.addExact(finished, minutes * 60000L);
    }
}
