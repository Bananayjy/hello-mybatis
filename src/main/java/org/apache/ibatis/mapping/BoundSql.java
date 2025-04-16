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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;

/**
 * 一次可执行的 SQL 封装
 * 用于表示最终可执行的 SQL 语句及其参数映射信息，是 SqlSource 解析后的结果
 * 包含了 JDBC 可直接执行的 SQL（已替换 #{} 为 ?）和参数绑定信息（ParameterMapping，即#{xxx}的xxx信息都会放在这里）
 *
 * 作用：
 * 1.存储可执行的 SQL：将动态 SQL（如 <if>、${}、#{}）转换为静态 SQL（带 ? 占位符）。
 * 2.维护参数映射：记录 #{} 中的参数名、类型、参数处理器（TypeHandler）
 * 3.供 Executor 使用：最终由 JDBC PreparedStatement 执行
 * An actual SQL String got from an {@link SqlSource} after having processed any dynamic content. The SQL may have SQL
 * placeholders "?" and a list (ordered) of a parameter mappings with the additional information for each parameter (at
 * least the property name of the input object to read the value from).
 * 在处理任何动态内容之后，从{@link SqlSource}获得的实际SQL字符串。SQL可能有SQL占位符“？”和一个参数映射列表（有序），
 * 其中包含每个参数的附加信息（至少是要从中读取值的输入对象的属性名）。
 * <p>
 * Can also have additional parameters that are created by the dynamic language (for loops, bind...).
 * 还可以有由动态语言创建的附加参数（for循环，bind…）
 *
 * @author Clinton Begin
 */
public class BoundSql {

  // SQL 语句
  private final String sql;
  //  ParameterMapping 数组（参数绑定信息）
  private final List<ParameterMapping> parameterMappings;
  // 参数对象（传入的）
  private final Object parameterObject;
  // 附加的参数集合
  private final Map<String, Object> additionalParameters;
  // {@link #additionalParameters} 的 MetaObject 对象
  private final MetaObject metaParameters;

  public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings,
      Object parameterObject) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.parameterObject = parameterObject;
    this.additionalParameters = new HashMap<>();
    this.metaParameters = configuration.newMetaObject(additionalParameters);
  }

  public String getSql() {
    return sql;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  public Object getParameterObject() {
    return parameterObject;
  }

  public boolean hasAdditionalParameter(String name) {
    String paramName = new PropertyTokenizer(name).getName();
    return additionalParameters.containsKey(paramName);
  }

  public void setAdditionalParameter(String name, Object value) {
    metaParameters.setValue(name, value);
  }

  public Object getAdditionalParameter(String name) {
    return metaParameters.getValue(name);
  }

  public Map<String, Object> getAdditionalParameters() {
    return additionalParameters;
  }
}
