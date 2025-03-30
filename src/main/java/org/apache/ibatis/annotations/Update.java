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

/**
 * The annotation that specify an SQL for updating record(s).
 * <p>
 * <b>How to use:</b>
 *
 * <pre>
 * public interface UserMapper {
 *   &#064;Update("UPDATE users SET name = #{name} WHERE id = #{id}")
 *   boolean update(User user);
 * }
 * </pre>
 *
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Update.List.class)
public @interface Update {
  /**
   * Returns an SQL for updating record(s).
   * 返回用于更新记录的SQL
   *
   * @return an SQL for updating record(s)
   */
  String[] value();

  /**
   * @return A database id that correspond this statement
   * 此语句对应的数据库id
   * 用于多数据库支持的特性，允许你为不同的数据库编写特定的 SQL 语句
   * @since 3.5.5
   */
  String databaseId() default "";

  /**
   * The container annotation for {@link Update}.
   * Update的容器式注解
   *
   * @author Kazuki Shimizu
   *
   * @since 3.5.5
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    Update[] value();
  }

}
