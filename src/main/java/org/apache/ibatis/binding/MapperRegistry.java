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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper 注册表
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

  // MyBatis Configuration 对象（相关配置信息）
  private final Configuration config;
  // MapperProxyFactory的映射
  // key：Mapper接口  Value：MapperProxyFactory（创建Mapper代理实现类的工厂）
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new ConcurrentHashMap<>();

  // MapperRegistry构造器
  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  // 获得 Mapper Proxy 对象
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    // 获得 MapperProxyFactory 对象
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) { // 不存在，抛出异常
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    // 创建Mapper Proxy对象
    try {
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  // 判断当前类是否已经添加到 knownMappers 中
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 将符合的类，添加到 knownMappers （MapperRegistry被Configuration配置类维护）
   * @param type 需要添加的类对象
   */
  public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) { // 当前类需要是接口
      if (hasMapper(type)) {  // 判断是否已经添加，如果已经添加则抛出异常
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      // 标记加载是否完成
      boolean loadCompleted = false;
      try {
        // 将当前类对象type和其MapperProxyFactory对象添加到knownMappers中
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // It's important that the type is added before the parser is run 在解析器运行之前添加类型是很重要的
        // otherwise the binding may automatically be attempted by the 方法可能会自动尝试绑定
        // mapper parser. If the type is already known, it won't try. 映射器解析器。如果类型是已知的，它就不会尝试
        // 通过MapperAnnotationBuilder对象解析Mapper接口的注解配置
        // 创建MapperAnnotationBuilder对象
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        // 调用MapperAnnotationBuilder对象的parse方法对注解进行解析
        parser.parse();
        // 标记加载完成
        loadCompleted = true;
      } finally {
        if (!loadCompleted) { // 若加载未完成，从 knownMappers 中移除
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * Gets the mappers.
   * 获得 Mappers
   * @return the mappers
   *
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * Adds the mappers.
   * 添加mappers
   *
   * @param packageName
   *          the package name 包名
   * @param superType
   *          the super type 当前匹配的类对象
   *
   * @since 3.2.2
   */
  public void addMappers(String packageName, Class<?> superType) {
    // 通过resolverUtil扫描指定包下的指定类
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {  // 遍历匹配到的类，添加到knowMappers中
      addMapper(mapperClass);
    }
  }

  /**
   * Adds the mappers.
   * 添加mappers（默认匹配寻找Object类对象）
   *
   * @param packageName
   *          the package name 包名
   *
   * @since 3.2.2
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
