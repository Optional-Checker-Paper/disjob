/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.registry.consul;

import org.junit.jupiter.api.Assertions;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * <pre>
 * Embedded consul server based testcontainers.
 * <a href="https://www.testcontainers.org/modules/consul/">testcontainers consul</a>
 * 1、startup local docker environment
 * 2、pull docker consul image: docker pull consul:1.14.2
 * 3、"consul:1.14.2" is docker {image-name:version}
 *
 * 查看latest的具体版本号：docker image inspect {image-name}:latest | grep -i version
 * </pre>
 *
 * @author Ponfee
 */
public final class EmbeddedConsulServerTestcontainers {

    private static final String CONSUL_DOCKER_IMAGE_NAME = "consul:1.14.2";
    private static final List<String> PORT_BINDINGS = Arrays.asList("8500:8500", "8502:8502");

    public static void main(String[] args) {
        String key = "config/testing1", val = "value123";

        DockerImageName consulImage = DockerImageName.parse(CONSUL_DOCKER_IMAGE_NAME).asCompatibleSubstituteFor("consul-test");

        ConsulContainer consulContainer = new ConsulContainer(consulImage)
            .withConsulCommand("kv put " + key + " " + val);

        consulContainer.setPortBindings(PORT_BINDINGS);
        Runtime.getRuntime().addShutdownHook(new Thread(consulContainer::close));
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(consulContainer::close));
            System.out.println("Embedded docker consul server starting...");
            consulContainer.start();
            System.out.println("Embedded docker consul server started!");

            Assertions.assertEquals(PORT_BINDINGS, consulContainer.getPortBindings());
            Assertions.assertEquals(Arrays.asList(8500, 8502), consulContainer.getExposedPorts());
            Assertions.assertEquals(val, consulContainer.execInContainer("consul", "kv", "get", key).getStdout().trim());

            new CountDownLatch(1).await();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            consulContainer.close();
        }
    }

}
