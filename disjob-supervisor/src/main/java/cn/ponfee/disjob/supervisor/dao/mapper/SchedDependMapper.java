/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.supervisor.dao.mapper;

import cn.ponfee.disjob.core.model.SchedDepend;

import java.util.List;
import java.util.Set;

/**
 * Mybatis mapper of sched_depend database table.
 *
 * @author Ponfee
 */
public interface SchedDependMapper {

    int batchInsert(List<SchedDepend> records);

    List<SchedDepend> findByParentJobId(long parentJobId);

    List<SchedDepend> findByChildJobIds(Set<Long> childJobIds);

    int deleteByParentJobId(long parentJobId);

    int deleteByChildJobId(long parentJobId);

}
