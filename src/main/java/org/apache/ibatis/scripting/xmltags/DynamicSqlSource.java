/*
 *    Copyright 2009-2022 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 动态的 SqlSource 实现类，实现 SqlSource 接口
 * 含 ${}、OGNL 表达式 或 XML注解中的动态标签
 *
 * 关于OGNL表达式示例：
 * <select id="findUser" resultType="User">
 *     SELECT * FROM user
 *     <where>
 *         <if test="name != null and name != ''">
 *             AND name = #{name}
 *         </if>
 *         <if test="age > 18">
 *             AND age = #{age}
 *         </if>
 *     </where>
 * </select>
 *
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  // 配置对象
  private final Configuration configuration;
  // 根 SqlNode 对象
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  // 适用于使用了 OGNL 表达式，或者使用了 ${} 表达式的 SQL ，所以它是动态的，
  // 需要在每次执行 #getBoundSql(Object parameterObject) 方法，根据参数，生成对应的 SQL
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 应用 rootSqlNode，即调用apply方法，解析处理动态标签
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    rootSqlNode.apply(context);
    // 创建 SqlSourceBuilder 对象
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 解析出 SqlSource 对象（将#{} 替换成 ？）
    // 此时 SqlSource为最终解析后的SQL，即StaticSqlSource
    // 其中SQL中#{}中需要注入的参数都被封装到了parameterMappings中，并且#{}都被替换成了？封装到了SqlSource的sql对象中
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass(); // 获取入参的类型
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 获得 BoundSql 对象
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 添加附加参数到 BoundSql 对象的additionalParameter变量中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    // 返回 BoundSql 对象
    return boundSql;
  }

}
