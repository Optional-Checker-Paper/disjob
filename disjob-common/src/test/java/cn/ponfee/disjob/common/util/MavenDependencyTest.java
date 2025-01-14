/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.common.util;

import cn.ponfee.disjob.common.tuple.Tuple4;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Maven dependency test
 *
 * @author Ponfee
 */
public class MavenDependencyTest {

    public static void main(String[] args) throws IOException {
        StopWatch stopWatch = StopWatch.createStarted();
        String dependencyTree = dependencyTree();
        stopWatch.stop();

        System.out.println("\n\n<<<-----------------------------------dependency tree----------------------------------->>>");
        System.out.println(dependencyTree);
        System.out.println("<<<-----------------------------------dependency tree----------------------------------->>>\n\n");

        System.out.println("\n\n<<<-----------------------------------dependency jar conflict----------------------------------->>>");
        String result = parseConflictedVersionJar(dependencyTree);
        if (StringUtils.isBlank(result)) {
            System.out.println("Not conflicted version jar");
        } else {
            System.out.println(result);
        }
        System.out.println("<<<-----------------------------------dependency jar conflict----------------------------------->>>\n\n");

        System.out.println("\n\nExecute maven install & dependency tree cost time: " + stopWatch);
    }

    private static String dependencyTree() {
        String path = new File(MavenProjects.getProjectBaseDir()).getParentFile().getAbsolutePath() + "/";
        String installCmd = "bash " + path + "mvnw clean install -DskipTests -U -f " + path + "pom.xml";

        // String treeCmd = "mvn dependency:tree -f " + path + "pom.xml";
        // -B: Run in non-interactive (batch) mode (disables output color)
        // -q: 安静模式,只输出ERROR
        String treeCmd = "bash " + path + "mvnw -B dependency:tree -f " + path + "pom.xml";
        try {
            execute(installCmd);
            return execute(treeCmd);
        } catch (Exception e) {
            return ExceptionUtils.rethrow(e);
        }
    }

    private static String parseConflictedVersionJar(String text) {
        StringBuilder builder = new StringBuilder();
        Arrays.stream(text.split("\n"))
            .filter(e -> StringUtils.startsWithAny(e, "[INFO] +- ", "[INFO] |  "))
            .map(s -> s.replaceAll("^\\[INFO] ", "").replaceAll("^\\W+", "").trim())
            .map(s -> s.split(":"))
            .filter(e -> e.length >= 4)
            .map(e -> Tuple4.of(e[0], e[1], e[2], e[3]))
            .distinct()
            .collect(Collectors.groupingBy(e -> e.a + ":" + e.b))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .filter(e -> e.getValue().stream().noneMatch(x -> StringUtils.endsWithAny(x.d, "-x86_64", "-aarch_64")))
            .forEach(e -> {
                builder.append(e.getKey()).append("\n");
                e.getValue().forEach(x -> builder.append("    " + x.a + ":" + x.b + ":" + x.c + ":" + x.d + "\n"));
            });
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    private static String execute(String command) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(out);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(120000L);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setWatchdog(watchdog);
        executor.execute(CommandLine.parse(command));
        return out.toString(StandardCharsets.UTF_8.name());
    }

}
