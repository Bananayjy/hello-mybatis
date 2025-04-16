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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper 方法。在 Mapper 接口中，每个定义的方法，对应一个 MapperMethod 对象
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  // SqlCommand 对象
  private final SqlCommand command;
  // MethodSignature 对象
  private final MethodSignature method;

  // 构造器
  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  // 执行对应的操作
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
        // 转换参数
        Object param = method.convertArgsToSqlCommandParam(args);
        // 执行 INSERT 操作
        // 转换 rowCount
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        // 转换参数
        Object param = method.convertArgsToSqlCommandParam(args);
        // 执行更新
        // 转换 rowCount
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        // 转换参数
        Object param = method.convertArgsToSqlCommandParam(args);
        // 转换 rowCount
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        // 无返回，并且有 ResultHandler 方法参数，则将查询的结果，提交给 ResultHandler 进行处理
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) { // 执行查询，返回列表
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) { // 执行查询，返回 Map
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) { // 执行查询，返回 Cursor
          result = executeForCursor(sqlSession, args);
        } else { // 执行查询，返回单个对象
          // 转换参数（入参映射关系）
          Object param = method.convertArgsToSqlCommandParam(args);
          // 查询单条
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional() && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        // 刷入批处理
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    // 返回结果为 null ，并且返回类型为基本类型，则抛出 BindingException 异常
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + "' attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  // 将返回的行变更数，转换成方法实际要返回的类型
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) { // Void 情况，不用返回
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) { // Int
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) { // Long
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) { // Boolean
      result = rowCount > 0;
    } else {
      throw new BindingException(
          "Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  // 将查询的结果，提交给 ResultHandler 进行处理
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    // 获得 MappedStatement 对象
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException(
          "method " + command.getName() + " needs either a @ResultMap annotation, a @ResultType annotation,"
              + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    // 转换参数
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行 SELECT 操作
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  // 执行查询，返回列表
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    // 转换参数
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行 SELECT 操作
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    // 封装 Array 或 Collection 结果
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      }
      return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
    }
    // 直接返回的结果
    return result;
  }

  // 执行查询，返回Cursor
  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    // 转换参数
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行 SELECT 操作
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (!arrayComponentType.isPrimitive()) {
      return list.toArray((E[]) array);
    }
    for (int i = 0; i < list.size(); i++) {
      Array.set(array, i, list.get(i));
    }
    return array;
  }

  // 执行查询，返回 Map
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    // 转换参数
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行 SELECT 操作
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  // MapperMethod的静态内部类，存放SQL命令
  public static class SqlCommand {

    // {@link MappedStatement#getId()}
    private final String name;

    // SQL 命令类型
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 获取方法名称
      final String methodName = method.getName();
      // 获取方法类对象
      final Class<?> declaringClass = method.getDeclaringClass();
      // 获得 MappedStatement 对象
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass, configuration);
      if (ms == null) { // 未找到对应MappedStatement 对象，说明该方法上，没有对应的 SQL 声明
        if (method.getAnnotation(Flush.class) == null) { // 抛出 BindingException 异常，如果找不到 MappedStatement
          throw new BindingException(
              "Invalid bound statement (not found): " + mapperInterface.getName() + "." + methodName);
        }
        // 如果有 @Flush 注解，则标记为 FLUSH 类型，说明该方法是用于执行 flush 操作
        name = null;
        type = SqlCommandType.FLUSH;
      } else {
        // 设置 name（MappedStatement的id）
        // 对应 MappedStatement#getId() 方法获得的标识。实际上，就是 ${NAMESPACE_NAME}.${语句_ID}，
        // 例如："org.apache.ibatis.autoconstructor.AutoConstructorMapper.getSubject2"
        name = ms.getId();
        // 设置 type （当前SQL操作类型）
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) { // 如果是 UNKNOWN 类型，抛出 BindingException 异常
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    // 获取name成员变量，即MappedStatement#getId()
    public String getName() {
      return name;
    }

    // 获取SQL 命令类型
    public SqlCommandType getType() {
      return type;
    }

    // 获得 MappedStatement 对象
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName, Class<?> declaringClass,
        Configuration configuration) {
      // 获取 MappedStatement的id ，即 ${NAMESPACE_NAME}.${语句_ID}
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {  // 如果有，获得 MappedStatement 对象，并返回
        // Configuration 里缓存了所有的 MappedStatement ，并且每一个 XML 里声明的例如 <select /> 或者 <update /> 等等，都对应一个 MappedStatement 对象
        return configuration.getMappedStatement(statementId);
      }
      if (mapperInterface.equals(declaringClass)) { // 如果没有，并且当前方法就是 declaringClass 声明的，则说明真的找不到（不用向上找了）
        return null;
      }
      // 遍历父接口，递归父接口获得 MappedStatement 对象（该方法定义在父接口中）
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName, declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  // MapperMethod的静态内部类，存放方法签名
  public static class MethodSignature {

    // 返回类型是否为集合
    private final boolean returnsMany;
    // 返回类型是否为 Map
    private final boolean returnsMap;
    // 返回类型是否为 void
    private final boolean returnsVoid;
    // 返回类型是否为 Cursor
    private final boolean returnsCursor;
    // 返回类型是否为 Optional
    private final boolean returnsOptional;
    // 返回类型
    private final Class<?> returnType;
    // 返回方法上的 {@link MapKey#value()} ，前提是返回类型为 Map
    private final String mapKey;
    // 获得 {@link ResultHandler} 在方法参数中的位置（如果为 null ，说明不存在这个类型）
    private final Integer resultHandlerIndex;
    // 获得 {@link RowBounds} 在方法参数中的位置（如果为 null ，说明不存在这个类型）
    private final Integer rowBoundsIndex;
    // ParamNameResolver 对象
    private final ParamNameResolver paramNameResolver;

    // 构造器
    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 初始化 returnType 属性
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) { // 普通类
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) { // 泛型
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else { // 内部类等等
        this.returnType = method.getReturnType();
      }
      // 初始化 returnsVoid 属性：判断返回类似是否为void
      this.returnsVoid = void.class.equals(this.returnType);
      // 初始化 returnsMany 属性
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      // 初始化 returnsCursor 属性
      this.returnsCursor = Cursor.class.equals(this.returnType);
      // 初始化 returnsOptional 属性
      this.returnsOptional = Optional.class.equals(this.returnType);
      // 获得注解的 @MapKey的value()值，并初始化mapKey
      this.mapKey = getMapKey(method);
      // 初始化 returnsMap 属性
      this.returnsMap = this.mapKey != null;
      // 初始化rowBoundsIndex：获取RowBounds在方法参数中的位置（如果为 null ，说明不存在这个类型）
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
     // 初始化ResultHandler：获得ResultHandler}在方法参数中的位置（如果为 null ，说明不存在这个类型）
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      // 初始化 ParamNameResolver 对象
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    // 获得 SQL 通用参数映射
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     *
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    // 获得指定参数类型在方法参数中的位置
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      // 遍历方法参数
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) { // 类型符合
          if (index != null) { // 如果重复类型了，则抛出 BindingException 异常
            throw new BindingException(
                method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
          // 获得第一次的位置
          index = i;
        }
      }
      // 返回位置
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    // 获得注解的 {@link MapKey#value()}
    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) { // 返回类型需要满足为 Map
        // 使用 @MapKey 注解
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          // 获得 @MapKey 注解的键
          mapKey = mapKeyAnnotation.value();
        }
      }
      // 返回@MapKey 注解的键
      return mapKey;
    }
  }

}
