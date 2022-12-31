package cn.ponfee.scheduler.registry.nacos.configuration;

import cn.ponfee.scheduler.core.base.JobConstants;
import cn.ponfee.scheduler.core.base.Supervisor;
import cn.ponfee.scheduler.core.base.Worker;
import cn.ponfee.scheduler.registry.SupervisorRegistry;
import cn.ponfee.scheduler.registry.WorkerRegistry;
import cn.ponfee.scheduler.registry.nacos.NacosServerRegistry;
import cn.ponfee.scheduler.registry.nacos.NacosSupervisorRegistry;
import cn.ponfee.scheduler.registry.nacos.NacosWorkerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;

import static cn.ponfee.scheduler.core.base.JobConstants.SPRING_BEAN_NAME_CURRENT_SUPERVISOR;
import static cn.ponfee.scheduler.core.base.JobConstants.SPRING_BEAN_NAME_CURRENT_WORKER;

/**
 * Nacos server register & discovery configuration.
 *
 * @author Ponfee
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({NacosServerRegistry.class})
public class NacosServerRegistryConfigure {

    /**
     * Configuration nacos supervisor registry.
     */
    @Configuration(proxyBeanMethods = false)
    @AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
    @DependsOn(SPRING_BEAN_NAME_CURRENT_SUPERVISOR)
    @ConditionalOnBean({Supervisor.class})
    public static class NacosSupervisorRegistryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SupervisorRegistry supervisorRegistry(@Value("${" + JobConstants.SCHEDULER_NAMESPACE + ":}") String namespace,
                                                     NacosProperties config) {
            return new NacosSupervisorRegistry(namespace, config);
        }
    }

    /**
     * Configuration nacos worker registry.
     */
    @Configuration(proxyBeanMethods = false)
    @AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
    @DependsOn(SPRING_BEAN_NAME_CURRENT_WORKER)
    @ConditionalOnBean({Worker.class})
    public static class NacosWorkerRegistryConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public WorkerRegistry workerRegistry(@Value("${" + JobConstants.SCHEDULER_NAMESPACE + ":}") String namespace,
                                             NacosProperties config) {
            return new NacosWorkerRegistry(namespace, config);
        }
    }

}