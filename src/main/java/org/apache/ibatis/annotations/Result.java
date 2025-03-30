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
package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * The annotation that specify a mapping definition for the property.
 *
 * @see Results
 *
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Results.class)
public @interface Result {
  /**
   * Returns whether id column or not.
   *  是否是 ID 字段
   *
   * @return {@code true} if id column; {@code false} if otherwise
   */
  boolean id() default false;

  /**
   * Return the column name(or column label) to map to this argument.
   * 返回列名（或列标签）以映射到此参数。
   *
   * @return the column name(or column label)
   */
  String column() default "";

  /**
   * Returns the property name for applying this mapping.
   * 返回应用此映射的属性名称。
   *
   * @return the property name
   */
  String property() default "";

  /**
   * Return the java type for this argument.
   * 返回该参数的java类型。
   *
   * @return the java type
   */
  Class<?> javaType() default void.class;

  /**
   * Return the jdbc type for column that map to this argument.
   * 返回映射到该参数的列的jdbc类型。
   *
   * @return the jdbc type
   */
  JdbcType jdbcType() default JdbcType.UNDEFINED;

  /**
   * Returns the {@link TypeHandler} type for retrieving a column value from result set.
   * 返回从结果集中检索列值的{@link TypeHandler}类型
   *
   * @return the {@link TypeHandler} type
   */
  Class<? extends TypeHandler> typeHandler() default UnknownTypeHandler.class;

  /**
   * Returns the mapping definition for single relationship.
   * 返回单个关系的映射定义。
   *
   * @return the mapping definition for single relationship
   */
  One one() default @One;

  /**
   * Returns the mapping definition for collection relationship.
   * 返回集合关系的映射定义。
   *
   * @return the mapping definition for collection relationship
   */
  Many many() default @Many;
}
