package triplesix.babyfeed;

import java.text.NumberFormat;
import java.util.Locale;

final class Amounts {
    static String number(long value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    static String ml(long value) {
        return number(value) + " ml";
    }

    static String number(long value, Locale locale) {
        return NumberFormat.getIntegerInstance(locale).format(value);
    }

    static String ml(long value, Locale locale) {
        return number(value, locale) + " ml";
    }
}
