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
package org.apache.ibatis.builder.xml;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * Statement XML 配置构建器，继承 BaseBuilder 抽象类
 * 加载Statement配置，主要负责解析 Statement 配置，即 <select />、<insert />、<update />、<delete /> 标签
 *
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  private final MapperBuilderAssistant builderAssistant;
  // 当前 XML 节点，例如：<select />、<insert />、<update />、<delete /> 标签
  private final XNode context;
  // 要求的 databaseId
  private final String requiredDatabaseId;

  // 构造器
  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context,
      String databaseId) {
    // 必维护的Configuration对象
    super(configuration);
    // Mapper 构造器助手
    this.builderAssistant = builderAssistant;
    // 当前 XML 节点
    this.context = context;
    // 当前需要的 databaseId
    this.requiredDatabaseId = databaseId;
  }

  // 执行 Statement 解析
  /**
   * 示例：
   *  <select id="selectAllAuthorsSet" resultType="org.apache.ibatis.domain.blog.Author">
   *         select * from author
   *     </select>
   */
  public void parseStatementNode() {
    // 获得 id 属性，编号，如:selectAllAuthorsSet
    String id = context.getStringAttribute("id");
    // 获取指定的数据库id 如:databaseId="mysql"
    String databaseId = context.getStringAttribute("databaseId");

    // 判断 databaseId 是否匹配
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    // 获得标签中各种属性
    // 当前节点的名称，如selet、update、delete、insert
    String nodeName = context.getNode().getNodeName();
    // 当前节点操作对应的枚举类
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    // 当前操作是否是isSelect
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    // 刷新缓存实现类
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    // 使用缓存实现类
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    // 结果集是否排序
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // 创建 XMLIncludeTransformer 对象，并替换 <include /> 标签相关的内容
    // Include Fragments before parsing
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // 获取参数映射的类型
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);

    // 获得 lang 对应的 LanguageDriver 对象
    String lang = context.getStringAttribute("lang");
    LanguageDriver langDriver = getLanguageDriver(lang);

    // 解析 <selectKey /> 标签
    // Parse selectKey after includes and remove them.
    processSelectKeyNodes(id, parameterTypeClass, langDriver);

    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    // 获得 KeyGenerator 对象
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {  // 从 configuration 中获得 KeyGenerator 对象。如果存在，意味着是 <selectKey /> 标签配置的
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else { // 其次，根据标签属性的情况，判断是否使用对应的 Jdbc3KeyGenerator 或者 NoKeyGenerator 对象
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
              ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    // 创建 SqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    StatementType statementType = StatementType
        .valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    Integer fetchSize = context.getIntAttribute("fetchSize");
    Integer timeout = context.getIntAttribute("timeout");
    String parameterMap = context.getStringAttribute("parameterMap");
    String resultType = context.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    String resultMap = context.getStringAttribute("resultMap");
    if (resultTypeClass == null && resultMap == null) {
      resultTypeClass = MapperAnnotationBuilder.getMethodReturnType(builderAssistant.getCurrentNamespace(), id);
    }
    String resultSetType = context.getStringAttribute("resultSetType");
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);
    if (resultSetTypeEnum == null) {
      resultSetTypeEnum = configuration.getDefaultResultSetType();
    }
    String keyProperty = context.getStringAttribute("keyProperty");
    String keyColumn = context.getStringAttribute("keyColumn");
    String resultSets = context.getStringAttribute("resultSets");
    boolean dirtySelect = context.getBooleanAttribute("affectData", Boolean.FALSE);

    // 创建 MappedStatement 对象，并加入configuration配置对象的MappedStatement变量中
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap,
        parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets, dirtySelect);
  }

  // 解析 <selectKey /> 标签
  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    // 获得 <selectKey /> 节点们
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    // 执行解析 <selectKey /> 节点们
    if (configuration.getDatabaseId() != null) {
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    //  移除 <selectKey /> 节点们
    removeSelectKeyNodes(selectKeyNodes);
  }

  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass,
      LanguageDriver langDriver, String skRequiredDatabaseId) {
    // 遍历 <selectKey /> 节点们
    for (XNode nodeToHandle : list) {
      // 获得完整 id ，格式为 `${id}!selectKey`
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      //  获得 databaseId ， 判断 databaseId 是否匹配
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        // 执行解析单个 <selectKey /> 节点
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  // 执行解析单个 <selectKey /> 节点
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver,
      String databaseId) {
    // 获得各种属性和对应的类
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    StatementType statementType = StatementType
        .valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    // 创建 MappedStatement 需要用到的默认值
    // defaults
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    // 创建 SqlSource 对象
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    // 创建 MappedStatement 对象
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap,
        parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null, false);

    // 获得 SelectKeyGenerator 的编号，格式为 `${namespace}.${id}`
    id = builderAssistant.applyCurrentNamespace(id, false);
    // 获得 MappedStatement 对象
    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    // 创建 SelectKeyGenerator 对象，并添加到 configuration 中
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    // 如果需要的id 不为null，则与数据库id进行比较
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    // 说明requiredDatabaseId 不等于 databaseId
    if (databaseId != null) {
      return false;
    }
    // requiredDatabaseId和databaseId都等于null的时候，判断配置对象中是否已经存在当前标签
    id = builderAssistant.applyCurrentNamespace(id, false);
    if (!this.configuration.hasStatement(id, false)) { // 配置对象中不存在当前标签，直接返回true，则匹配成功
      return true;
    }
    // skip this statement if there is a previous one with a not null databaseId
    // 如果前面存在非空databaseId语句，则跳过此语句
    MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
    // 若存在，则判断原有的 标签 是否 databaseId 为空。因为，当前 databaseId 为空，这样两者才能匹配
    return previous.getDatabaseId() == null;
  }

  // 获得 lang 对应的 LanguageDriver 对象
  private LanguageDriver getLanguageDriver(String lang) {
    // 解析获取 lang 对应的类
    Class<? extends LanguageDriver> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    // 获取 LanguageDriver 对象
    return configuration.getLanguageDriver(langClass);
  }

}
