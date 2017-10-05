package org.jboss.remoting3.test;

import org.jboss.remoting3._private.IntIndexHashMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class IntIndexHashMapTestCase {

    private static final int THREADS = 40;
    private static final int ITERATIONS = 1000;
    public static final int MAP_RANGE = THREADS * ITERATIONS * 2;

    @Test
    public void testIntIndexHashMap() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
        try {
            IntIndexHashMap<ValueType> map = new IntIndexHashMap<>(ValueType::getKey, 1);
            CountDownLatch[] latches = new CountDownLatch[THREADS];
            for (int i = 0; i < THREADS; ++i) {
                latches[i] = new CountDownLatch(1);
            }
            List<Future<String>> ret = new ArrayList<>();
            for (int i = 0; i < THREADS; ++i) {
                final int c = i;
                Future<String> f = executorService.submit(new Callable<String>() {
                    @Override
                    public String call() {
                        try {
                            Map<Integer, Integer> localValues = new HashMap<>();
                            Random random = new Random();
                            for (int i = 0; i < ITERATIONS; ++i) {

                                for (; ; ) {
                                    int key = random.nextInt(MAP_RANGE);
                                    int val = random.nextInt();
                                    ValueType entry = new ValueType(key, val);
                                    if (map.putIfAbsent(entry) == null) {
                                        localValues.put(entry.getKey(), entry.getValue());
                                        ValueType valueType = map.get(entry.getKey());
                                        Assert.assertNotNull(valueType);
                                        Assert.assertEquals(valueType.getValue(), entry.getValue());
                                        break;
                                    }
                                }
                            }
                            latches[c].countDown();
                            for (int i = 0; i < THREADS; ++i) {
                                try {
                                    latches[i].await();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            int count = 0;
                            for (Map.Entry<Integer, Integer> e : localValues.entrySet()) {
                                count++;
                                ValueType valueType = map.removeKey(e.getKey());
                                Assert.assertNotNull(valueType);
                                Assert.assertEquals((int) e.getValue(), valueType.getValue());
                            }
                            return "passed";
                        } finally {

                            latches[c].countDown();
                        }
                    }
                });
                ret.add(f);
            }

            for (int i = 0; i < THREADS; ++i) {
                try {
                    latches[i].await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            for (Future<String> i : ret) {
                Assert.assertEquals("passed", i.get());
            }
        } finally {
            executorService.shutdownNow();
        }

    }

    static final class ValueType {
        final int key, value;

        private ValueType(int key, int value) {
            this.key = key;
            this.value = value;
        }

        public int getKey() {
            return key;
        }

        public int getValue() {
            return value;
        }
    }
}
