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
package org.apache.ibatis.scripting;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;

// 语言驱动接口
//是 MyBatis 中负责处理 SQL 脚本的核心接口，它定义了如何解析和执行动态 SQL 的规范
public interface LanguageDriver {

  /**
   *
   * 创建 ParameterHandler 对象
   * Creates a {@link ParameterHandler} that passes the actual parameters to the the JDBC statement.
   * 创建一个{@link ParameterHandler}，将实际参数传递给JDBC语句
   *
   * @author Frank D. Martinez [mnesarco]
   *
   * @param mappedStatement
   *          The mapped statement that is being executed 正在执行的映射语句
   * @param parameterObject
   *          The input parameter object (can be null) 输入参数对象（可以为null）
   * @param boundSql
   *          The resulting SQL once the dynamic language has been executed. 执行动态语言后的结果SQL
   *
   * @return the parameter handler ParameterHandler对象
   *
   * @see DefaultParameterHandler
   */
  ParameterHandler createParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql);

  /**
   * 创建 SqlSource 对象，从 Mapper XML 配置的 Statement 标签中，即 <select /> 等
   * Creates an {@link SqlSource} that will hold the statement read from a mapper xml file. It is called during startup,
   * when the mapped statement is read from a class or an xml file.
   * 创建{@link SqlSource}来保存从mapper xml文件中读取的语句。当从类或xml文件中读取映射语句时，在启动期间调用它。
   *
   * @param configuration
   *          The MyBatis configuration Myabtis的配置文件
   * @param script
   *          XNode parsed from a XML file  从XML文件解析的XNode
   * @param parameterType
   *          input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be
   *          null. 从映射器方法获得或在parameterType xml属性中指定的输入参数类型。可以为空
   *
   * @return the sql source
   */
  SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType);

  /**
   * 创建 SqlSource 对象，从方法注解配置，即 @Select 等。
   * Creates an {@link SqlSource} that will hold the statement read from an annotation. It is called during startup,
   * when the mapped statement is read from a class or an xml file.
   * 创建{@link SqlSource}来保存从注释中读取的语句。当从类或xml文件中读取映射语句时，在启动期间调用它
   *
   * @param configuration
   *          The MyBatis configuration Myabtis的配置文件
   * @param script
   *          The content of the annotation 注释的内容
   * @param parameterType
   *          input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be
   *          null. 从映射器方法获得或在parameterType xml属性中指定的输入参数类型。可以为空
   *
   * @return the sql source
   */
  SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType);

}
