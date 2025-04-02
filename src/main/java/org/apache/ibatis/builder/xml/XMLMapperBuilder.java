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

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * XMLMapperBuilder 继承 BaseBuilder 抽象类
 * Mapper XML 配置构建器，主要负责解析 Mapper 映射配置文件
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  // 基于 Java XPath 解析器
  private final XPathParser parser;
  // Mapper 构造器助手
  // XMLMapperBuilder 和 MapperAnnotationBuilder 的小助手，提供了一些公用的方法，
  // 例如创建 ParameterMap、MappedStatement 对象等等
  private final MapperBuilderAssistant builderAssistant;
  // 可被其他语句引用的可重用语句块的集合
  // 例如：<sql id="userColumns"> ${alias}.id,${alias}.username,${alias}.password </sql>
  private final Map<String, XNode> sqlFragments;
  // 资源引用的地址，即Mapper映射配置文件的资源引用地址
  // eg：org/apache/ibatis/builder/AuthorMapper.xml
  private final String resource;

  // 构造器
  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments,
      String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()), configuration,
        resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()), configuration,
        resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments) {
    // 调用父类 BaseBuilder 构造器
    super(configuration);
    // 创建 MapperBuilderAssistant 对象
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    // 赋值变量 parser
    this.parser = parser;
    // 赋值变量 sqlFragments
    this.sqlFragments = sqlFragments;
    // 赋值 resource
    this.resource = resource;
  }

  public void parse() {
    // 判断当前 Mapper 是否已经加载过
    if (!configuration.isResourceLoaded(resource)) {
      // 解析 `<mapper />` 节点，解析后的相关内容，都会放置到 Configuration 配置对象中进行维护
      configurationElement(parser.evalNode("/mapper"));
      // 标记该 Mapper 已经加载过，即加入到配置对象configuration的属性loadedResources中
      configuration.addLoadedResource(resource);
      // 绑定 Mapper 接口（即将Mapper配置文件对应的接口也加入到配置对象维护的mapperRegistry对象中）
      bindMapperForNamespace();
    }

    /**
     * 如下三个解析方法，操作基本一致：
     * 1）获得对应的集合；2）遍历集合，执行解析；3）执行成功，则移除出集合；4）执行失败，根据入参reportUnresolved判断是否忽略异常
     */
    // 解析待定的 <resultMap /> 节点
    configuration.parsePendingResultMaps(false);
    // 解析待定的 <cache-ref /> 节点
    configuration.parsePendingCacheRefs(false);
    // 解析待定的 SQL 语句的节点
    configuration.parsePendingStatements(false);
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  // 解析 <mapper /> 节点
  // context就是对<mapper /> 节点 XML内容的一个封装
  // 解析后的相关内容，都会放置到 Configuration 配置对象中进行维护
  private void configurationElement(XNode context) {
    try {
      // 获取<mapper /> 节点的namespace属性
      String namespace = context.getStringAttribute("namespace");
      // 对namespace为空情况的处理
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // 设置 namespace 属性，设置到builderAssistant对象中
      builderAssistant.setCurrentNamespace(namespace);
      // 解析 <cache-ref /> 节点
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析 <cache /> 节点
      cacheElement(context.evalNode("cache"));
      // todo 已废弃！老式风格的参数映射。内联参数是首选,这个元素可能在将来被移除，这里不会记录
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析 <resultMap /> 节点们
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析 <sql /> 节点们
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析 <select /> <insert /> <update /> <delete /> 节点们
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  // 解析 <select />、<insert />、<update />、<delete /> 节点们
  // list：一个个 <select />、<insert />、<update />、<delete /> 对应的 XNode 对象的集合
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
    // 上面两块代码，可以简写成 buildStatementFromContext(list, configuration.getDatabaseId());
  }

  // 解析 <select />、<insert />、<update />、<delete /> 节点们
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // 遍历 <select /> <insert /> <update /> <delete /> 节点们
    for (XNode context : list) {
      // 创建 XMLStatementBuilder 对象，执行解析
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context,
          requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // 解析失败，添加到 configuration 中
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  // 解析 <cache-ref /> 节点
  // 示例：<cache-ref namespace="com.someone.application.data.SomeMapper"/>
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 获得指向的 namespace 名字，并添加到 configuration 的 cacheRefMap 中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // 创建 CacheRefResolver 对象
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant,
          context.getStringAttribute("namespace"));
      try {
        // 通过 CacheRefResolver 对象执行解析
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // 解析失败，添加到 configuration 的 incompleteCacheRefs 中
        // 因为此处指向的 Cache 对象可能未初始化
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  // 解析 <cache /> 节点

  /**
   * 示例：
   * // 使用默认缓存
   * <cache eviction="FIFO" flushInterval="60000"  size="512" readOnly="true"/>
   *
   * // 使用自定义缓存
   * <cache type="com.domain.something.MyCustomCache">
   *   <property name="cacheFile" value="/tmp/my-custom-cache.tmp"/>
   * </cache>
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      // 获得负责存储的 Cache 实现类（默认：PERPETUAL）
      String type = context.getStringAttribute("type", "PERPETUAL");
      // 通过typeAliasRegistry获取 Cache实现类的类对象（先尝试从typeAliases找，再尝试通过名称查找）
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // 获取负责过期的Cache实现类
      String eviction = context.getStringAttribute("eviction", "LRU");
      // 通过typeAliasRegistry获取 过期的Cache实现类的类对象（先尝试从typeAliases找，再尝试通过名称查找）
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // 获得 flushInterval、size、readWrite、blocking 属性
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      // 创建 Cache 对象
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property,
            javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  // 解析 <resultMap /> 节点列表
  private void resultMapElements(List<XNode> list) {
    // 遍历 <resultMap /> 节点们
    for (XNode resultMapNode : list) {
      try {
        // 处理单个 <resultMap /> 节点
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  // 解析 <resultMap /> 节点
  private ResultMap resultMapElement(XNode resultMapNode) {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  // 解析 <resultMap /> 节点
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings,
      Class<?> enclosingType) {
    // 获取 id 属性
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // 获取 type 属性（当前返回结果对象的全限定名称）
    String type = resultMapNode.getStringAttribute("type", resultMapNode.getStringAttribute("ofType",
        resultMapNode.getStringAttribute("resultType", resultMapNode.getStringAttribute("javaType"))));
    // 获得 type 的类对象
    Class<?> typeClass = resolveClass(type);
    // type 类对象为空时的处理
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    // 创建 ResultMapping 集合
    List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
    // 遍历 <resultMap /> 的子节点
    // 将每一个子节点解析后成一个或多个 ResultMapping 对象，并加入到集合 resultMappings 中
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) { // 处理 <constructor /> 节点
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) { // 处理 <discriminator /> 节点
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else { // 处理其它节点
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // 获取 <resultMap /> 节点的 id属性值，如果获取不到则调用resultMapNode的getValueBasedIdentifier方法
    String id = resultMapNode.getStringAttribute("id", resultMapNode::getValueBasedIdentifier);
    String extend = resultMapNode.getStringAttribute("extends");
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // 创建 ResultMapResolver 对象
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator,
        resultMappings, autoMapping);
    try {
      // 调用 resultMapResolver 的 resolve方法 获取 ResultMap 对象
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
      // 解析失败，添加到 configuration 的 incompleteResultMaps 中
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  // 解析 <resultMap /> 节点, 处理 <constructor /> 节点
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
    // 获取 <constructor /> 的子节点们
    List<XNode> argChildren = resultChild.getChildren();
    // 遍历 <constructor /> 的子节点们
    for (XNode argChild : argChildren) {
      // 获得 ResultFlag 集合
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      // 将当前子节点构建成 ResultMapping 对象，并添加到 resultMappings 中
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  // 解析 <resultMap /> 节点, 处理 <discriminator /> 节点
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType,
      List<ResultMapping> resultMappings) {
    // 解析各种属性
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    // 解析获取各类属性对应的类
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 遍历 <discriminator /> 的子节点，解析成 discriminatorMap 集合
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      // 如果是内嵌的 ResultMap 的情况，则调用 #processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) 方法，处理内嵌的 ResultMap 的情况
      String resultMap = caseChild.getStringAttribute("resultMap",
          () -> processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    // 创建 Discriminator 对象
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass,
        discriminatorMap);
  }

  // 解析 <sql /> 节点们
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
    // 上面两块代码，可以简写成 sqlElement(list, configuration.getDatabaseId());
  }

  // 解析 <sql /> 节点
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    // 遍历所有 <sql /> 节点
    for (XNode context : list) {
      // 获得 databaseId 属性
      String databaseId = context.getStringAttribute("databaseId");
      // 获取完整的 id 属性，格式为： ${namespace}.${id}
      String id = context.getStringAttribute("id");
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 判断 databaseId 是否匹配
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // 添加到 sqlFragments 中
        sqlFragments.put(id, context);
      }
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    // 如果requiredDatabaseId不为null，与databaseId进行比较
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    // 如果未设置 requiredDatabaseId ，但是 databaseId 存在，说明还是不匹配，则返回 false
    if (databaseId != null) {
      return false;
    }
    // 判断是否已经存在该id
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // sqlFragments已经存在当前id
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    // 若存在，则判断原有的 sqlFragment 是否 databaseId 为空。因为，当前 databaseId 为空，这样两者才能匹配
    return context.getStringAttribute("databaseId") == null;
  }

  // 将当前节点构建成 ResultMapping 对象
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
    // 获得各种属性
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        () -> processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy"
        .equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    // 获得各种属性对应的类
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 构建 ResultMapping 对象
    // 调用 MapperBuilderAssistant#buildResultMapping(...) 方法，构建 ResultMapping 对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect,
        nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  // 处理内嵌的 ResultMap
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings,
      Class<?> enclosingType) {
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
        && context.getStringAttribute("select") == null) {
      validateCollection(context, enclosingType);
      // “递归”调用处理内嵌的 ResultMap 的情况，并返回 ResultMap
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
            "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  // 绑定Mapper
  private void bindMapperForNamespace() {
    // 从MapperBuilderAssistant对象中获取当前命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      // 获得 Mapper 映射配置文件对应的 Mapper 接口，实际上类名就是 namespace
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        // ignore, bound type is not required
      }
      // 不存在该 Mapper 接口，则进行添加
      if (boundType != null && !configuration.hasMapper(boundType)) {
        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        // 标记 namespace 已经添加，避免 MapperAnnotationBuilder#loadXmlResource(...) 重复加载
        configuration.addLoadedResource("namespace:" + namespace);
        // 添加到 configuration 中
        configuration.addMapper(boundType);
      }
    }
  }

}
