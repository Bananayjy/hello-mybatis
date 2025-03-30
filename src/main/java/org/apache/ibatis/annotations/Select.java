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
 * The annotation that specify an SQL for retrieving record(s).
 * <p>
 * <b>How to use:</b>
 * <ul>
 * <li>Simple:
 *
 * <pre>{@code
 * public interface UserMapper {
 *   @Select("SELECT id, name FROM users WHERE id = #{id}")
 *   User selectById(int id);
 * }
 * }</pre>
 *
 * </li>
 * <li>Dynamic SQL:
 *
 * <pre>{@code
 * public interface UserMapper {
 *   @Select({ "<script>", "select * from users", "where name = #{name}",
 *       "<if test=\"age != null\"> age = #{age} </if>", "</script>" })
 *   User select(@NotNull String name, @Nullable Integer age);
 * }
 * }</pre>
 *
 * </li>
 * </ul>
 *
 * @author Clinton Begin
 *
 * @see <a href="https://mybatis.org/mybatis-3/dynamic-sql.html">How to use Dynamic SQL</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Select.List.class)
public @interface Select {
  /**
   * Returns an SQL for retrieving record(s).
   * 返回用于检索记录的SQL
   *
   * @return an SQL for retrieving record(s)
   */
  String[] value();

  /**
   * @return A database id that correspond this statement
   * 与此语句对应的数据库id
   * 用于多数据库支持的特性，允许你为不同的数据库编写特定的 SQL 语句
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
   * The container annotation for {@link Select}.
   * Select的容器式注解
   *
   * @author Kazuki Shimizu
   *
   * @since 3.5.5
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    Select[] value();
  }

}
