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
package org.apache.ibatis.scripting;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.util.MapUtil;

/**
 * LanguageDriver 注册表
 * @author Frank D. Martinez [mnesarco]
 */
public class LanguageDriverRegistry {

  // LanguageDriver 映射对象
  private final Map<Class<? extends LanguageDriver>, LanguageDriver> languageDriverMap = new HashMap<>();

  // 默认的 LanguageDriver 类对象
  private Class<? extends LanguageDriver> defaultDriverClass;

  // 根据cls类对象创建LanguageDriver对象，并添加到LanguageDriver 映射对象中
  public void register(Class<? extends LanguageDriver> cls) {
    // cls为null，抛出异常
    if (cls == null) {
      throw new IllegalArgumentException("null is not a valid Language Driver");
    }
    // 创建 cls 对应的对象，并添加到 LANGUAGE_DRIVER_MAP 中
    MapUtil.computeIfAbsent(languageDriverMap, cls, k -> {
      try {
        return k.getDeclaredConstructor().newInstance();
      } catch (Exception ex) {
        throw new ScriptingException("Failed to load language driver for " + cls.getName(), ex);
      }
    });
  }

  // 将LanguageDriver对象instance，添加到LanguageDriver 映射中
  public void register(LanguageDriver instance) {
    if (instance == null) {
      throw new IllegalArgumentException("null is not a valid Language Driver");
    }
    // 添加到 LANGUAGE_DRIVER_MAP 中
    Class<? extends LanguageDriver> cls = instance.getClass();
    if (!languageDriverMap.containsKey(cls)) {
      languageDriverMap.put(cls, instance);
    }
  }

  // 根据cls类对象，从 LanguageDriver 映射中获取指定的LanguageDriver
  public LanguageDriver getDriver(Class<? extends LanguageDriver> cls) {
    return languageDriverMap.get(cls);
  }

  // 获取默认的 LanguageDriver 对类象
  public LanguageDriver getDefaultDriver() {
    return getDriver(getDefaultDriverClass());
  }

  // 获取默认的 LanguageDriver 对类象
  public Class<? extends LanguageDriver> getDefaultDriverClass() {
    return defaultDriverClass;
  }

  // 设置默认的 LanguageDriver 类对象
  public void setDefaultDriverClass(Class<? extends LanguageDriver> defaultDriverClass) {
    register(defaultDriverClass);
    this.defaultDriverClass = defaultDriverClass;
  }

}
