/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.common.spring;

import cn.ponfee.disjob.common.exception.Throwables.ThrowingSupplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.util.function.Consumer;

/**
 * Spring transaction utility.
 *
 * @author Ponfee
 */
public class TransactionUtils {

    /**
     * 在事务提交后再执行
     *
     * @param action the action code
     */
    public static void doAfterTransactionCommit(final Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronization ts = new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            };
            TransactionSynchronizationManager.registerSynchronization(ts);
        } else {
            action.run();
        }
    }

    /**
     * 创建一个新事务，如果当前存在事务，则将这个事务挂起。
     * <p>内部事务与外部事务相互独立，互不依赖。
     *
     * @param txManager the txManager
     * @param action    the action code
     * @param log       the exception log
     * @param <R>       return type
     * @return do action result
     */
    public static <R> R doInRequiresNewTransaction(PlatformTransactionManager txManager,
                                                   ThrowingSupplier<R, Throwable> action,
                                                   Consumer<Throwable> log) {
        return doInPropagationTransaction(txManager, action, log, TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 如果当前存在事务则开启一个嵌套事务，如果当前不存在事务则新建一个事务并运行。
     * <p>内部事务为外部事务的一个子事务。
     * <p>内部事务的提交/回滚不影响外部事务的提交/回滚
     * <p>内部事务的提交/回滚最终依赖外部事务的提交/回滚。
     *
     * @param txManager the txManager
     * @param action    the action code
     * @param log       the exception log
     * @param <R>       return type
     * @return do action result
     */
    public static <R> R doInNestedTransaction(PlatformTransactionManager txManager,
                                              ThrowingSupplier<R, Throwable> action,
                                              Consumer<Throwable> log) {
        Assert.isTrue(
            TransactionSynchronizationManager.isActualTransactionActive(),
            "Do nested transaction must be in parent transaction."
        );
        return doInPropagationTransaction(txManager, action, log, TransactionDefinition.PROPAGATION_NESTED);
    }

    // ----------------------------------------------------------------------private methods

    private static <R> R doInPropagationTransaction(PlatformTransactionManager txManager,
                                                    ThrowingSupplier<R, Throwable> action,
                                                    Consumer<Throwable> log,
                                                    int transactionPropagation) {
        DefaultTransactionDefinition txDefinition = new DefaultTransactionDefinition();
        txDefinition.setPropagationBehavior(transactionPropagation);
        TransactionStatus status = txManager.getTransaction(txDefinition);
        try {
            R result = action.get();
            txManager.commit(status);
            return result;
        } catch (Throwable t) {
            txManager.rollback(status);
            log.accept(t);
            return null;
        }
    }

}
