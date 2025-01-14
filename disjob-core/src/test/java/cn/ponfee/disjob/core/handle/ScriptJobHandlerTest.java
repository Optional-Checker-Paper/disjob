/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.core.handle;

import cn.ponfee.disjob.common.util.Jsons;
import cn.ponfee.disjob.core.handle.execution.ExecutingTask;
import cn.ponfee.disjob.core.handle.impl.ScriptJobHandler;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author Ponfee
 */
public class ScriptJobHandlerTest {

    @Test
    public void testShell() throws Exception {
        if (!SystemUtils.IS_OS_UNIX) {
            return;
        }

        ScriptJobHandler.ScriptParam scriptParam = new ScriptJobHandler.ScriptParam();
        scriptParam.setType(ScriptJobHandler.ScriptType.SHELL);
        scriptParam.setScript("#!/bin/sh\necho \"hello, shell!\"\n");

        ExecutingTask executingTask = new ExecutingTask();
        executingTask.setTaskId(1L);
        executingTask.setTaskParam(Jsons.toJson(scriptParam));

        ScriptJobHandler scriptJobHandler = new ScriptJobHandler();

        ExecuteResult execute = scriptJobHandler.execute(executingTask, Savepoint.DISCARD);
        Assertions.assertEquals("{\"code\":0,\"msg\":\"hello, shell!\\n\"}", Jsons.toJson(execute));
    }

    @Disabled
    @Test
    public void testPython() throws Exception {
        ScriptJobHandler.ScriptParam scriptParam = new ScriptJobHandler.ScriptParam();
        scriptParam.setType(ScriptJobHandler.ScriptType.PYTHON);
        scriptParam.setScript("print('hello, python!')\n");

        ExecutingTask executingTask = new ExecutingTask();
        executingTask.setTaskId(1L);
        executingTask.setTaskParam(Jsons.toJson(scriptParam));

        ScriptJobHandler scriptJobHandler = new ScriptJobHandler();

        ExecuteResult execute = scriptJobHandler.execute(executingTask, Savepoint.DISCARD);
        Assertions.assertEquals("{\"code\":0,\"msg\":\"OK\",\"data\":\"hello, python!\\n\"}", Jsons.toJson(execute));
    }

}
