/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.dispatch.http.configuration;

import cn.ponfee.disjob.common.base.TimingWheel;
import cn.ponfee.disjob.core.base.HttpProperties;
import cn.ponfee.disjob.core.base.RetryProperties;
import cn.ponfee.disjob.core.base.Supervisor;
import cn.ponfee.disjob.core.base.Worker;
import cn.ponfee.disjob.core.param.ExecuteTaskParam;
import cn.ponfee.disjob.dispatch.TaskDispatcher;
import cn.ponfee.disjob.dispatch.TaskReceiver;
import cn.ponfee.disjob.dispatch.configuration.BaseTaskDispatchingAutoConfiguration;
import cn.ponfee.disjob.dispatch.http.HttpTaskDispatcher;
import cn.ponfee.disjob.dispatch.http.HttpTaskReceiver;
import cn.ponfee.disjob.registry.DiscoveryRestTemplate;
import cn.ponfee.disjob.registry.SupervisorRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

/**
 * Spring autoconfiguration for http task dispatching.
 *
 * @author Ponfee
 */
public class HttpTaskDispatchingAutoConfiguration extends BaseTaskDispatchingAutoConfiguration {

    /**
     * Configuration http task dispatcher.
     */
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnBean(Supervisor.class)
    @ConditionalOnMissingBean
    @Bean
    public TaskDispatcher taskDispatcher(HttpProperties httpProperties,
                                         RetryProperties retryProperties,
                                         SupervisorRegistry discoveryWorker,
                                         @Nullable TimingWheel<ExecuteTaskParam> timingWheel,
                                         @Nullable ObjectMapper objectMapper) {
        httpProperties.check();
        retryProperties.check();
        DiscoveryRestTemplate<Worker> discoveryRestTemplate = DiscoveryRestTemplate.<Worker>builder()
            .httpConnectTimeout(httpProperties.getConnectTimeout())
            .httpReadTimeout(httpProperties.getReadTimeout())
            .retryMaxCount(retryProperties.getMaxCount())
            .retryBackoffPeriod(retryProperties.getBackoffPeriod())
            .objectMapper(objectMapper)
            .discoveryServer(discoveryWorker)
            .build();

        return new HttpTaskDispatcher(discoveryRestTemplate, retryProperties, timingWheel);
    }

    /**
     * Configuration http task receiver.
     */
    @ConditionalOnBean(Worker.class)
    @ConditionalOnMissingBean
    @Bean
    public TaskReceiver taskReceiver(Worker currentWorker, TimingWheel<ExecuteTaskParam> timingWheel) {
        return new HttpTaskReceiver(currentWorker, timingWheel);
    }

}
