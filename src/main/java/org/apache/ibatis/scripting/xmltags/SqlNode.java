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

/**
 * SQL Node 接口，每个 XML Node 会解析成对应的 SQL Node 对象
 * 是 MyBatis 中用于处理 动态 SQL 标签 的核心接口，它定义了所有动态 SQL 节点（如 <if>、<foreach>、<where> 等）的解析和执行行为。
 * 每个 XML 中的动态标签在解析后都会转换为对应的 SqlNode 实现类，最终组合成一棵动态 SQL 解析树
 * 通过调用apply方法对动态标签进行处理
 * @author Clinton Begin
 */
public interface SqlNode {
  /**
   * 应用当前 SQL Node 节点，即处理动态SQL标签
   * @param context context 上下文
   * @return 当前 SQL Node 节点是否应用成功。
   */
  boolean apply(DynamicContext context);
}
