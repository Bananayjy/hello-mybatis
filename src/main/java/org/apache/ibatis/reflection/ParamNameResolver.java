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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  // 通过静态代码块初始化后：param0、param1、param2 ……
  public static final String[] GENERIC_NAME_CACHE = new String[10];

  static {
    for (int i = 0; i < 10; i++) {
      GENERIC_NAME_CACHE[i] = GENERIC_NAME_PREFIX + (i + 1);
    }
  }

  // 是否使用实际的参数名称
  private final boolean useActualParamName;

  // 参数名映射  key：参数顺序  value：参数名
  /**
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified, the parameter index is
   * used. Note that this index could be different from the actual index when the method has special parameters (i.e.
   * {@link RowBounds} or {@link ResultHandler}).
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  // 是否有@Param注解
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    // 从配置文件中读取参数，是否使用实际的参数名称
    this.useActualParamName = config.isUseActualParamName();
    // 获取当前方法的参数类型，并作为一个数组返回
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取当前所有参数的注解信息（二维，一维表示哪一个参数，二维表示有哪些注释）
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    // 声明一个TreeMap，用于参访参数顺序（key）和参数名称（value）
    final SortedMap<Integer, String> map = new TreeMap<>();
    // 获取当前参数属性
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 忽略，如果是特殊参数
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) { // 首先，从 @Param 注解中获取参数
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          // 获取名称
          name = ((Param) annotation).value();
          break;
        }
      }
      if (name == null) {
        // @Param was not specified.
        if (useActualParamName) { // 其次，获取真实的参数名（如果在配置文件中开启了使用真实参数名称）
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {  // 最差，使用 map 的顺序，作为编号
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      // 添加到 map 中
      map.put(paramIndex, name);
    }
    // 构建不可变集合
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * A single non-special parameter is returned without a name. Multiple parameters are named using the naming rule. In
   * addition to the default names, this method also adds the generic names (param1, param2, ...).
   * 返回一个没有名称的非特殊参数。多个参数使用命名规则命名。除了默认名称之外，该方法还添加了泛型名称（param1, param2，…）
   *
   * @param args
   *          the args
   *
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    // 获取参数数量
    final int paramCount = names.size();
    if (args == null || paramCount == 0) { // 无参数，则返回 null
      return null;
    }
    if (!hasParamAnnotation && paramCount == 1) { // 只有一个非注解的参数，直接返回首元素
      Object value = args[names.firstKey()];
      return wrapToMapIfCollection(value, useActualParamName ? names.get(names.firstKey()) : null);
    } else {
      // 集合。
      // 组合 1 ：KEY：参数名（names中存储的参数名），VALUE：参数值
      // 组合 2 ：KEY：GENERIC_NAME_PREFIX + 参数顺序，VALUE ：参数值
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) { // 遍历 names 集合
        // 组合 1 ：添加到 param 中
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 组合 2 ：添加到 param 中
        final String genericParamName = i < 10 ? GENERIC_NAME_CACHE[i] : GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object
   *          a parameter object
   * @param actualParamName
   *          an actual parameter name (If specify a name, set an object to {@link ParamMap} with specified name)
   *
   * @return a {@link ParamMap}
   *
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    if (object instanceof Collection) { // 如果是集合，则添加到 collection 中
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    if (object != null && object.getClass().isArray()) { // 如果是 Array ，则添加到 array 中
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

}
