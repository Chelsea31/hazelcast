/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map.impl.recordstore;

import com.hazelcast.cluster.Address;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.EntryView;
import com.hazelcast.internal.eviction.ClearExpiredRecordsTask;
import com.hazelcast.internal.eviction.ExpiredKey;
import com.hazelcast.internal.nearcache.impl.invalidation.InvalidationQueue;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.util.ExceptionUtil;
import com.hazelcast.map.impl.MapContainer;
import com.hazelcast.map.impl.event.MapEventPublisher;
import com.hazelcast.map.impl.eviction.Evictor;
import com.hazelcast.map.impl.record.Record;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.spi.impl.eventservice.EventService;
import com.hazelcast.spi.merge.SplitBrainMergeTypes.MapMergeTypes;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.spi.properties.HazelcastProperties;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.core.EntryEventType.EVICTED;
import static com.hazelcast.core.EntryEventType.EXPIRED;
import static com.hazelcast.internal.util.ToHeapDataConverter.toHeapData;
import static com.hazelcast.map.impl.ExpirationTimeSetter.calculateExpirationWithDelay;
import static com.hazelcast.map.impl.ExpirationTimeSetter.getIdlenessStartTime;
import static com.hazelcast.map.impl.ExpirationTimeSetter.getLifeStartTime;
import static com.hazelcast.map.impl.ExpirationTimeSetter.setExpirationTime;
import static com.hazelcast.map.impl.MapService.SERVICE_NAME;
import static com.hazelcast.map.impl.eviction.Evictor.NULL_EVICTOR;
import static com.hazelcast.map.impl.record.Record.UNSET;

/**
 * Contains eviction specific functionality.
 */
public abstract class AbstractEvictableRecordStore extends AbstractRecordStore {

    private static final int ONE_HUNDRED_PERCENT = 100;
    private static final int MAX_SAMPLE_AT_A_TIME = 16;
    private static final int MIN_TOTAL_NUMBER_OF_KEYS_TO_SCAN = 100;
    private static final ThreadLocal<List> SAMPLING_LIST
            = ThreadLocal.withInitial(() -> new ArrayList<>(MAX_SAMPLE_AT_A_TIME << 1));

    protected final long expiryDelayMillis;
    protected final Address thisAddress;
    protected final EventService eventService;
    protected final MapEventPublisher mapEventPublisher;
    protected final ClearExpiredRecordsTask clearExpiredRecordsTask;
    protected final InvalidationQueue<ExpiredKey> expiredKeys = new InvalidationQueue<>();
    /**
     * Iterates over a pre-set entry count/percentage in one round.
     * Used in expiration logic for traversing entries. Initializes lazily.
     */
    protected Iterator<Map.Entry<Data, Record>> expirationIterator;

    protected volatile boolean hasEntryWithCustomExpiration;

    protected AbstractEvictableRecordStore(MapContainer mapContainer, int partitionId) {
        super(mapContainer, partitionId);
        NodeEngine nodeEngine = mapServiceContext.getNodeEngine();
        HazelcastProperties hazelcastProperties = nodeEngine.getProperties();
        expiryDelayMillis = hazelcastProperties.getMillis(ClusterProperty.MAP_EXPIRY_DELAY_SECONDS);
        eventService = nodeEngine.getEventService();
        mapEventPublisher = mapServiceContext.getMapEventPublisher();
        thisAddress = nodeEngine.getThisAddress();
        clearExpiredRecordsTask = mapServiceContext.getExpirationManager().getTask();
    }

    /**
     * Returns {@code true} if this record store has at least one candidate entry
     * for expiration (idle or tll) otherwise returns {@code false}.
     */
    private boolean isRecordStoreExpirable() {
        MapConfig mapConfig = mapContainer.getMapConfig();
        return hasEntryWithCustomExpiration || mapConfig.getMaxIdleSeconds() > 0
                || mapConfig.getTimeToLiveSeconds() > 0;
    }

    @Override
    public void evictExpiredEntries(int percentage, boolean backup) {
        long now = getNow();
        int size = size();
        int maxSample = getMaxSampleCount(size, percentage);
        int maxRetry = 3;
        int loop = 0;
        int evictedEntryCount = 0;
        while (loop++ < maxRetry && evictedEntryCount < maxSample) {
            evictedEntryCount += evictExpiredEntriesInternal(maxSample, now, backup);
        }

        accumulateOrSendExpiredKey(null, null);
    }

    @Override
    public boolean isExpirable() {
        return isRecordStoreExpirable();
    }

    /**
     * Intended to put an upper bound to sampling. Used in evictions.
     *
     * @param size       of iterate-able.
     * @param percentage percentage of size.
     * @return 100 If calculated sample count is less than
     * 100, otherwise returns calculated sample count.
     */
    private int getMaxSampleCount(int size, int percentage) {
        if (size <= MIN_TOTAL_NUMBER_OF_KEYS_TO_SCAN) {
            return size;
        }

        int numberOfKeysInPercentage = (int) (1D * size * percentage / ONE_HUNDRED_PERCENT);
        return Math.max(MIN_TOTAL_NUMBER_OF_KEYS_TO_SCAN, numberOfKeysInPercentage);
    }

    private int evictExpiredEntriesInternal(final int maxSample, long now, boolean backup) {
        int evictedCount = 0;
        int sampledCount = 0;
        try {
            Iterator<Map.Entry<Data, Record>> iterator = initExpirationIterator();
            while (sampledCount < maxSample && iterator.hasNext()) {
                sampledCount += sampleForExpiry();
                evictedCount += evictExpiredSamples(now, backup);
            }
        } catch (Exception t) {
            SAMPLING_LIST.get().clear();
            throw ExceptionUtil.rethrow(t);
        }
        return evictedCount;
    }

    private int sampleForExpiry() {
        List sampledPairs = SAMPLING_LIST.get();
        int sampledCount = 0;
        Iterator<Map.Entry<Data, Record>> iterator = expirationIterator;
        while (iterator.hasNext() && sampledCount++ < MAX_SAMPLE_AT_A_TIME) {
            Map.Entry<Data, Record> entry = iterator.next();
            sampledPairs.add(entry.getKey());
            sampledPairs.add(entry.getValue());
        }
        return sampledCount;
    }

    /**
     * Evict expired keys among sampled ones.
     *
     * @return number of evicted keys.
     */
    private int evictExpiredSamples(long now, boolean backup) {
        int evictedCount = 0;

        List sampledPairs = SAMPLING_LIST.get();
        try {
            for (int i = 0; i < sampledPairs.size(); i += 2) {
                Data key = (Data) sampledPairs.get(i);
                Record record = (Record) sampledPairs.get(i + 1);
                if (getOrNullIfExpired(key, record, now, backup) == null) {
                    evictedCount++;
                }
            }
        } finally {
            sampledPairs.clear();
        }

        return evictedCount;
    }

    private Iterator<Map.Entry<Data, Record>> initExpirationIterator() {
        if (expirationIterator == null || !expirationIterator.hasNext()) {
            expirationIterator = storage.mutationTolerantIterator();
        }
        return expirationIterator;
    }

    @Override
    public void evictEntries(Data excludedKey) {
        if (shouldEvict()) {
            mapContainer.getEvictor().evict(this, excludedKey);
        }
    }

    @Override
    public void sampleAndForceRemoveEntries(int entryCountToRemove) {
        Queue<Data> keysToRemove = new LinkedList<>();
        Iterable<EntryView> sample = storage.getRandomSamples(entryCountToRemove);
        for (EntryView entryView : sample) {
            Data dataKey = storage.extractDataKeyFromLazy(entryView);
            keysToRemove.add(dataKey);
        }

        Data dataKey;
        while ((dataKey = keysToRemove.poll()) != null) {
            evict(dataKey, true);
        }
    }

    @Override
    public boolean shouldEvict() {
        Evictor evictor = mapContainer.getEvictor();
        return evictor != NULL_EVICTOR && evictor.checkEvictable(this);
    }

    protected void markRecordStoreExpirable(long ttl, long maxIdle) {
        if (isTtlDefined(ttl) || isMaxIdleDefined(maxIdle)) {
            hasEntryWithCustomExpiration = true;
        }

        if (isRecordStoreExpirable()) {
            mapServiceContext.getExpirationManager().scheduleExpirationTask();
        }
    }

    // this method is overridden on ee
    protected boolean isTtlDefined(long ttl) {
        return ttl > 0L && ttl < Long.MAX_VALUE;
    }

    protected boolean isMaxIdleDefined(long maxIdle) {
        return maxIdle > 0L && maxIdle < Long.MAX_VALUE;
    }

    @Override
    public boolean isTtlOrMaxIdleDefined(Record record) {
        long ttl = record.getTtl();
        long maxIdle = record.getMaxIdle();
        return isTtlDefined(ttl) || isMaxIdleDefined(maxIdle);
    }


    @Override
    public Record getOrNullIfExpired(Data key, Record record,
                                     long now, boolean backup) {
        if (!isRecordStoreExpirable()) {
            return record;
        }
        if (record == null) {
            return null;
        }
        if (isLocked(key)) {
            return record;
        }
        if (!isExpired(record, now, backup)) {
            return record;
        }
        evict(key, backup);
        if (!backup) {
            doPostEvictionOperations(key, record);
        }
        return null;
    }

    public boolean isExpired(Record record, long now, boolean backup) {
        return record == null
                || isIdleExpired(record, now, backup)
                || isTTLExpired(record, now, backup);
    }

    private boolean isIdleExpired(Record record, long now, boolean backup) {
        if (backup && mapServiceContext.getClearExpiredRecordsTask().canPrimaryDriveExpiration()) {
            // don't check idle expiry on backup
            return false;
        }

        long maxIdleMillis = getRecordMaxIdleOrConfig(record);
        if (maxIdleMillis < 1L || maxIdleMillis == Long.MAX_VALUE) {
            return false;
        }

        long idlenessStartTime = getIdlenessStartTime(record);
        long idleMillis = calculateExpirationWithDelay(maxIdleMillis, expiryDelayMillis, backup);
        long elapsedMillis = now - idlenessStartTime;
        return elapsedMillis >= idleMillis;
    }

    private boolean isTTLExpired(Record record, long now, boolean backup) {
        if (record == null) {
            return false;
        }
        long ttl = getRecordTTLOrConfig(record);
        // when ttl is zero or negative or Long.MAX_VALUE, entry should live forever.
        if (ttl < 1L || ttl == Long.MAX_VALUE) {
            return false;
        }
        long ttlStartTime = getLifeStartTime(record);
        long ttlMillis = calculateExpirationWithDelay(ttl, expiryDelayMillis, backup);
        long elapsedMillis = now - ttlStartTime;
        return elapsedMillis >= ttlMillis;
    }

    private long getRecordMaxIdleOrConfig(Record record) {
        if (record.getMaxIdle() != UNSET) {
            return record.getMaxIdle();
        }

        return TimeUnit.SECONDS.toMillis(mapContainer.getMapConfig().getMaxIdleSeconds());
    }

    private long getRecordTTLOrConfig(Record record) {
        if (record.getTtl() != UNSET) {
            return record.getTtl();
        }

        return TimeUnit.SECONDS.toMillis(mapContainer.getMapConfig().getTimeToLiveSeconds());
    }

    @Override
    public void doPostEvictionOperations(Data dataKey, Record record) {
        Object value = record.getValue();

        long now = getNow();
        boolean idleExpired = isIdleExpired(record, now, false);
        boolean ttlExpired = isTTLExpired(record, now, false);
        boolean expired = idleExpired || ttlExpired;

        if (eventService.hasEventRegistration(SERVICE_NAME, name)) {
            mapEventPublisher.publishEvent(thisAddress, name,
                    expired ? EXPIRED : EVICTED, dataKey, value, null);
        }

        if (!ttlExpired && idleExpired) {
            // only send expired key to backup if it is expired according to idleness.
            accumulateOrSendExpiredKey(dataKey, record);
        }
    }

    @Override
    public InvalidationQueue<ExpiredKey> getExpiredKeysQueue() {
        return expiredKeys;
    }

    private void accumulateOrSendExpiredKey(Data dataKey, Record record) {
        if (mapContainer.getTotalBackupCount() == 0) {
            return;
        }

        if (record != null) {
            expiredKeys.offer(new ExpiredKey(toHeapData(dataKey), record.getCreationTime()));
        }

        clearExpiredRecordsTask.tryToSendBackupExpiryOp(this, true);
    }

    @Override
    public void accessRecord(Record record, long now) {
        record.onAccess(now);
        updateStatsOnGet(now);
        setExpirationTime(record);
    }

    protected void mergeRecordExpiration(Record record, MapMergeTypes mergingEntry) {
        mergeRecordExpiration(record, mergingEntry.getTtl(), mergingEntry.getMaxIdle(), mergingEntry.getCreationTime(),
                mergingEntry.getLastAccessTime(), mergingEntry.getLastUpdateTime());
    }

    private void mergeRecordExpiration(Record record, long ttlMillis, Long maxIdleMillis,
                                       long creationTime, long lastAccessTime, long lastUpdateTime) {
        record.setTtl(ttlMillis);
        // WAN events received from source cluster also carry null maxIdle
        // see com.hazelcast.map.impl.wan.WanMapEntryView.getMaxIdle
        if (maxIdleMillis != null) {
            record.setMaxIdle(maxIdleMillis);
        }
        record.setCreationTime(creationTime);
        record.setLastAccessTime(lastAccessTime);
        record.setLastUpdateTime(lastUpdateTime);

        setExpirationTime(record);

        markRecordStoreExpirable(record.getTtl(), record.getMaxIdle());
    }
}
