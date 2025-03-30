/*
 *    Copyright 2009-2024 the original author or authors.
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

/**
 * The annotation that specify a method that provide an SQL for retrieving record(s).
 * <p>
 * <b>How to use:</b>
 *
 * <pre>{@code
 * public interface UserMapper {
 *
 *   @SelectProvider(type = SqlProvider.class, method = "selectById")
 *   User selectById(int id);
 *
 *   public static class SqlProvider {
 *     public static String selectById() {
 *       return "SELECT id, name FROM users WHERE id = #{id}";
 *     }
 *   }
 *
 * }
 * }</pre>
 *
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(SelectProvider.List.class)
public @interface SelectProvider {

  /**
   * Specify a type that implements an SQL provider method.
   * 指定实现SQL提供程序方法的类型
   *
   * @return a type that implements an SQL provider method 实现SQL提供程序方法的类型
   *
   * @since 3.5.2
   *
   * @see #type()
   */
  Class<?> value() default void.class;

  /**
   * Specify a type that implements an SQL provider method.
   * 指定实现SQL提供程序方法的类型
   * <p>
   * This attribute is alias of {@link #value()}. 这个属性是{@link #value()}的别名
   *
   * @return a type that implements an SQL provider method 实现SQL提供程序方法的类型
   *
   * @see #value()
   */
  Class<?> type() default void.class;

  /**
   * Specify a method for providing an SQL.
   * 指定提供SQL的方法
   * <p>
   * Since 3.5.1, this attribute can omit. 从3.5.1开始，这个属性可以省略。
   * <p>
   * If this attribute omit, the MyBatis will call a method that decide by following rules. 如果省略此属性，MyBatis将调用一个方法，该方法根据以下规则决定
   * <ul>
   * <li>If class that specified the {@link #type()} attribute implements the
   * {@link org.apache.ibatis.builder.annotation.ProviderMethodResolver}, the MyBatis use a method that returned by it.
   * 如果指定{@link #type()}属性的类实现了
   * {@link org.apache.ibatis.builder.annotation.ProviderMethodResolver}， MyBatis使用由它返回的方法
   * </li>
   * <li>If cannot resolve a method by {@link org.apache.ibatis.builder.annotation.ProviderMethodResolver} (= not
   * implement it or it was returned <code>null</code>), the MyBatis will search and use a fallback method that named
   * <code>provideSql</code> from specified type.</li>
   * 如果无法解析方法 {@link org.apache.ibatis.builder.annotation.ProviderMethodResolver} 如果没有实现它，或者返回<code>null</code>)，
   * MyBatis将搜索并使用一个名为<code>provideSql</code> from指定的类型
   * </ul>
   *
   * @return a method name of method for providing an SQL 提供SQL的方法的方法名
   */
  String method() default "";

  /**
   * @return A database id that correspond this provider 与此提供程序对应的数据库id
   *
   * @since 3.5.5
   */
  String databaseId() default "";

  /**
   * Returns whether this select affects DB data.<br>
   * e.g. RETURNING of PostgreSQL or OUTPUT of MS SQL Server.
   * 返回此选择是否影响DB数据。
   * 例如：PostgreSQL的return或MS SQL Server的OUTPUT。
   *
   * @return {@code true} if this select affects DB data; {@code false} if otherwise
   *
   * @since 3.5.12
   */
  boolean affectData() default false;

  /**
   * The container annotation for {@link SelectProvider}.
   * SelectProviderd的容器式注解
   *
   * @author Kazuki Shimizu
   *
   * @since 3.5.5
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    SelectProvider[] value();
  }

}
