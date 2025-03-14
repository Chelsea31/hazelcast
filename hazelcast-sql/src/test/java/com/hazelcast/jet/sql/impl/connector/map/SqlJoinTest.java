/*
 * Copyright 2021 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.connector.map;

import com.hazelcast.jet.sql.SqlTestSupport;
import com.hazelcast.jet.sql.impl.connector.map.model.Person;
import com.hazelcast.jet.sql.impl.connector.map.model.PersonId;
import com.hazelcast.jet.sql.impl.connector.test.TestBatchSqlConnector;
import com.hazelcast.sql.SqlService;
import com.hazelcast.sql.impl.QueryException;
import com.hazelcast.test.HazelcastParametrizedRunner;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static com.hazelcast.sql.impl.type.QueryDataTypeFamily.INTEGER;
import static com.hazelcast.sql.impl.type.QueryDataTypeFamily.VARCHAR;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Enclosed.class)
public class SqlJoinTest {

    public static class SqlInnerJoinTest extends SqlTestSupport {

        private static SqlService sqlService;

        @BeforeClass
        public static void setUpClass() {
            initialize(2, null);
            sqlService = instance().getSql();
        }

        @Test
        public void test_innerJoin() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "INNER JOIN " + mapName + " m ON l.v = m.__key",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinUsing() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    singletonList("__key"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{"1"}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.__key, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m USING (__key)",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinConditionInWhereClause() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l, " + mapName + " m " +
                            "WHERE l.v = m.__key",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinAndConditionInWhereClause() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            // TODO assert that it uses the join-primitive plan
            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "INNER JOIN " + mapName + " m ON 1 = 1 " +
                            "WHERE l.v = m.__key",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinWithoutCondition() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 2);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l, " + mapName + " m ",
                    asList(
                            new Row(0, "value-1"),
                            new Row(0, "value-2"),
                            new Row(1, "value-1"),
                            new Row(1, "value-2")
                    )
            );
        }

        @Test
        public void test_crossJoin() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 2);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "CROSS JOIN " + mapName + " m ",
                    asList(
                            new Row(0, "value-1"),
                            new Row(0, "value-2"),
                            new Row(1, "value-1"),
                            new Row(1, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinNull() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.__key",
                    singletonList(new Row(2, "value-2"))
            );
        }

        @Test
        public void test_innerJoinFilter() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.__key " +
                            "WHERE m.__key < 2",
                    singletonList(new Row(1, "value-1"))
            );
        }

        @Test
        public void test_innerJoinProject() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this || '-s' " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.__key ",
                    asList(
                            new Row(1, "value-1-s"),
                            new Row(2, "value-2-s")
                    )
            );
        }

        @Test
        public void test_innerJoinDynamicParameters() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this || ?" +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.__key " +
                            "WHERE m.__key < ?",
                    asList("-s", 2),
                    singletonList(new Row(1, "value-1-s"))
            );
        }

        @Test
        public void test_innerJoinConditionProject() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.__key, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = 2 * m.__key",
                    singletonList(new Row(2, 1, "value-1"))
            );
        }

        @Test
        public void test_innerJoinOnValue() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, String.class, int.class);
            instance().getMap(mapName).put("value-1", 1);
            instance().getMap(mapName).put("value-2", 2);
            instance().getMap(mapName).put("value-3", 3);

            assertRowsAnyOrder(
                    "SELECT l.v, m.__key " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.this",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinNonEqui() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 4);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.__key, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v > m.__key",
                    asList(
                            new Row(2, 1, "value-1"),
                            new Row(3, 1, "value-1"),
                            new Row(3, 2, "value-2")
                    )
            );
        }

        @Test
        public void test_joinEquiJoinAndDisjunction() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 4);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            // this currently uses the full-scan join
            assertRowsAnyOrder(
                    "SELECT l.v, m.__key, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.__key OR l.v = m.__key",
                    asList(
                            new Row(1, 1, "value-1"),
                            new Row(2, 2, "value-2"),
                            new Row(3, 3, "value-3")
                    )
            );
        }

        @Test
        public void test_innerJoinEquiAndNonEqui() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    asList("v1", "v2"),
                    asList(INTEGER, INTEGER),
                    asList(new String[]{"0", "0"}, new String[]{"1", "0"}, new String[]{"2", "2"})
            );

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v1, l.v2, m.__key, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v1 = m.__key AND l.v2 != m.__key",
                    singletonList(new Row(1, 0, 1, "value-1"))
            );
        }

        @Test
        public void test_innerJoinMulti() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName1 = randomName();
            createMapping(mapName1, int.class, String.class);
            instance().getMap(mapName1).put(1, "value-1.1");
            instance().getMap(mapName1).put(2, "value-1.2");
            instance().getMap(mapName1).put(3, "value-1.3");

            String mapName2 = randomName();
            createMapping(mapName2, int.class, String.class);
            instance().getMap(mapName2).put(1, "value-2.1");
            instance().getMap(mapName2).put(2, "value-2.2");
            instance().getMap(mapName2).put(3, "value-2.3");

            assertRowsAnyOrder(
                    "SELECT l.v, m1.this, m2.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName1 + " m1 ON l.v = m1.__key " +
                            "JOIN " + mapName2 + " m2 ON l.v + m1.__key > m2.__key",
                    asList(
                            new Row(1, "value-1.1", "value-2.1"),
                            new Row(2, "value-1.2", "value-2.1"),
                            new Row(2, "value-1.2", "value-2.2"),
                            new Row(2, "value-1.2", "value-2.3")
                    )
            );
        }

        @Test
        public void test_innerJoinPartOfTheCompositeKey() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, Person.class, PersonId.class);
            instance().getMap(mapName).put(new Person(null, "value-1"), new PersonId());
            instance().getMap(mapName).put(new Person(2, "value-2"), new PersonId());
            instance().getMap(mapName).put(new Person(3, "value-3"), new PersonId());

            assertRowsEventuallyInAnyOrder(
                    "SELECT l.v, m.name, m.id " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.id",
                    singletonList(new Row(2, "value-2", 2))
            );
        }

        @Test
        public void test_innerJoinFullCompositeKeyConjunction() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    asList("v1", "v2"),
                    asList(INTEGER, VARCHAR),
                    asList(new String[]{"0", "value-0"}, new String[]{"1", null}, new String[]{"2", "value-2"})
            );

            String mapName = randomName();
            createMapping(mapName, Person.class, PersonId.class);
            instance().getMap(mapName).put(new Person(1, null), new PersonId());
            instance().getMap(mapName).put(new Person(2, "value-2"), new PersonId());
            instance().getMap(mapName).put(new Person(3, "value-3"), new PersonId());

            assertRowsEventuallyInAnyOrder(
                    "SELECT l.v1, l.v2, m.id, m.name " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v1 = m.id AND l.v2 = m.name",
                    singletonList(new Row(2, "value-2", 2, "value-2"))
            );
        }

        @Test
        public void test_innerJoinFullCompositeKeyDisjunction() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    asList("v1", "v2"),
                    asList(INTEGER, VARCHAR),
                    asList(new String[]{"0", "value-0"}, new String[]{"1", null}, new String[]{"2", "value-2"})
            );

            String mapName = randomName();
            createMapping(mapName, Person.class, PersonId.class);
            instance().getMap(mapName).put(new Person(1, "value-1"), new PersonId());
            instance().getMap(mapName).put(new Person(2, "value-2"), new PersonId());
            instance().getMap(mapName).put(new Person(3, "value-3"), new PersonId());

            assertRowsEventuallyInAnyOrder(
                    "SELECT l.v1, l.v2, m.id, m.name " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v1 = m.id OR l.v2 = m.name",
                    asList(
                            new Row(1, null, 1, "value-1"),
                            new Row(2, "value-2", 2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinPartOfTheCompositeValue() {
            String leftName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    leftName,
                    singletonList("v"),
                    singletonList(VARCHAR),
                    asList(new String[]{"value-0"}, new String[]{"value-1"}, new String[]{"value-2"})
            );

            String mapName = randomName();
            createMapping(mapName, PersonId.class, Person.class);
            instance().getMap(mapName).put(new PersonId(1), new Person(0, "value-1"));
            instance().getMap(mapName).put(new PersonId(2), new Person(0, "value-2"));
            instance().getMap(mapName).put(new PersonId(3), new Person(0, "value-3"));

            assertRowsEventuallyInAnyOrder(
                    "SELECT l.v, m.id " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.name",
                    asList(
                            new Row("value-1", 1),
                            new Row("value-2", 2)
                    )
            );
        }

        @Test
        public void test_innerJoinKeyAndValue() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, int.class, Person.class);
            instance().getMap(mapName).put(1, new Person(0, "value-1"));
            instance().getMap(mapName).put(2, new Person(2, "value-2"));
            instance().getMap(mapName).put(3, new Person(0, "value-3"));

            assertRowsEventuallyInAnyOrder(
                    "SELECT l.v, m.id, m.name " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON l.v = m.__key AND l.v = m.id",
                    singletonList(new Row(2, 2, "value-2"))
            );
        }

        @Test
        public void test_innerJoinWithAlwaysFalseCondition() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 4);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.__key, m.this " +
                            "FROM " + leftName + " l " +
                            "JOIN " + mapName + " m ON 1 = 2",
                    emptyList()
            );
        }

        @Test
        public void test_innerJoinWithTypeConversion_smallerLeft() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, short.class, String.class);
            instance().getMap(mapName).put((short) 1, "value-1");
            instance().getMap(mapName).put((short) 2, "value-2");
            instance().getMap(mapName).put((short) 3, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "INNER JOIN " + mapName + " m ON l.v = m.__key",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_innerJoinWithTypeConversion_smallerRight() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 3);

            String mapName = randomName();
            createMapping(mapName, long.class, String.class);
            instance().getMap(mapName).put(1L, "value-1");
            instance().getMap(mapName).put(2L, "value-2");
            instance().getMap(mapName).put(3L, "value-3");

            assertRowsAnyOrder(
                    "SELECT l.v, m.this " +
                            "FROM " + leftName + " l " +
                            "INNER JOIN " + mapName + " m ON l.v = m.__key",
                    asList(
                            new Row(1, "value-1"),
                            new Row(2, "value-2")
                    )
            );
        }

        @Test
        public void test_joinSubquery() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 1);

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");

            assertThatThrownBy(() ->
                    sqlService.execute(
                            "SELECT 1 " +
                                    "FROM " + leftName + " AS l " +
                                    "JOIN (SELECT * FROM " + mapName + ") AS m ON l.v = m.__key"
                    ))
                    .hasCauseInstanceOf(QueryException.class)
                    .hasMessageContaining("Sub-query not supported on the right side of a (LEFT) JOIN or the left side of a RIGHT JOIN");
        }

        @Test
        public void test_joinValues() {
            String leftName = randomName();
            TestBatchSqlConnector.create(sqlService, leftName, 1);

            assertThatThrownBy(() ->
                    sqlService.execute(
                            "SELECT * FROM " + leftName + " l JOIN (VALUES (1)) AS r (__key) ON l.v = r.__key"
                    ))
                    .hasCauseInstanceOf(QueryException.class)
                    .hasMessageContaining("VALUES clause not supported on the right side of a (LEFT) JOIN or the left side of a RIGHT JOIN");
        }
    }

    @RunWith(HazelcastParametrizedRunner.class)
    public static class SqlAsymmetricOuterJoinTest extends SqlTestSupport {

        private static SqlService sqlService;

        @BeforeClass
        public static void setUpClass() {
            initialize(2, null);
            sqlService = instance().getSql();
        }

        @Parameters(name = "{0} JOIN")
        public static Object[] params() {
            return new Object[] { OuterJoinType.LEFT, OuterJoinType.RIGHT };
        }

        @Parameter
        public OuterJoinType joinType;

        public enum OuterJoinType {
            LEFT,
            RIGHT
        }

        @Test
        public void test_join() {
            String batchName = randomName() + "_batch";
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName() + "_map";
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this || '-s' FROM "
                            + joinClause(batchName, mapName)
                            + " ON t.v = m.__key",
                    asList(
                            new Row(0, null, null),
                            new Row(null, null, null),
                            new Row(2, 2, "value-2-s")
                    )
            );
        }

        @Test
        public void test_joinOnPrimitiveKey() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " + joinClause(batchName, mapName) +
                            " ON t.v = m.__key",
                    asList(
                            new Row(0, null, null),
                            new Row(null, null, null),
                            new Row(2, 2, "value-2")
                    )
            );
        }

        @Test
        public void test_joinNotOnPrimitiveKey() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, String.class, int.class);
            instance().getMap(mapName).put("value-1", 1);
            instance().getMap(mapName).put("value-2", 2);
            instance().getMap(mapName).put("value-3", 3);

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " + joinClause(batchName, mapName) +
                            " ON t.v = m.this",
                    asList(
                            new Row(0, null, null),
                            new Row(null, null, null),
                            new Row(2, "value-2", 2)
                    )
            );
        }

        @Test
        public void test_joinNotOnPrimitiveKey_withAdditionalCondition() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, String.class, int.class);
            instance().getMap(mapName).put("value-1", 1);
            instance().getMap(mapName).put("value-2", 2);
            instance().getMap(mapName).put("value-3", 3);

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " + joinClause(batchName, mapName) +
                            " ON t.v = m.this and m.__key is null",
                    asList(
                            new Row(0, null, null),
                            new Row(null, null, null),
                            new Row(2, null, null)
                    )
            );
        }

        @Test
        public void test_joinNotOnPrimitiveKey_multipleMatches() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, String.class, int.class);
            instance().getMap(mapName).put("value-1", 1);
            instance().getMap(mapName).put("value-2", 2);
            instance().getMap(mapName).put("value-3", 2);

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " + joinClause(batchName, mapName) +
                            " ON t.v = m.this",
                    asList(
                            new Row(0, null, null),
                            new Row(null, null, null),
                            new Row(2, "value-2", 2),
                            new Row(2, "value-3", 2)
                    )
            );
        }

        @Test
        public void test_joinNotOnPrimitiveKey_multipleMatches_additionalCondition() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"})
            );

            String mapName = randomName();
            createMapping(mapName, String.class, int.class);
            instance().getMap(mapName).put("value-1", 1);
            instance().getMap(mapName).put("value-2", 2);
            instance().getMap(mapName).put("value-3", 2);

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " + joinClause(batchName, mapName) +
                            " ON t.v = m.this and m.__key='value-3'",
                    asList(
                            new Row(0, null, null),
                            new Row(null, null, null),
                            new Row(2, "value-3", 2)
                    )
            );
        }

        @Test
        public void test_joinWithAlwaysTrueCondition() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null})
            );

            String mapName = randomName();
            createMapping(mapName, String.class, int.class);
            instance().getMap(mapName).put("value-1", 1);
            instance().getMap(mapName).put("value-2", 2);
            instance().getMap(mapName).put("value-3", 3);

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " +  joinClause(batchName, mapName) + " ON 1 = 1",
                    asList(
                            new Row(0, "value-1", 1),
                            new Row(0, "value-2", 2),
                            new Row(0, "value-3", 3),
                            new Row(null, "value-1", 1),
                            new Row(null, "value-2", 2),
                            new Row(null, "value-3", 3)
                    )
            );
        }

        @Test
        public void test_joinWithNonEquiJoin() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"}, new String[]{"3"})
            );

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this FROM "
                            + joinClause(batchName, mapName)
                            + " ON m.__key > t.v",
                    asList(
                            new Row(0, 1, "value-1"),
                            new Row(0, 2, "value-2"),
                            new Row(0, 3, "value-3"),
                            new Row(null, null, null),
                            new Row(2, 3, "value-3"),
                            new Row(3, null, null)
                    )
            );
        }

        @Test
        public void test_joinWithNonEquiJoin_additionalCondition() {
            String batchName = randomName();
            TestBatchSqlConnector.create(
                    sqlService,
                    batchName,
                    singletonList("v"),
                    singletonList(INTEGER),
                    asList(new String[]{"0"}, new String[]{null}, new String[]{"2"}, new String[]{"3"})
            );

            String mapName = randomName();
            createMapping(mapName, int.class, String.class);
            instance().getMap(mapName).put(1, "value-1");
            instance().getMap(mapName).put(2, "value-2");
            instance().getMap(mapName).put(3, "value-3");

            assertRowsAnyOrder(
                    "SELECT t.v, m.__key, m.this " +
                            "FROM " +  joinClause(batchName, mapName)
                            + " ON m.__key > t.v AND m.this IS NOT NULL",
                    asList(
                            new Row(0, 1, "value-1"),
                            new Row(0, 2, "value-2"),
                            new Row(0, 3, "value-3"),
                            new Row(null, null, null),
                            new Row(2, 3, "value-3"),
                            new Row(3, null, null)

                    )
            );
        }

        @Test
        public void test_whenOuterJoinWrongSide_thenExceptionThrown() {
            String batchName = randomName();
            TestBatchSqlConnector.create(sqlService, batchName, 0);

            assertThatThrownBy(() -> sqlService.execute(
                    "SELECT * FROM " + joinClause(batchName, "TABLE(GENERATE_STREAM(1))") + " ON true"))
                    .hasCauseInstanceOf(QueryException.class)
                    .hasMessageContaining("The right side of a LEFT JOIN or the left side of a RIGHT JOIN cannot be a streaming source");

            assertThatThrownBy(() -> sqlService.execute(
                    "SELECT * FROM " + joinClause(batchName, "(SELECT * FROM " + batchName + ")") + " ON true"))
                    .hasCauseInstanceOf(QueryException.class)
                    .hasMessageContaining("Sub-query not supported on the right side of a (LEFT) JOIN or the left side of a RIGHT JOIN");

            assertThatThrownBy(() -> sqlService.execute(
                    "SELECT * FROM " + joinClause(batchName, "(VALUES(1,2))") + " ON true"))
                    .hasCauseInstanceOf(QueryException.class)
                    .hasMessageContaining("VALUES clause not supported on the right side of a (LEFT) JOIN or the left side of a RIGHT JOIN");
        }

        private String joinClause(
                String batchSourceName,
                String mapSourceName
        ) {
            String leftJoinOperand = joinType == OuterJoinType.LEFT ? batchSourceName : mapSourceName;
            String rightJoinOperand = joinType == OuterJoinType.LEFT ? mapSourceName : batchSourceName;
            String leftAlias = joinType == OuterJoinType.LEFT ? "t" : "m";
            String rightAlias = joinType == OuterJoinType.LEFT ? "m" : "t";
            return String.format(
                    "%s %s %s JOIN %s %s",
                    leftJoinOperand,
                    leftAlias,
                    joinType.toString(),
                    rightJoinOperand,
                    rightAlias
            );
        }
    }
}
