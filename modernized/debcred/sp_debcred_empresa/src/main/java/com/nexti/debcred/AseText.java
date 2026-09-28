package com.nexti.debcred;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sybase text and number semantics the procedure relies on.
 *
 * <ul>
 *   <li>{@code =} on char/varchar ignores trailing blanks: {@code 'SPI  ' = 'SPI'} is true.</li>
 *   <li>Comparisons the legacy writes with {@code ltrim(rtrim(...))} also ignore leading blanks
 *       (TRANSWIFT, IMPADUAN, CORPEI, PAGOPRV, SPI at lines 558 and 1348).</li>
 *   <li>Only the blank (char 32) is padding; a tab is data.</li>
 *   <li>A NULL never equals anything, and converting a NULL number gives a NULL text.</li>
 *   <li>{@code convert(char(2), n)} right-pads with blanks: {@code 1 -> "1 "}.</li>
 * </ul>
 */
final class AseText {

    private AseText() {
    }

    /** {@code value = literal} with Sybase char semantics: trailing blanks ignored, NULL never equal. */
    static boolean equalsIgnoringTrailingBlanks(String value, String literal) {
        return value != null && rtrim(value).equals(literal);
    }

    /** {@code ltrim(rtrim(value)) = literal}. */
    static boolean trimmedEquals(String value, String literal) {
        return value != null && ltrim(rtrim(value)).equals(literal);
    }

    /** {@code convert(char(width), number)}: decimal text right-padded with blanks; NULL stays NULL. */
    static String toChar(Integer number, int width) {
        return number == null ? null : String.format("%-" + width + "s", number);
    }

    /** {@code convert(varchar, number)}: decimal text; NULL stays NULL. */
    static String toVarchar(Integer number) {
        return number == null ? null : Integer.toString(number);
    }

    /** {@code ltrim(rtrim(value))}; an all-blank result is NULL, as in ASE. */
    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = ltrim(rtrim(value));
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** {@code convert(char(width), text)}: cut or right-padded with blanks; NULL stays NULL. */
    static String toChar(String value, int width) {
        if (value == null) {
            return null;
        }
        return value.length() >= width ? value.substring(0, width) : String.format("%-" + width + "s", value);
    }

    /** Assignment to a {@code varchar(width)} variable: longer text is cut; NULL stays NULL. */
    static String varchar(String value, int width) {
        return value == null || value.length() <= width ? value : value.substring(0, width);
    }

    /**
     * {@code substring(value, start, length)} in ASE: NULL when the value is NULL, the length is not
     * positive or the start is past the end.
     */
    static String substring(String value, int start, int length) {
        if (value == null || length <= 0 || start > value.length()) {
            return null;
        }
        int from = Math.max(start, 1) - 1;
        return value.substring(from, Math.min(value.length(), start - 1 + length));
    }

    /** {@code convert(varchar(11), money)}: plain digits, two decimals rounded half up; NULL stays NULL. */
    static String moneyToVarchar(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** {@code isnull(value, 0)} for money. */
    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** {@code value > 0} where a NULL compares false. */
    static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    private static String ltrim(String s) {
        int start = 0;
        while (start < s.length() && s.charAt(start) == ' ') {
            start++;
        }
        return s.substring(start);
    }
}
