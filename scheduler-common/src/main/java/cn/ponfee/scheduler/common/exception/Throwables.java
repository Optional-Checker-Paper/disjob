/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.scheduler.common.exception;

import cn.ponfee.scheduler.common.concurrent.Threads;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Throwable utilities.
 *
 * @author Ponfee
 */
public final class Throwables {

    private static final Logger LOG = LoggerFactory.getLogger(Throwables.class);

    /**
     * Gets the root cause throwable stack trace
     *
     * @param throwable the throwable
     * @return a string of throwable stack trace information
     */
    public static String getRootCauseStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return ExceptionUtils.getStackTrace(throwable);
    }

    public static String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        List<Throwable> list = ExceptionUtils.getThrowableList(throwable);
        for (int i = list.size() - 1; i >= 0; i--) {
            String message = list.get(i).getMessage();
            if (StringUtils.isNotBlank(message)) {
                return "error: " + message;
            }
        }

        return "error: <" + ClassUtils.getName(throwable.getClass()) + ">";
    }

    /**
     * eg: new Thread(Throwables.runnable(printer::print))
     *
     * @param runnable the ThrowingRunnable
     * @param <T>      the type of Throwable
     * @return Runnable instance
     */
    public static <T extends Throwable> Runnable runnable(ThrowingRunnable<T> runnable) {
        return ThrowingRunnable.checked(runnable);
    }

    public static <R, T extends Throwable> Callable<R> callable(ThrowingCallable<R, T> callable) {
        return ThrowingCallable.checked(callable);
    }

    public static <E, T extends Throwable> Consumer<E> consumer(ThrowingConsumer<E, T> consumer) {
        return ThrowingConsumer.checked(consumer);
    }

    public static <E, R, T extends Throwable> Function<E, R> function(ThrowingFunction<E, R, T> function) {
        return ThrowingFunction.checked(function);
    }

    public static <R, T extends Throwable> Supplier<R> supplier(ThrowingSupplier<R, T> supplier) {
        return ThrowingSupplier.checked(supplier);
    }

    public static <E, T extends Throwable> Comparator<? super E> comparator(ThrowingComparator<E, T> comparator) {
        return ThrowingComparator.checked(comparator);
    }

    // -------------------------------------------------------------------------------caught

    public static void caught(ThrowingRunnable<?> runnable) {
        caught(runnable, "");
    }

    public static void caught(ThrowingRunnable<?> runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            LOG.error(message == null ? "" : message, t);
            Threads.interruptIfNecessary(t);
        }
    }

    public static void caught(ThrowingRunnable<?> runnable, Supplier<String> message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            LOG.error(message.get(), t);
            Threads.interruptIfNecessary(t);
        }
    }

    public static <R> R caught(ThrowingSupplier<R, ?> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
            Threads.interruptIfNecessary(t);
            return null;
        }
    }

    public static <E> void caught(ThrowingConsumer<E, ?> consumer, E arg) {
        try {
            consumer.accept(arg);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
            Threads.interruptIfNecessary(t);
        }
    }

    public static <E, R> R caught(ThrowingFunction<E, R, ?> function, E arg) {
        return caught(function, arg, null);
    }

    public static <E, R> R caught(ThrowingFunction<E, R, ?> function, E arg, R defaultValue) {
        try {
            return function.apply(arg);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
            Threads.interruptIfNecessary(t);
            return defaultValue;
        }
    }

    // -------------------------------------------------------------------------------checked

    public static void checked(ThrowingRunnable<?> runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            ExceptionUtils.rethrow(t);
        }
    }

    public static <R> R checked(ThrowingSupplier<R, ?> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return ExceptionUtils.rethrow(t);
        }
    }

    public static <E> void checked(ThrowingConsumer<E, ?> consumer, E arg) {
        try {
            consumer.accept(arg);
        } catch (Throwable t) {
            ExceptionUtils.rethrow(t);
        }
    }

    public static <E, R> R checked(ThrowingFunction<E, R, ?> function, E arg) {
        return caught(function, arg, null);
    }

    public static <E, R> R checked(ThrowingFunction<E, R, ?> function, E arg, R defaultValue) {
        try {
            return function.apply(arg);
        } catch (Throwable t) {
            return ExceptionUtils.rethrow(t);
        }
    }

    // -------------------------------------------------------------------------------ignored

    public static void ignored(ThrowingRunnable<?> runnable) {
        try {
            runnable.run();
        } catch (Throwable ignored) {
        }
    }

    public static <R> R ignored(ThrowingSupplier<R, ?> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------interface definitions

    @FunctionalInterface
    public interface ThrowingRunnable<T extends Throwable> {
        void run() throws T;

        static <T extends Throwable> Runnable checked(ThrowingRunnable<T> runnable) {
            return () -> {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    ExceptionUtils.rethrow(t);
                }
            };
        }
    }

    /**
     * Lambda function checked exception
     *
     * @param <R> the result type of method {@code call}
     * @param <T> the type of the call "call" method possible occur exception
     */
    @FunctionalInterface
    public interface ThrowingCallable<R, T extends Throwable> {
        R call() throws T;

        static <R, T extends Throwable> Callable<R> checked(ThrowingCallable<R, T> callable) {
            return () -> {
                try {
                    return callable.call();
                } catch (Throwable t) {
                    return ExceptionUtils.rethrow(t);
                }
            };
        }
    }

    /**
     * Lambda function checked exception
     *
     * @param <E> the type of the input to the operation
     * @param <T> the type of the call accept method possible occur exception
     */
    @FunctionalInterface
    public interface ThrowingConsumer<E, T extends Throwable> {
        void accept(E e) throws T;

        static <E, T extends Throwable> Consumer<E> checked(ThrowingConsumer<E, T> consumer) {
            return e -> {
                try {
                    consumer.accept(e);
                } catch (Throwable t) {
                    ExceptionUtils.rethrow(t);
                }
            };
        }
    }

    /**
     * Lambda function checked exception
     *
     * @param <E> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param <T> the type of the call apply method possible occur exception
     */
    @FunctionalInterface
    public interface ThrowingFunction<E, R, T extends Throwable> {
        R apply(E e) throws T;

        static <E, R, T extends Throwable> Function<E, R> checked(ThrowingFunction<E, R, T> function) {
            return e -> {
                try {
                    return function.apply(e);
                } catch (Throwable t) {
                    return ExceptionUtils.rethrow(t);
                }
            };
        }
    }

    /**
     * Lambda function checked exception
     *
     * @param <R> the type of results supplied by this supplier
     * @param <T> the type of the call get method possible occur exception
     */
    @FunctionalInterface
    public interface ThrowingSupplier<R, T extends Throwable> {
        R get() throws T;

        static <R, T extends Throwable> Supplier<R> checked(ThrowingSupplier<R, T> supplier) {
            return () -> {
                try {
                    return supplier.get();
                } catch (Throwable t) {
                    return ExceptionUtils.rethrow(t);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingComparator<E, T extends Throwable> {
        int compare(E e1, E e2) throws T;

        static <E, T extends Throwable> Comparator<? super E> checked(ThrowingComparator<E, T> comparator) {
            return (e1, e2) -> {
                try {
                    return comparator.compare(e1, e2);
                } catch (Throwable t) {
                    return ExceptionUtils.rethrow(t);
                }
            };
        }
    }

}