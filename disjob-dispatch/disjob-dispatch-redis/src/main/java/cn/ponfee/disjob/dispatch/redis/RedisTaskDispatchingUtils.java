/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.dispatch.redis;

import cn.ponfee.disjob.core.base.JobConstants;
import cn.ponfee.disjob.core.base.Worker;

/**
 * Dispatch utility.
 *
 * @author Ponfee
 */
final class RedisTaskDispatchingUtils {

    private static final String REDIS_DISPATCH_KEY_PREFIX = JobConstants.DISJOB_KEY_PREFIX + ".tasks.dispatch.";

    static String buildDispatchTasksKey(Worker worker) {
        return REDIS_DISPATCH_KEY_PREFIX + worker.serialize();
    }

}
