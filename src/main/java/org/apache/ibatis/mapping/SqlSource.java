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
package org.apache.ibatis.mapping;

/**
 * SQL 来源接口。它代表从 Mapper XML 或方法注解上，读取的一条 SQL 内容
 * 用于表示一条 SQL 语句的原始内容及其动态特性（如包含 ${} 或 #{} 的动态参数）。它负责在运行时生成最终可执行的 SQL 和参数映射。
 * Represents the content of a mapped statement read from an XML file or an annotation. It creates the SQL that will be
 * passed to the database out of the input parameter received from the user.
 * 表示从XML文件或注释中读取的映射语句的内容（原始内容）。它创建将从用户接收到的输入参数传递到数据库的SQL。
 *
 * @author Clinton Begin
 */
public interface SqlSource {

  /**
   * 根据传入的参数对象，返回 BoundSql 对象
   *
   * @param parameterObject 参数对象
   * @return BoundSql 对象
   */
  BoundSql getBoundSql(Object parameterObject);

}
