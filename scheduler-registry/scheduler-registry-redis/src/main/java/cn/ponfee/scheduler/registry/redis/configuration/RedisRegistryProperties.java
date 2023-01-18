/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.scheduler.registry.redis.configuration;

import cn.ponfee.scheduler.core.base.JobConstants;
import cn.ponfee.scheduler.registry.AbstractRegistryProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis registry configuration properties.
 *
 * @author Ponfee
 */
@Data
@ConfigurationProperties(prefix = JobConstants.SCHEDULER_REGISTRY_KEY_PREFIX + ".redis")
public class RedisRegistryProperties extends AbstractRegistryProperties {
    private static final long serialVersionUID = -6079627443420731390L;

    /**
     * Session timeout milliseconds
     */
    private long sessionTimeoutMs = 30 * 1000;

    /**
     * Registry period milliseconds
     */
    private long registryPeriodMs = 3 * 1000;

    /**
     * Discovery period milliseconds
     */
    private long discoveryPeriodMs = 3 * 1000;

}