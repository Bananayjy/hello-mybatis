/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;
import org.apache.ibatis.util.MapUtil;

/**
 * TransactionalCache 管理器，二级缓存的核心事务管理器
 * 专门用于在事务性环境中协调多个 TransactionalCache 实例的缓存操作，确保缓存数据与数据库事务的一致性
 * 因为二级缓存是支持跨 Session 进行共享，此处需要考虑事务，那么，必然需要做到事务提交时，
 * 才将当前事务中查询时产生的缓存，同步到二级缓存中，若事务回滚，这些操作将被丢弃，避免脏缓存。
 * 这个功能，就通过 TransactionalCacheManager 来实现
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  // Cache 和 TransactionalCache 的映射
  // 因为在一次的事务过程中，可能有多个不同的 MappedStatement 操作（即select操作），而它们可能对应多个 Cache 对象(不同命名空间的Cache对象不一样)
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  // 清空缓存
  public void clear(Cache cache) {
    // 对transactionalCaches中key为cache的数据进行清除
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    // 首先，获得 当前 Cache对象 对应的 TransactionalCache 对象
    // 然后从 TransactionalCache 对象中，获得 key 对应的值
    return getTransactionalCache(cache).getObject(key);
  }

  // 添加 Cache + KV ，到缓存中
  public void putObject(Cache cache, CacheKey key, Object value) {
    // 首先，获得 Cache 对应的 TransactionalCache 对象
    // 然后，添加 KV 到 TransactionalCache 对象中
    getTransactionalCache(cache).putObject(key, value);
  }

  // 提交所有 TransactionalCache
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  // 回滚所有 TransactionalCache
  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  // 获取当前Cache对象对应的TransactionalCache 管理器
  // 如果不存在，则创建一个 TransactionalCache 对象，并添加到 transactionalCaches 中
  private TransactionalCache getTransactionalCache(Cache cache) {
    return MapUtil.computeIfAbsent(transactionalCaches, cache, TransactionalCache::new);
  }

}
