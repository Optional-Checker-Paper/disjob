/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.common.util;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Proxy utility class.
 *
 * @author Ponfee
 */
public class ProxyUtils {

    /**
     * Creates jdk proxy instance
     *
     * @param interfaceType     the interface class
     * @param invocationHandler jdk invocation handler
     * @param <T>               the interface type
     * @return jdk proxy instance
     */
    public static <T> T create(Class<T> interfaceType, InvocationHandler invocationHandler) {
        Class<?>[] interfaces = {interfaceType};
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), interfaces, invocationHandler);
    }

    /**
     * Returns the proxy target object
     *
     * @param object the object
     * @return target object
     * @throws Exception
     */
    public static Object getTargetObject(Object object) throws Exception {
        if (!AopUtils.isAopProxy(object)) {
            return object;
        }
        if (object instanceof Advised) {
            return ((Advised) object).getTargetSource().getTarget();
        }
        if (AopUtils.isJdkDynamicProxy(object)) {
            return getProxyTargetObject(Fields.get(object, "h"));
        }
        if (AopUtils.isCglibProxy(object)) {
            return getProxyTargetObject(Fields.get(object, "CGLIB$CALLBACK_0"));
        }
        return object;
    }

    private static Object getProxyTargetObject(Object proxy) throws Exception {
        AdvisedSupport advisedSupport = (AdvisedSupport) Fields.get(proxy, "advised");
        return advisedSupport.getTargetSource().getTarget();
    }

}
