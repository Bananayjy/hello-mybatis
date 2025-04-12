/*
 *    Copyright 2009-2025 the original author or authors.
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

/**
 * <if /> 标签的 SqlNode 实现类
 * @author Clinton Begin
 */
public class IfSqlNode implements SqlNode {
  // 表达式求值程序(单例)
  private final ExpressionEvaluator evaluator = ExpressionEvaluator.INSTANCE;
  // 判断表达式
  private final String test;
  // 内嵌的 SqlNode 节点
  private final SqlNode contents;

  public IfSqlNode(SqlNode contents, String test) {
    this.test = test;
    this.contents = contents;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 判断是否符合条件
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      //  符合，执行 contents 的应用
      contents.apply(context);
      return true;
    }
    return false;
  }

}
