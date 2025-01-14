/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.common.base;

/**
 * Represents value enum type structure.
 *
 * @param <V> value type
 * @param <T> enum type
 * @author Ponfee
 */
public interface ValueEnum<V, T extends Enum<T> & ValueEnum<V, T>> {

    /**
     * Returns enum value
     *
     * @return enum value
     */
    V value();

    /**
     * Returns enum description
     *
     * @return enum description
     */
    String desc();

    static <V, T extends Enum<T> & ValueEnum<V, T>> T of(Class<T> type, V value) {
        if (type == null) {
            throw new IllegalArgumentException("Enum type cannot be null: " + type);
        }
        if (value == null) {
            throw new IllegalArgumentException("Enum value cannot be null.");
        }
        for (T e : type.getEnumConstants()) {
            if (value.equals(e.value())) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid enum value: " + value);
    }

}
