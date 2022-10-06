package cn.ponfee.scheduler.dispatch.redis.configuration;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enable redis task dispatch
 *
 * @author Ponfee
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(RedisTaskDispatchingConfiguration.class)
public @interface EnableRedisTaskDispatching {

}