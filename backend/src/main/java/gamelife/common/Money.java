package gamelife.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts are decimals with exactly 2 digits (brief section 5). */
public final class Money {
    private Money() {}

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal parse(String text) {
        return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
    }
}
