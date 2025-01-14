/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.core.handle;

/**
 * Save task execution snapshot
 *
 * @author Ponfee
 */
@FunctionalInterface
public interface Savepoint {

    /**
     * Save the task execution snapshot
     *
     * @param taskId          the task id
     * @param executeSnapshot the execution snapshot data
     * @throws Exception if saved occur exception
     */
    void save(long taskId, String executeSnapshot) throws Exception;

    Savepoint DISCARD = (taskId, executeSnapshot) -> {};
}
