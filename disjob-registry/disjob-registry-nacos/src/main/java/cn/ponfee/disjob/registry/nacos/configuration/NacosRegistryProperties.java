/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.registry.nacos.configuration;

import cn.ponfee.disjob.core.base.JobConstants;
import cn.ponfee.disjob.registry.AbstractRegistryProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cglib.beans.BeanMap;

import java.util.Properties;

/**
 * Nacos registry configuration properties.
 *
 * @author Ponfee
 * @see com.alibaba.nacos.api.PropertyKeyConst
 */
@Getter
@Setter
@ConfigurationProperties(prefix = JobConstants.DISJOB_REGISTRY_KEY_PREFIX + ".nacos")
public class NacosRegistryProperties extends AbstractRegistryProperties {
    private static final long serialVersionUID = 2961908276104522907L;

    /**
     * Nacos server address
     */
    private String serverAddr = "localhost:8848";

    /**
     * Nacos server username
     */
    private String username = "nacos";

    /**
     * Nacos server password
     */
    private String password = "nacos";

    /**
     * Nacos server naming load cache at start
     */
    private String namingLoadCacheAtStart = "true";

    public Properties toProperties() {
        Properties properties = new Properties();
        BeanMap.create(this).forEach((k, v) -> {
            if (v != null) {
                properties.put(k, v);
            }
        });
        return properties;
    }

}
