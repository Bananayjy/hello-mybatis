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
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * XMLConfigBuilder继承BaseBuilder抽象类，用于解析XML配置文件，并构建Configuration对象。
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  // 是否已经解析
  private boolean parsed;

  // 基于 Java XPath 的解析器
  private final XPathParser parser;

  // 环境
  private String environment;

  // ReflectorFactory 对象（反射对象工厂）
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();


  // 构造器
  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(Configuration.class, reader, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, Reader reader, String environment,
      Properties props) {
    this(configClass, new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(Configuration.class, inputStream, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, InputStream inputStream, String environment,
      Properties props) {
    this(configClass, new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(Class<? extends Configuration> configClass, XPathParser parser, String environment,
      Properties props) {
    // 创建 Configuration 对象
    super(newConfig(configClass));
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 设置 Configuration 的 variables 属性
    this.configuration.setVariables(props);
    // 设置当前状态为未解析
    this.parsed = false;
    // 环境变量赋值
    this.environment = environment;
    // 解析器
    this.parser = parser;
  }


  // 解析 mybatis-config.xml 成 Configuration 对象
  public Configuration parse() {
    if (parsed) { // 如果已经解析过了，就抛出 BuilderException 异常
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 标记已经解析
    parsed = true;
    // 调用 XPathParser#evalNode(String expression) 方法，获得 XML <configuration /> 节点
    // 调用 #parseConfiguration(XNode root) 方法，解析该节点
    parseConfiguration(parser.evalNode("/configuration"));
    // 返回configuration对象
    return configuration;
  }

  // 解析 <configuration /> 节点
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 解析 <properties /> 标签
      propertiesElement(root.evalNode("properties"));
      // 解析 <settings /> 标签
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 加载自定义 VFS 实现类
      loadCustomVfsImpl(settings);
      // 加载自定义Log实现类
      loadCustomLogImpl(settings);
      // 解析 <typeAliases /> 标签
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析 <plugins /> 标签
      pluginsElement(root.evalNode("plugins"));
      // 解析 <objectFactory /> 标签
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 <objectWrapperFactory /> 标签
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 <reflectorFactory /> 标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 赋值 <settings /> 到 Configuration 属性
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析 <environments /> 标签
      environmentsElement(root.evalNode("environments"));
      // 解析 <databaseIdProvider /> 标签
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析 <typeHandlers /> 标签
      typeHandlersElement(root.evalNode("typeHandlers"));
      // 解析 <mappers /> 标签
      mappersElement(root.evalNode("mappers"));
    } catch (Exception e) { // 解析 mybatis-config.xml 失败，抛出 BuilderException 异常
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  // 将 <setting /> 标签解析为 Properties 对象
  private Properties settingsAsProperties(XNode context) {
    // 将子标签，解析成 Properties 对象
    if (context == null) {
      return new Properties();
    }
    // 读取子标签们，为 Properties 对象
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 为Configuration创建元数据对象
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      // 校验每个属性，在 Configuration 中，有相应的 setter 方法，否则抛出 BuilderException 异常
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException(
            "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    // 返回 Properties 对象
    return props;
  }

  // 加载自定义 VFS 实现类
  private void loadCustomVfsImpl(Properties props) throws ClassNotFoundException {
    // 获得 vfsImpl 属性
    String value = props.getProperty("vfsImpl");
    if (value == null) {  // 如果vfsImpl属性为null，直接返回
      return;
    }
    // 使用 , 作为分隔符，拆成 VFS 类名的数组
    String[] clazzes = value.split(",");
    for (String clazz : clazzes) {
      if (!clazz.isEmpty()) {
        @SuppressWarnings("unchecked")
          // 通过反射创建 VFS 的类对象
        Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
        // 设置到 Configuration 中
        configuration.setVfsImpl(vfsImpl);
      }
    }
  }

  // 加载自定义Log实现类
  private void loadCustomLogImpl(Properties props) {
    // 从 <settings /> 标签中获取
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  // 解析 <typeAliases /> 标签，将配置类注册到 typeAliasRegistry 中
  private void typeAliasesElement(XNode context) {
    // 如果 <typeAliases /> 标签为 null，直接返回
    if (context == null) {
      return;
    }
    // 遍历子节点
    for (XNode child : context.getChildren()) {
      if ("package".equals(child.getName())) {  // 指定为包的情况下，注册包下的每个类
        // 获取包名
        String typeAliasPackage = child.getStringAttribute("name");
        // 注册包下的每个类
        configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
      } else { // 指定为类的情况下，直接注册类和别名
        String alias = child.getStringAttribute("alias");
        String type = child.getStringAttribute("type");
        try {
          // 根据type获取类对象
          Class<?> clazz = Resources.classForName(type);
          if (alias == null) {  // 别名alias为null的情况注册（默认使用当前type类对象的SimpleName值，优先使用aliasAnnotation中的值）
            typeAliasRegistry.registerAlias(clazz);
          } else {  // 别名不为null的情况注册
            typeAliasRegistry.registerAlias(alias, clazz);
          }
        } catch (ClassNotFoundException e) {  // 若类不存在，则抛出 BuilderException 异常
          throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
        }
      }
    }
  }

  // 解析 <plugins /> 标签，添加到 Configuration#interceptorChain 中
  private void pluginsElement(XNode context) throws Exception {
    if (context != null) {
      // 遍历子标签
      for (XNode child : context.getChildren()) {
        // 获取配置的插件信息
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        // 创建 Interceptor 对象，并设置属性
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor()
            .newInstance();
        interceptorInstance.setProperties(properties);
        // 将插件添加到 configuration 维护的 interceptorChain 对象中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  // 解析 <objectFactory /> 节点
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 ObjectFactory 的实现类（全限定名称）
      String type = context.getStringAttribute("type");
      // 获得 Properties 属性
      Properties properties = context.getChildrenAsProperties();
      // 创建 ObjectFactory 对象，并设置 Properties 属性
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      // 设置 Configuration 的 objectFactory 属性
      configuration.setObjectFactory(factory);
    }
  }

  // 解析 <objectWrapperFactory /> 节点
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 objectWrapperFactory 实现类
      String type = context.getStringAttribute("type");
      // 创建 ObjectWrapperFactory 对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置 Configuration 的 objectWrapperFactory 属性
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 ReflectorFactory 的实现类
      String type = context.getStringAttribute("type");
      // 创建 ReflectorFactory 对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置 Configuration 的 reflectorFactory 属性
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析 <properties /> 节点。逻辑：
   * 1.解析 <properties /> 标签，成 Properties 对象。
   * 2，将configuration 中的 Properties 对象内容添加到 Properties 对象结果中。
   * 3。将Properties 对象设置到 parser 和 configuration 中。
   * @param context <properties /> 节点
   * @throws Exception 异常
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context == null) {  // <properties /> 节点为null，则直接返回
      return;
    }
    // 读取子标签们，为 Properties 对象
    Properties defaults = context.getChildrenAsProperties();
    // 读取 resource 和 url 属性
    String resource = context.getStringAttribute("resource");
    String url = context.getStringAttribute("url");
    // resource 和 url 都存在的情况下，抛出 BuilderException 异常
    if (resource != null && url != null) {
      throw new BuilderException(
          "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
    }
    // 读取本地 Properties 配置文件到 defaults 中。
    if (resource != null) {
      // 调用Resources获取resource资源中的信息，并封装成Properties对象
      defaults.putAll(Resources.getResourceAsProperties(resource));
    } else if (url != null) {
      // 调用Resources获取url资源中的信息，并封装成Properties对象
      defaults.putAll(Resources.getUrlAsProperties(url));
    }
    // 将configuration 中的 Properties 对象到 defaults 中
    Properties vars = configuration.getVariables();
    if (vars != null) {
      defaults.putAll(vars);
    }
    // 设置 defaults 到 parser 和 configuration 中
    parser.setVariables(defaults);
    configuration.setVariables(defaults);
  }

  // 赋值 <settings /> 到 Configuration 属性
  private void settingsElement(Properties props) {
    configuration
        .setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(
        AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(
        stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setArgNameBasedConstructorAutoMapping(
        booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context == null) {  // environments节点为空，则直接返回
      return;
    }
    // environment 属性为空，从 default 属性获得并赋值
    if (environment == null) {
      environment = context.getStringAttribute("default");
    }
    // 遍历Xnode子节点，获取对应的 environment 配置
    for (XNode child : context.getChildren()) {
      // 获取 id 属性
      String id = child.getStringAttribute("id");
      if (isSpecifiedEnvironment(id)) { // 判断当前environment 是否和id匹配
        // 解析 `<transactionManager />` 标签，返回 TransactionFactory 对象
        TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
        // 解析 `<dataSource />` 标签，返回 DataSourceFactory 对象
        DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
        // 通过 DataSourceFactory 对象 获取 DataSource 对象
        DataSource dataSource = dsFactory.getDataSource();
        // 创建 Environment.Builder 对象
        Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory)
            .dataSource(dataSource);
        // 构造 Environment 对象，并设置到 configuration 中
        configuration.setEnvironment(environmentBuilder.build());
        break;
      }
    }
  }

  // 解析 <databaseIdProvider /> 标签
  // 参考文章：https://blog.csdn.net/zhou920786312/article/details/109140248
  private void databaseIdProviderElement(XNode context) throws Exception {
    // <databaseIdProvider /> 标签 为 null，直接返回
    if (context == null) {
      return;
    }
    // 获得 DatabaseIdProvider 的类
    String type = context.getStringAttribute("type");
    // awful patch to keep backward compatibility
    if ("VENDOR".equals(type)) {
      type = "DB_VENDOR";
    }
    // 获取<databaseIdProvider /> 标签下的内容，并封装成一个 Properties 对象
    Properties properties = context.getChildrenAsProperties();
    // 创建 DatabaseIdProvider 对象，并设置对应的属性
    DatabaseIdProvider databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor()
        .newInstance();
    databaseIdProvider.setProperties(properties);
    // 获取当前环境对象 Environment
    Environment environment = configuration.getEnvironment();
    if (environment != null) {
      // 获得对应的 databaseId 编号
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      // 设置到 configuration 中
      configuration.setDatabaseId(databaseId);
    }
  }

  // 解析 <transactionManager /> 标签，返回 TransactionFactory 对象
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 TransactionFactory 的类
      String type = context.getStringAttribute("type");
      // 获得 Properties 属性
      Properties props = context.getChildrenAsProperties();
      // 创建 TransactionFactory 对象，并设置属性
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  // 解析 <dataSource /> 标签，返回 DataSourceFactory 对象
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 DataSourceFactory 的类
      String type = context.getStringAttribute("type");
      // 获得 Properties 属性
      Properties props = context.getChildrenAsProperties();
      // 创建 DataSourceFactory 对象，并设置属性
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      // 返回 DataSourceFactory 对象
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  // 解析 <typeHandlers /> 标签
  private void typeHandlersElement(XNode context) {
    if (context == null) { // <typeHandlers /> 标签为 null，直接返回
      return;
    }
    // 遍历子节点
    for (XNode child : context.getChildren()) {
      if ("package".equals(child.getName())) {  // 如果是 package 标签，则扫描该包
        String typeHandlerPackage = child.getStringAttribute("name");
        typeHandlerRegistry.register(typeHandlerPackage);
      } else {  // 如果是 typeHandler 标签，则注册该 typeHandler 信息
        // 获得 javaType、jdbcType、handler
        String javaTypeName = child.getStringAttribute("javaType");
        String jdbcTypeName = child.getStringAttribute("jdbcType");
        String handlerTypeName = child.getStringAttribute("handler");
        Class<?> javaTypeClass = resolveClass(javaTypeName);
        JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
        Class<?> typeHandlerClass = resolveClass(handlerTypeName);
        // 注册 typeHandler
        if (javaTypeClass != null) {
          if (jdbcType == null) {
            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
          } else {
            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
          }
        } else {
          typeHandlerRegistry.register(typeHandlerClass);
        }
      }
    }
  }

  // 解析 <mappers /> 标签
  private void mappersElement(XNode context) throws Exception {
    if (context == null) {  // 解析 <mappers /> 标签为null，则直接返回
      return;
    }
    // 遍历子节点
    for (XNode child : context.getChildren()) {
      if ("package".equals(child.getName())) {  // 如果是 package 标签，则扫描该包，将包下对应的mapper接口都加入
        String mapperPackage = child.getStringAttribute("name");
        configuration.addMappers(mapperPackage);
      } else {  // 如果是 mapper 标签，
        // 获得 resource、url、class 属性
        String resource = child.getStringAttribute("resource");
        String url = child.getStringAttribute("url");
        String mapperClass = child.getStringAttribute("class");
        if (resource != null && url == null && mapperClass == null) { // 使用相对于类路径的资源引用
          ErrorContext.instance().resource(resource);
          try (InputStream inputStream = Resources.getResourceAsStream(resource)) { // 获得 resource 的 InputStream 对象
            // 创建 XMLMapperBuilder 对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                configuration.getSqlFragments());
            // 执行解析
            mapperParser.parse();
          }
        } else if (resource == null && url != null && mapperClass == null) {  // 使用完全限定资源定位符（URL）
          ErrorContext.instance().resource(url);
          try (InputStream inputStream = Resources.getUrlAsStream(url)) { // 获得 url 的 InputStream 对象
            // 创建 XMLMapperBuilder 对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                configuration.getSqlFragments());
            // 执行解析
            mapperParser.parse();
          }
        } else if (resource == null && url == null && mapperClass != null) { // 使用映射器接口实现类的完全限定类名
          // 获得 Mapper 接口
          Class<?> mapperInterface = Resources.classForName(mapperClass);
          // 添加到 configuration 中
          configuration.addMapper(mapperInterface);
        } else { // 其他情况，抛出异常
          throw new BuilderException(
              "A mapper element may only specify a url, resource or class, but not more than one.");
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    // environment 或 id 为null，则抛出异常
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    // 判断当前environment是否与id匹配
    return environment.equals(id);
  }

  // 通过反射的方式，创建一个Configuration对象
  private static Configuration newConfig(Class<? extends Configuration> configClass) {
    try {
      return configClass.getDeclaredConstructor().newInstance();
    } catch (Exception ex) {
      throw new BuilderException("Failed to create a new Configuration instance.", ex);
    }
  }

}
