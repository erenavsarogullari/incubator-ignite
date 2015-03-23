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

package org.apache.ignite.examples.datagrid.store.auto;

import org.apache.ignite.cache.*;
import org.apache.ignite.cache.store.*;
import org.apache.ignite.cache.store.jdbc.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.examples.datagrid.store.*;
import org.apache.ignite.internal.util.typedef.*;
import org.h2.jdbcx.*;

import javax.cache.configuration.*;
import java.sql.*;

import static org.apache.ignite.cache.CacheAtomicityMode.*;

/**
 * Predefined configuration for examples with {@link CacheJdbcPojoStore}.
 */
public class CacheConfig {
    /**
     * Configure cache with store.
     */
    public static CacheConfiguration<Long, Person> jdbcPojoStoreCache() {
        CacheConfiguration<Long, Person> cfg = new CacheConfiguration<>();

        // Set atomicity as transaction, since we are showing transactions in example.
        cfg.setAtomicityMode(TRANSACTIONAL);

        cfg.setCacheStoreFactory(new Factory<CacheStore<? super Long, ? super Person>>() {
            @Override public CacheStore<? super Long, ? super Person> create() {
                CacheJdbcPojoStore<Long, Person> store = new CacheJdbcPojoStore<>();

                store.setDataSource(JdbcConnectionPool.create("jdbc:h2:tcp://localhost/mem:ExampleDb", "sa", ""));

                return store;
            }
        });

        CacheTypeMetadata tm = new CacheTypeMetadata();

        tm.setDatabaseTable("PERSON");

        tm.setKeyType("java.lang.Long");
        tm.setValueType("org.apache.ignite.examples.datagrid.store.Person");

        tm.setKeyFields(F.asList(new CacheTypeFieldMetadata("ID", Types.BIGINT, "id", Long.class)));

        tm.setValueFields(F.asList(
            new CacheTypeFieldMetadata("ID", Types.BIGINT, "id", long.class),
            new CacheTypeFieldMetadata("FIRST_NAME", Types.VARCHAR, "firstName", String.class),
            new CacheTypeFieldMetadata("LAST_NAME", Types.VARCHAR, "lastName", String.class)
        ));

        cfg.setTypeMetadata(F.asList(tm));

        cfg.setWriteBehindEnabled(true);

        cfg.setReadThrough(true);
        cfg.setWriteThrough(true);

        return cfg;
    }
}