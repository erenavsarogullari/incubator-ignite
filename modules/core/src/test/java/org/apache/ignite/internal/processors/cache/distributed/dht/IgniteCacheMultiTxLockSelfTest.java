/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.distributed.dht;

import org.apache.ignite.cache.*;
import org.apache.ignite.cache.eviction.lru.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.internal.processors.cache.transactions.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.junits.common.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Tests explicit lock.
 */
public class IgniteCacheMultiTxLockSelfTest extends GridCommonAbstractTest {
    /** */
    public static final String CACHE_NAME = "part_cache";

    /** IP finder. */
    private static final TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    private volatile boolean run = true;

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();

        assertEquals(0, G.allGrids().size());
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(ipFinder);

        c.setDiscoverySpi(disco);

        CacheConfiguration ccfg = new CacheConfiguration();

        ccfg.setName(CACHE_NAME);
        ccfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        ccfg.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        ccfg.setBackups(2);
        ccfg.setCacheMode(CacheMode.PARTITIONED);
        ccfg.setStartSize(100000);

        LruEvictionPolicy plc = new LruEvictionPolicy();
        plc.setMaxSize(100000);

        ccfg.setEvictionPolicy(plc);
        ccfg.setEvictSynchronized(true);

        c.setCacheConfiguration(ccfg);

        return c;
    }

    /**
     * @throws Exception If failed.
     */
    public void testExplicitLockOneKey() throws Exception {
        checkExplicitLock(1);
    }

    /**
     * @throws Exception If failed.
     */
    public void testExplicitLockManyKeys() throws Exception {
        checkExplicitLock(4);
    }

    /**
     * @throws Exception If failed.
     */
    public void checkExplicitLock(int keys) throws Exception {
        Collection<Thread> threads = new ArrayList<>();

        try {
            // Start grid 1.
            IgniteEx grid1 = startGrid(1);

            threads.add(runCacheOperations(grid1.cachex(CACHE_NAME), keys));

            TimeUnit.SECONDS.sleep(3L);

            // Start grid 2.
            IgniteEx grid2 = startGrid(2);

            threads.add(runCacheOperations(grid2.cachex(CACHE_NAME), keys));

            TimeUnit.SECONDS.sleep(3L);

            // Start grid 3.
            IgniteEx grid3 = startGrid(3);

            threads.add(runCacheOperations(grid3.cachex(CACHE_NAME), keys));

            TimeUnit.SECONDS.sleep(3L);

            // Start grid 4.
            IgniteEx grid4 = startGrid(4);

            threads.add(runCacheOperations(grid4.cachex(CACHE_NAME), keys));

            TimeUnit.SECONDS.sleep(3L);

            stopThreads(threads);

            for (int i = 1; i <= 4; i++) {
                IgniteTxManager tm = ((IgniteKernal)grid(i)).internalCache(CACHE_NAME).context().tm();

                assertEquals("txMap is not empty:" + i, 0, tm.idMapSize());
            }
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @param threads Thread which will be stopped.
     */
    private void stopThreads(Iterable<Thread> threads) {
        try {
            run = false;

            for (Thread thread : threads)
                thread.join();
        }
        catch (Exception e) {
            U.error(log(), "Couldn't stop threads.", e);
        }
    }

    /**
     * @param cache Cache.
     * @return Running thread.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    private Thread runCacheOperations(final IgniteInternalCache<Object,Object> cache, final int keys) {
        Thread t = new Thread() {
            @Override public void run() {
                while (run) {
                    TreeMap<Integer, String> vals = generateValues(keys);

                    try {
                        // Explicit lock.
                        cache.lock(vals.firstKey(), 0);

                        try {
                            // Put or remove.
                            if (ThreadLocalRandom.current().nextDouble(1) < 0.65)
                                cache.putAll(vals);
                            else
                                cache.removeAll(vals.keySet());
                        }
                        catch (Exception e) {
                            U.error(log(), "Failed cache operation.", e);
                        }
                        finally {
                            cache.unlock(vals.firstKey());
                        }

                        U.sleep(100);
                    }
                    catch (Exception e){
                        U.error(log(), "Failed unlock.", e);
                    }
                }
            }
        };

        t.start();

        return t;
    }

    /**
     * @param cnt Number of keys to generate.
     * @return Map.
     */
    private TreeMap<Integer, String> generateValues(int cnt) {
        TreeMap<Integer, String> res = new TreeMap<>();

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        while (res.size() < cnt) {
            int key = rnd.nextInt(0, 100);

            res.put(key, String.valueOf(key));
        }

        return res;
    }
}
