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
package org.apache.ibatis.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseBuilder {

  // Mybatis核心配置类：负责存储和管理 MyBatis 的各种配置信息，包括数据库连接、映射文件、插件、缓存、SQL 映射等内容
  // XML 和注解中解析到的配置，最终都会设置到 Configuration 对象中
  protected final Configuration configuration;
  // 类型别名注册对象：负责注册和管理类型别名（type alias），以及根据别名获取相应的 Java 类型
  protected final TypeAliasRegistry typeAliasRegistry;
  // 类型处理器别名注册对象：负责注册和管理类型类型处理器，以及根据类型获取响应的类型处理器
  protected final TypeHandlerRegistry typeHandlerRegistry;

  /**
   * 构造器
   * 传入Configuration对象，完成成员变量configuration、typeAliasRegistry、typeHandlerRegistry的赋值
   * @param configuration Configuration 对象
   */
  public BaseBuilder(Configuration configuration) {
    this.configuration = configuration;
    this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
    this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 创建正则表达式
   *
   * @param regex 指定表达式
   * @param defaultValue 默认表达式
   * @return 正则表达式
   */
  protected Pattern parseExpression(String regex, String defaultValue) {
    return Pattern.compile(regex == null ? defaultValue : regex);
  }


  /**
   * 如下有很多xxxValueOf
   * 将字符串转换成对应的数据类型xxx，并返回对应的值
   */
  protected Boolean booleanValueOf(String value, Boolean defaultValue) {
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  protected Integer integerValueOf(String value, Integer defaultValue) {
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  protected Set<String> stringSetValueOf(String value, String defaultValue) {
    value = value == null ? defaultValue : value;
    return new HashSet<>(Arrays.asList(value.split(",")));
  }


  /**
   * 解析对应的 JdbcType 类型
   * @param alias jdbc别名，如ARRAY、BIT……
   * @return jdbcType对象（枚举类中的code对象维护java.sql.Types对应的值）
   */
  protected JdbcType resolveJdbcType(String alias) {
    try {
      return alias == null ? null : JdbcType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
    }
  }

  /**
   * 解析对应的 ResultSetType 类型
   * @param alias 别名，如DEFAULT、FORWARD_ONLY……
   * @return ResultSetType 对象
   */
  protected ResultSetType resolveResultSetType(String alias) {
    try {
      return alias == null ? null : ResultSetType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
    }
  }

  /**
   * 解析对应的 ParameterMode 类型
   * @param alias 别名，如IN、OUT、INOUT
   * @return ParameterMode对象
   */
  protected ParameterMode resolveParameterMode(String alias) {
    try {
      return alias == null ? null : ParameterMode.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
    }
  }

  /**
   * 创建指定类型对象
   * 从 typeAliasRegistry中 维护的 typeAliases中，通过别名或类全名（如果typeAlias中找不到就通过类全限定名进行查找对应的类对象），获得对应的类
   * @param alias 类型别名或者类的全限定名
   * @return 创建指定的对象
   */
  protected Object createInstance(String alias) {
    // 获得对应的类型
    Class<?> clazz = resolveClass(alias);
    try {
      // 创建对象
      return clazz == null ? null : clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new BuilderException("Error creating instance. Cause: " + e, e);
    }
  }

  protected <T> Class<? extends T> resolveClass(String alias) {
    try {
      return alias == null ? null : resolveAlias(alias);
    } catch (Exception e) {
      throw new BuilderException("Error resolving class. Cause: " + e, e);
    }
  }

  /**
   * 从 typeHandlerRegistry 中获得或创建对应的 TypeHandler 对象
   * @param javaType java的类对象
   * @param typeHandlerAlias 类处理器别名
   * @return 类处理器
   */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
    if (typeHandlerAlias == null) { // 类型别名 == null，直接返回结果为null
      return null;
    }
    // 通过typeAliasRegistry对象根据别名获取对应的类对象（typeHandlerAlias如果无法通过别名获取，会根据全限定名称也可以）
    Class<?> type = resolveClass(typeHandlerAlias);
    if (type != null && !TypeHandler.class.isAssignableFrom(type)) {  // 如果type不是TypeHandler的子类，抛出异常
      throw new BuilderException(
          "Type " + type.getName() + " is not a valid TypeHandler because it does not implement TypeHandler interface");
    }
    @SuppressWarnings("unchecked") // already verified it is a TypeHandler
    Class<? extends TypeHandler<?>> typeHandlerType = (Class<? extends TypeHandler<?>>) type; // 将其转化为TypeHandler的实现类的类对象
    return resolveTypeHandler(javaType, typeHandlerType);
  }

  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
    if (typeHandlerType == null) {
      return null;
    }
    // javaType ignored for injected handlers see issue #746 for full detail
    // 先获得 TypeHandler 对象（直接从typeHandlerRegistry维护的allTypeHandlersMap对象中获取）
    TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);
    // if handler not in registry, create a new one, otherwise return directly
    // 如果不存在，进行创建 TypeHandler 对象
    return handler == null ? typeHandlerRegistry.getInstance(javaType, typeHandlerType) : handler;
  }

  // 通过typeAliasRegistry对象根据别名获取对应的类对象（typeHandlerAlias如果无法通过别名获取，会根据全限定名称也可以）
  protected <T> Class<? extends T> resolveAlias(String alias) {
    // 通过typeAliasRegistry对象根据别名获取对应的类对象（typeHandlerAlias如果无法通过别名获取，会根据全限定名称也可以）
    return typeAliasRegistry.resolveAlias(alias);
  }
}
