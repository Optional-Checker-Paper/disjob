/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.core.route.count;

/**
 * Atomic counter
 *
 * @author Ponfee
 */
public abstract class AtomicCounter {

    /**
     * Gets the current count value.
     *
     * @return current count value
     */
    public abstract long get();

    /**
     * Sets a new value to count.
     *
     * @param newValue the new value
     */
    public abstract void set(long newValue);

    /**
     * Gets the current count value and then add one.
     *
     * @return current count value
     */
    public final long getAndIncrement() {
        return getAndAdd(1);
    }

    /**
     * Gets the current count value and then add specified number of delta.
     *
     * @param delta the number of delta
     * @return current count value
     */
    public abstract long getAndAdd(long delta);

}
