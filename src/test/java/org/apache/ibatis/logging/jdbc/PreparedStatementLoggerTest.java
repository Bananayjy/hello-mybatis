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
package org.apache.ibatis.logging.jdbc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreparedStatementLoggerTest {

  @Mock
  Log log;

  @Mock
  PreparedStatement preparedStatement;

  @Mock
  ResultSet resultSet;

  private PreparedStatement ps;

  @BeforeEach
  void setUp() throws SQLException {
    // 通过Mock生成的模拟对象，创建PreparedStatement对象
    ps = PreparedStatementLogger.newInstance(this.preparedStatement, log, 1);
  }

  @Test
  void shouldPrintParameters() throws SQLException {
    // 当调用log的isDebugEnabled时，返回true，即开启dubug日志打印
    when(log.isDebugEnabled()).thenReturn(true);
    // 当调用preparedStatement的executeQuery时，返回resultSet对象，即模拟查询结果
    when(preparedStatement.executeQuery(anyString())).thenReturn(resultSet);

    // 通过preparedStatement对象执行查询操作
    ps.setInt(1, 10);
    ResultSet rs = ps.executeQuery("select 1 limit ?");

    // 判断log是否调用dubug方法，并且参数包含Parameters: 10(Integer)
    verify(log).debug(contains("Parameters: 10(Integer)"));
    // 判断rs是否为空，并且rs是否与resultSet相同，如果相同，则说明执行查询操作失败
    Assertions.assertNotNull(rs);
    Assertions.assertNotSame(resultSet, rs);
  }

  @Test
  void shouldPrintNullParameters() throws SQLException {
    // 当调用log的isDebugEnabled时，返回true，即开启dubug日志打印
    when(log.isDebugEnabled()).thenReturn(true);
    // 当调用preparedStatement的execute时，返回true，即模拟执行成功
    when(preparedStatement.execute(anyString())).thenReturn(true);

    // 执行操作
    ps.setNull(1, JdbcType.VARCHAR.TYPE_CODE);
    boolean result = ps.execute("update name = ? from test");

    // 看log是否调用debug方法，并且参数为null
    verify(log).debug(contains("Parameters: null"));
    // 判断执行结果是否为true，如果为true，则说明执行成功
    Assertions.assertTrue(result);
  }

  // 验证当调用 getResultSet() 和 getParameterMetaData() 方法时，PreparedStatementLogger 不会触发 log.debug() 日志记录。
  @Test
  void shouldNotPrintLog() throws SQLException {
    ps.getResultSet();
    ps.getParameterMetaData();

    verify(log, never()).debug(anyString());
  }

  @Test
  void shouldPrintUpdateCount() throws SQLException {
    // 当调用log的isDebugEnabled时，返回true，即开启dubug日志打印
    when(log.isDebugEnabled()).thenReturn(true);
    // 当调用preparedStatement的getUpdateCount时，返回1，即模拟更新操作
    when(preparedStatement.getUpdateCount()).thenReturn(1);

    // 调用getUpdateCount方法
    ps.getUpdateCount();

    // 验证log是否调用了debug方法，并且参数包含Updates: 1
    verify(log).debug(contains("Updates: 1"));
  }
}
