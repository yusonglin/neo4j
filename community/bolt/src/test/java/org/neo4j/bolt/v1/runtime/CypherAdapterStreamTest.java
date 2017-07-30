/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.v1.runtime;

import org.junit.Test;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.bolt.v1.runtime.spi.BoltResult;
import org.neo4j.graphdb.ExecutionPlanDescription;
import org.neo4j.graphdb.InputPosition;
import org.neo4j.graphdb.QueryStatistics;
import org.neo4j.graphdb.impl.notification.NotificationCode;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.impl.query.TransactionalContext;
import org.neo4j.values.result.QueryResult;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.neo4j.graphdb.QueryExecutionType.QueryType.READ_ONLY;
import static org.neo4j.graphdb.QueryExecutionType.QueryType.READ_WRITE;
import static org.neo4j.graphdb.QueryExecutionType.explained;
import static org.neo4j.graphdb.QueryExecutionType.query;
import static org.neo4j.helpers.collection.MapUtil.map;

public class CypherAdapterStreamTest
{
    @Test
    public void shouldIncludeBasicMetadata() throws Throwable
    {
        // Given
        QueryStatistics queryStatistics = mock( QueryStatistics.class );
        when( queryStatistics.containsUpdates() ).thenReturn( true );
        when( queryStatistics.getNodesCreated() ).thenReturn( 1 );
        when( queryStatistics.getNodesDeleted() ).thenReturn( 2 );
        when( queryStatistics.getRelationshipsCreated() ).thenReturn( 3 );
        when( queryStatistics.getRelationshipsDeleted() ).thenReturn( 4 );
        when( queryStatistics.getPropertiesSet() ).thenReturn( 5 );
        when( queryStatistics.getIndexesAdded() ).thenReturn( 6 );
        when( queryStatistics.getIndexesRemoved() ).thenReturn( 7 );
        when( queryStatistics.getConstraintsAdded() ).thenReturn( 8 );
        when( queryStatistics.getConstraintsRemoved() ).thenReturn( 9 );
        when( queryStatistics.getLabelsAdded() ).thenReturn( 10 );
        when( queryStatistics.getLabelsRemoved() ).thenReturn( 11 );

        QueryResult result = mock( QueryResult.class );
        when(result.fieldNames()).thenReturn( new String[0] );
        when( result.executionType() ).thenReturn( query( READ_WRITE ) );
        when( result.queryStatistics() ).thenReturn( queryStatistics );
        when( result.getNotifications() ).thenReturn( Collections.emptyList() );

        Clock clock = mock( Clock.class );
        when( clock.millis() ).thenReturn( 0L, 1337L );

        TransactionalContext tc = mock( TransactionalContext.class );
        CypherAdapterStream stream = new CypherAdapterStream( result, clock );

        // When
        Map<String,Object> meta = metadataOf( stream );

        // Then
        assertThat( meta.get("type").toString(), equalTo( "rw") );
        assertThat( meta.get("stats"), equalTo( map(
                "nodes-created", 1,
                "nodes-deleted", 2,
                "relationships-created", 3,
                "relationships-deleted", 4,
                "properties-set", 5,
                "indexes-added", 6,
                "indexes-removed", 7,
                "constraints-added", 8,
                "constraints-removed", 9,
                "labels-added", 10,
                "labels-removed", 11
        ) ) );
        assertThat(meta.get("result_consumed_after"), equalTo(1337L));
    }

    @Test
    public void shouldIncludePlanIfPresent() throws Throwable
    {
        // Given
        QueryStatistics queryStatistics = mock( QueryStatistics.class );
        when( queryStatistics.containsUpdates() ).thenReturn( false );
        QueryResult result = mock( QueryResult.class );
        when(result.fieldNames()).thenReturn( new String[0] );
        when( result.executionType() ).thenReturn( explained( READ_ONLY ) );
        when( result.queryStatistics() ).thenReturn( queryStatistics );
        when( result.getNotifications() ).thenReturn( Collections.emptyList() );
        when( result.executionPlanDescription() ).thenReturn(
                plan("Join", map( "arg1", 1 ), singletonList( "id1" ),
                plan("Scan", map( "arg2", 1 ), singletonList("id2")) ) );

        TransactionalContext tc = mock( TransactionalContext.class );
        CypherAdapterStream stream = new CypherAdapterStream( result, Clock.systemUTC() );

        // When
        Map<String,Object> meta = metadataOf( stream );

        // Then
        Map<String,Object> expectedChild = map(
                "args", map( "arg2", 1 ),
                "identifiers", Iterators.asSet("id2"),
                "operatorType", "Scan",
                "children", Collections.EMPTY_LIST
        );
        Map<String,Object> expectedPlan = map(
                "args", map( "arg1", 1 ),
                "identifiers", Iterators.asSet("id1"),
                "operatorType", "Join",
                "children", Arrays.asList( expectedChild )
        );
        assertThat( meta.get( "plan" ), equalTo( expectedPlan ) );
    }

    @Test
    public void shouldIncludeProfileIfPresent() throws Throwable
    {
        // Given
        QueryStatistics queryStatistics = mock( QueryStatistics.class );
        when( queryStatistics.containsUpdates() ).thenReturn( false );
        QueryResult result = mock( QueryResult.class );
        when(result.fieldNames()).thenReturn( new String[0] );
        when( result.executionType() ).thenReturn( explained( READ_ONLY ) );
        when( result.queryStatistics() ).thenReturn( queryStatistics );
        when( result.getNotifications() ).thenReturn( Collections.emptyList() );
        when( result.executionPlanDescription() ).thenReturn(
                plan( "Join", map( "arg1", 1 ), 2, 4, 3, 1, singletonList( "id1" ),
                        plan( "Scan", map( "arg2", 1 ), 2, 4, 7, 1, singletonList( "id2" ) ) ) );

        TransactionalContext tc = mock( TransactionalContext.class );
        CypherAdapterStream stream = new CypherAdapterStream( result, Clock.systemUTC() );

        // When
        Map<String,Object> meta = metadataOf( stream );

        // Then
        Map<String,Object> expectedChild = map(
                "args", map( "arg2", 1 ),
                "identifiers", Iterators.asSet("id2"),
                "operatorType", "Scan",
                "children", Collections.EMPTY_LIST,
                "rows", 1L,
                "dbHits", 2L,
                "pageCacheHits", 4L,
                "pageCacheMisses", 7L,
                "pageCacheHitRatio", 4.0 / 11
        );

        Map<String,Object> expectedProfile = map(
                "args", map( "arg1", 1 ),
                "identifiers", Iterators.asSet("id1"),
                "operatorType", "Join",
                "children", Arrays.asList( expectedChild ),
                "rows", 1L,
                "dbHits", 2L,
                "pageCacheHits", 4L,
                "pageCacheMisses", 3L,
                "pageCacheHitRatio", 4.0 / 7
        );

        assertMapEqualsWithDelta( (Map<String,Object>)meta.get( "profile" ), expectedProfile, 0.0001 );
    }

    @Test
    public void shouldIncludeNotificationsIfPresent() throws Throwable
    {
        // Given
        QueryResult result = mock( QueryResult.class );
        when(result.fieldNames()).thenReturn( new String[0] );

        QueryStatistics queryStatistics = mock( QueryStatistics.class );
        when( queryStatistics.containsUpdates() ).thenReturn( false );

        when( result.queryStatistics() ).thenReturn( queryStatistics );
        when( result.executionType() ).thenReturn( query( READ_WRITE ) );

        when( result.getNotifications() ).thenReturn( Arrays.<Notification>asList(
                NotificationCode.INDEX_HINT_UNFULFILLABLE.notification( InputPosition.empty ),
                NotificationCode.PLANNER_UNSUPPORTED.notification( new InputPosition( 4, 5, 6 ) )
        ) );
        TransactionalContext tc = mock( TransactionalContext.class );
        CypherAdapterStream stream = new CypherAdapterStream( result, Clock.systemUTC() );

        // When
        Map<String,Object> meta = metadataOf( stream );

        // Then
        Map<String,Object> msg1 = map(
                "severity", "WARNING",
                "code", "Neo.ClientError.Schema.IndexNotFound",
                "title", "The request (directly or indirectly) referred to an index that does not exist.",
                "description", "The hinted index does not exist, please check the schema"
        );
        Map<String,Object> msg2 = map(
                "severity", "WARNING",
                "code", "Neo.ClientNotification.Statement.PlannerUnsupportedWarning",
                "title", "This query is not supported by the COST planner.",
                "description", "Using COST planner is unsupported for this query, please use RULE planner instead",
                "position", map( "offset", 4, "column", 6, "line", 5 )
        );

        assertThat( meta.get( "notifications" ), equalTo( Arrays.asList( msg1, msg2 ) ) );
    }

    private Map<String,Object> metadataOf( CypherAdapterStream stream ) throws Exception
    {
        final Map<String, Object> meta = new HashMap<>();
        stream.accept( new BoltResult.Visitor()
        {
            @Override
            public void visit( QueryResult.Record record ) throws Exception
            {

            }

            @Override
            public void addMetadata( String key, Object value )
            {
                meta.put( key, value );
            }
        } );
        return meta;
    }

    private static void assertMapEqualsWithDelta( Map<String,Object> a, Map<String,Object> b, double delta )
    {
        assertThat( "Map should have same size", a.size(), equalTo(b.size()) );
        for ( Map.Entry<String,Object> entry : a.entrySet() )
        {
            String key = entry.getKey();
            assertThat( "Missing key", b.get( key ) != null );
            Object aValue = entry.getValue();
            Object bValue = b.get( key );
            if ( aValue instanceof Map )
            {
                assertThat( "Value mismatch", bValue instanceof Map );
                assertMapEqualsWithDelta( (Map<String,Object>)aValue, (Map<String,Object>)bValue, delta);
            }
            else if ( aValue instanceof Double )
            {
                assertThat( "Value mismatch", (double)aValue, closeTo( (double)bValue, delta ) );
            }
            else
            {
                assertThat( "Value mismatch", aValue, equalTo( bValue ) );
            }
        }
    }

    private static ExecutionPlanDescription plan( final String name, final Map<String, Object> args, final long dbHits,
            final long pageCacheHits, final long pageCacheMisses, final long rows, final List<String> identifiers,
            final ExecutionPlanDescription... children )
    {
        return plan( name, args, identifiers, new ExecutionPlanDescription.ProfilerStatistics()
        {
            @Override
            public long getRows()
            {
                return rows;
            }

            @Override
            public long getDbHits()
            {
                return dbHits;
            }

            @Override
            public long getPageCacheHits()
            {
                return pageCacheHits;
            }

            @Override
            public long getPageCacheMisses()
            {
                return pageCacheMisses;
            }
        }, children );
    }

    private static ExecutionPlanDescription plan( final String name, final Map<String, Object> args,
            final List<String> identifiers, final ExecutionPlanDescription ... children )
    {
        return plan( name, args, identifiers, null, children );
    }

    private static ExecutionPlanDescription plan( final String name, final Map<String, Object> args,
            final List<String> identifiers, final ExecutionPlanDescription.ProfilerStatistics profile,
            final ExecutionPlanDescription ... children )
    {
        return new ExecutionPlanDescription()
        {
            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public List<ExecutionPlanDescription> getChildren()
            {
                return asList(children);
            }

            @Override
            public Map<String,Object> getArguments()
            {
                return args;
            }

            @Override
            public Set<String> getIdentifiers()
            {
                return new HashSet<>( identifiers );
            }

            @Override
            public boolean hasProfilerStatistics()
            {
                return profile != null;
            }

            @Override
            public ProfilerStatistics getProfilerStatistics()
            {
                return profile;
            }
        };
    }
}
