/*
 *    Copyright 2009-2023 the original author or authors.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.ibatis.reflection.ArrayUtil;

/**
 * 缓存键（实现 Cloneable、Serializable 接口）
 * 因为 MyBatis 中的缓存键不是一个简单的 String ，而是通过多个对象组成。所以 CacheKey 可以理解成将多个对象放在一起，计算其缓存键
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  // 单例 - 空缓存键
  public static final CacheKey NULL_CACHE_KEY = new CacheKey() {

    private static final long serialVersionUID = 1L;

    // 空缓存键 更新时抛出异常
    @Override
    public void update(Object object) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }

    // 空缓存键 更新全部时抛出异常
    @Override
    public void updateAll(Object[] objects) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
  };

  // 默认 {@link #org.apache.ibatis.cache.CacheKey.multiplier} 的值
  private static final int DEFAULT_MULTIPLIER = 37;

  // 默认 {@link #org.apache.ibatis.cache.CacheKey.hashcode} 的值
  private static final int DEFAULT_HASHCODE = 17;

  // hashcode 求值的系数
  private final int multiplier;

  // 缓存键的 hashcode
  private int hashcode;
  // 校验和
  private long checksum;
  // 调用 {@link org.apache.ibatis.cache.CacheKey.update(Object)}方法的次数
  private int count;

  // 8/21/2017 - Sonarlint flags this as needing to be marked transient. While true if content is not serializable, this
  // is not always true and thus should not be marked transient.
  // 存放用于计算 {@link #hashcode} 的对象的集合
  private List<Object> updateList;

  // 构造器
  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLIER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  public CacheKey(Object[] objects) {
    this();
    // 基于 objects ，更新相关属性
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  // 更新相关属性
  public void update(Object object) {
    // 方法参数 object 的 hashcode
    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

    // 调用update更新次数+1
    count++;
    // checksum 为 baseHashCode 的求和
    checksum += baseHashCode;
    // 计算新的 hashcode 值
    baseHashCode *= count;
    hashcode = multiplier * hashcode + baseHashCode;

    // 添加 object 到 updateList 中
    updateList.add(object);
  }

  // 遍历objects数组，并调用update更新相关属性
  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  // 比较是否相等
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    if ((hashcode != cacheKey.hashcode) || (checksum != cacheKey.checksum) || (count != cacheKey.count)) {
      return false;
    }

    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  // 获得 hashcode 值
  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringJoiner returnValue = new StringJoiner(":");
    returnValue.add(String.valueOf(hashcode));
    returnValue.add(String.valueOf(checksum));
    updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
    return returnValue.toString();
  }

  // 克隆对象
  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    // 克隆 CacheKey 对象
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    // 创建 updateList 数组，避免原数组修改（目的：深克隆）
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}
