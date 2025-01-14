/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.common.collect;

import cn.ponfee.disjob.common.util.Numbers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Collection utilities
 *
 * @author Ponfee
 */
public class Collects {

    public static <E> LinkedList<E> newLinkedList(E element) {
        LinkedList<E> list = new LinkedList<>();
        list.add(element);
        return list;
    }

    public static <T> List<T> duplicate(List<T> list) {
        return duplicate(list, Function.identity());
    }

    /**
     * Returns the duplicates elements for list
     *
     * @param list the list
     * @return a set of duplicates elements for list
     */
    public static <T, R> List<R> duplicate(List<T> list, Function<T, R> mapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        return list.stream()
            .map(mapper)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Gets the first element from collection
     *
     * @param coll the coll
     * @param <T>  the coll element type
     * @return first element of coll
     */
    public static <T> T getFirst(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) {
            return null;
        }
        if (coll instanceof Deque) {
            return ((Deque<T>) coll).getFirst();
        }
        if (coll instanceof SortedSet) {
            return ((SortedSet<T>) coll).first();
        }
        if (coll instanceof List) {
            return ((List<T>) coll).get(0);
        }
        return coll.iterator().next();
    }

    /**
     * Gets the last element from collection
     *
     * @param coll the coll
     * @param <T>  the coll element type
     * @return last element of coll
     */
    public static <T> T getLast(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) {
            return null;
        }
        if (coll instanceof Deque) {
            return ((Deque<T>) coll).getLast();
        }
        if (coll instanceof SortedSet) {
            return ((SortedSet<T>) coll).last();
        }
        if (coll instanceof List) {
            return ((List<T>) coll).get(coll.size() - 1);
        }
        return coll.stream().reduce((a, b) -> b).orElse(null);
    }

    public static <T> T get(T[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return null;
        }
        return array[index];
    }

    public static <T> T get(List<T> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    public static <T> void batchProcess(List<T> list, Consumer<List<T>> processor, int batchSize) {
        Assert.notEmpty(list, "Process records cannot be empty.");
        if (list.size() <= batchSize) {
            processor.accept(list);
        } else {
            Lists.partition(list, batchSize).forEach(processor);
        }
    }

    /**
     * Returns consecutive sub array of an array,
     * each of the same size (the final list may be smaller).
     *
     * <pre>
     *  Collects.partition(new int[]{1,1,2,5,3}, 1)    ->  [1, 1, 2, 5, 3]
     *  Collects.partition(new int[]{1,1,2,5,3}, 3)    ->  [1, 1]; [2, 5]; [3]
     *  Collects.partition(new int[]{1,1,2,5,3}, 5)    ->  [1]; [1]; [2]; [5]; [3]
     *  Collects.partition(new int[]{1,1,2,5,3}, 6)    ->  [1]; [1]; [2]; [5]; [3]
     *  Collects.partition(new int[]{1,1,2,5,3}, 100)  ->  [1]; [1]; [2]; [5]; [3]
     * </pre>
     *
     * @param array the array
     * @param size  the size
     * @return a list of consecutive sub sets
     */
    public static List<int[]> partition(int[] array, int size) {
        Assert.isTrue(size > 0, "Size must be greater than 0.");
        if (array == null || array.length == 0) {
            return null;
        }
        size = Math.min(size, array.length);
        if (size == 1) {
            return Collections.singletonList(array);
        }

        List<int[]> result = new ArrayList<>(size);
        int pos = 0;
        for (int number : Numbers.slice(array.length, size)) {
            if (number == 0) {
                break;
            }
            result.add(Arrays.copyOfRange(array, pos, pos = pos + number));
        }
        return result;
    }

    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (list == null) {
            return null;
        }
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    public static <S, T> List<T> convert(List<S> source, Function<S, T> mapper) {
        if (source == null) {
            return null;
        }
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(source.size());
        source.stream().map(mapper).forEach(builder::add);
        return builder.build();
    }

    @SafeVarargs
    public static <T> List<T> concat(List<T> list, T... array) {
        if (list == null) {
            return array == null ? Collections.emptyList() : Arrays.asList(array);
        }
        if (array == null || array.length == 0) {
            return list;
        }
        List<T> result = new ArrayList<>(list.size() + array.length);
        result.addAll(list);
        Collections.addAll(result, array);
        return result;
    }

    public static <T> T[] newArray(Class<? extends T[]> newType, int length) {
        return newType.equals(Object[].class)
            ? (T[]) new Object[length]
            : (T[]) Array.newInstance(newType.getComponentType(), length);
    }

}
