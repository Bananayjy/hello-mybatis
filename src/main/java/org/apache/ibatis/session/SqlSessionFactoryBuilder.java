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
package org.apache.ibatis.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * MyBatis 的初始化流程的入口
 *
 * Builds {@link SqlSession} instances.
 *
 * @author Clinton Begin
 */
public class SqlSessionFactoryBuilder {

  /**
   * build的重载方法，最终调用的都{@link SqlSessionFactoryBuilder#build(Reader, String, Properties)}方法
   */
  public SqlSessionFactory build(Reader reader) {
    return build(reader, null, null);
  }

  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
  }

  /**
   * 构造 SqlSessionFactory 对象
   * @param reader Reader 对象
   * @param environment 环境信息
   * @param properties properties变量
   * @return SqlSessionFactory 工厂对象
   */
  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {
      // 1.创建XMLConfigBuilder对象
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
      // 2.parse.parse():执行 XML 解析，返回 Configuration 对象
      // 3.build: 创建 DefaultSqlSessionFactory 对象
      return build(parser.parse());
    } catch (Exception e) { // 创建SqlSession异常，抛出相关异常信息
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      // 重置ErrorContext对象
      ErrorContext.instance().reset();
      try {
        // 关闭readder对象
        if (reader != null) {
          reader.close();
        }
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }


  /**
   * build的重载方法，最终调用的都{@link SqlSessionFactoryBuilder#build(InputStream, String, Properties)}方法
   */
  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }


  /**
   * 构造 SqlSessionFactory 对象，本质和 {@link SqlSessionFactoryBuilder#build(Reader, String, Properties)}方法一样
   * 只不过获取创建 XMLConfigBuilder 对象的时候使用的输入数据的类型不同，一个使用 Reader，另一个使用
   * - Reader 是 Java 中用于读取字符流的类，通常用于处理文本数据，如 XML 配置文件（UTF-8 编码等）
   * - InputStream 是 Java 中用于读取字节流的类，通常用于处理二进制数据，如从文件、网络或其他来源读取字节数据
   *
   * @param inputStream 输入流对象
   * @param environment 环境信息
   * @param properties properties变量
   * @return SqlSessionFactory 工厂对象
   */
  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      // 1.创建XMLConfigBuilder对象
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      // 2.parse.parse():执行 XML 解析，返回 Configuration 对象
      // 3.build: 创建 DefaultSqlSessionFactory 对象
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        if (inputStream != null) {
          inputStream.close();
        }
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }


  /**
   * 创建DefaultSqlSessionFactory 对象
   * @param config Configuration 对象
   * @return DefaultSqlSessionFactory 对象
   */
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
