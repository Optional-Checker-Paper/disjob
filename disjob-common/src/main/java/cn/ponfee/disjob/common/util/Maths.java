/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.common.util;

import org.springframework.util.Assert;

/**
 * 数学算术
 * 取模：Modulo Operation
 *
 * @author Ponfee
 */
public class Maths {

    /**
     * 以2为底n的对数
     *
     * @param n the value
     * @return a value of log(n)/log(2)
     */
    public static strictfp double log2(double n) {
        return log(n, 2);
    }

    /**
     * 求以base为底n的对数
     * {@link Math#log10(double) }  求以10为底n的对数（lg）
     * {@link Math#log(double)   }  以e为底n的对数（自然对数，ln）
     * {@link Math#log1p(double) }  以e为底n+1的对数
     *
     * @param n     a value
     * @param base  底数
     * @return a double of logarithm
     */
    public static strictfp double log(double n, double base) {
        return Math.log(n) / Math.log(base);
    }

    /**
     * rotate shift left，循环左移位操作：0<=n<=32
     *
     * @param x the value
     * @param n shift bit len
     * @return a number of rotate left result
     */
    public static int rotateLeft(int x, int n) {
        return (x << n) | (x >>> (32 - n));
    }

    /**
     * <pre>
     * Returns a long value of bit count mask
     * calculate the bit counts mask long value
     *   a: (1 << bits) - 1
     *   b: -1L ^ (-1L << bits)
     *   c: ~(-1L << bits)
     *   d: Long.MAX_VALUE >>> (63 - bits)
     *
     *  bitsMask(0)  -> 0                   -> 0000000000000000000000000000000000000000000000000000000000000000
     *  bitsMask(1)  -> 1                   -> 0000000000000000000000000000000000000000000000000000000000000001
     *  bitsMask(2)  -> 3                   -> 0000000000000000000000000000000000000000000000000000000000000011
     *  bitsMask(10) -> 1023                -> 0000000000000000000000000000000000000000000000000000001111111111
     *  bitsMask(20) -> 1048575             -> 0000000000000000000000000000000000000000000011111111111111111111
     *  bitsMask(63) -> 9223372036854775807 -> 0111111111111111111111111111111111111111111111111111111111111111
     *  bitsMask(64) -> -1                  -> 1111111111111111111111111111111111111111111111111111111111111111
     * </pre>
     *
     * @param bits the bit count
     * @return a long value
     */
    public static long bitsMask(int bits) {
        Assert.isTrue(bits >= 0 && bits <= Long.SIZE, "bits must range [0,64].");
        return bits == Long.SIZE ? -1 : ~(-1L << bits);
    }

    /**
     * Returns a long value for {@code base}<sup>{@code exponent}</sup>.
     *
     * @param base      the base
     * @param exponent  the exponent
     * @return a long value for {@code base}<sup>{@code exponent}</sup>.
     */
    public static long pow(long base, int exponent) {
        if (exponent == 0) {
            return 1;
        }

        long result = base;
        while (--exponent > 0) {
            result *= base;
        }
        return result;
    }

    public static int abs(int a) {
        // Integer.MIN_VALUE & 0x7FFFFFFF = 0
        return (a == Integer.MIN_VALUE) ? Integer.MAX_VALUE : (a < 0) ? -a : a;
    }

    public static long abs(long a) {
        return (a == Long.MIN_VALUE) ? Long.MAX_VALUE : (a < 0) ? -a : a;
    }

    // ------------------------------------------------------------------------int plus/minus

    public static int plus(int a, int b) {
        if (a > 0 && b > 0) {
            return Integer.MAX_VALUE - b < a ? Integer.MAX_VALUE : a + b;
        } else if (a < 0 && b < 0) {
            return Integer.MIN_VALUE - b > a ? Integer.MIN_VALUE : a + b;
        } else {
            return a + b;
        }
    }

    public static int minus(int a, int b) {
        if (a > 0 && b < 0) {
            return Integer.MAX_VALUE + b < a ? Integer.MAX_VALUE : a - b;
        } else if (a < 0 && b > 0) {
            return Integer.MIN_VALUE + b > a ? Integer.MIN_VALUE : a - b;
        } else {
            return a - b;
        }
    }

    // ------------------------------------------------------------------------long plus/minus

    public static long plus(long a, long b) {
        if (a > 0 && b > 0) {
            return Long.MAX_VALUE - b < a ? Long.MAX_VALUE : a + b;
        } else if (a < 0 && b < 0) {
            return Long.MIN_VALUE - b > a ? Long.MIN_VALUE : a + b;
        } else {
            return a + b;
        }
    }

    public static long minus(long a, long b) {
        if (a > 0 && b < 0) {
            return Long.MAX_VALUE + b < a ? Long.MAX_VALUE : a - b;
        } else if (a < 0 && b > 0) {
            return Long.MIN_VALUE + b > a ? Long.MIN_VALUE : a - b;
        } else {
            return a - b;
        }
    }

    /**
     * Returns the greatest common divisor
     *
     * @param a the first number
     * @param b the second number
     * @return gcd
     */
    public static int gcd(int a, int b) {
        if (a < 0 || b < 0) {
            throw new ArithmeticException();
        }

        if (a == 0 || b == 0) {
            return Math.abs(a - b);
        }

        for (int c; (c = a % b) != 0;) {
            a = b;
            b = c;
        }
        return b;
    }

    /**
     * Returns the greatest common divisor in array
     *
     * @param array the int array
     * @return gcd
     */
    public static int gcd(int[] array) {
        int result = array[0];
        for (int i = 1; i < array.length; i++) {
            result = gcd(result, array[i]);
        }

        return result;
    }

}
