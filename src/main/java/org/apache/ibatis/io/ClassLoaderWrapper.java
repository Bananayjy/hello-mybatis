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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * A class to wrap access to multiple class loaders making them work as one
 * ClassLoader 包装器 用于包装对多个类加载器的访问，使它们作为一个类工作
 * 可使用多个 ClassLoader 加载对应的资源，直到有一成功后返回资源。
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

  // 默认ClassLoader对象
  // 目前不存在初始化该属性的构造方法。可通过 ClassLoaderWrapper.defaultClassLoader = xxx 的方式，进行设置
  ClassLoader defaultClassLoader;
  // 系统ClassLoader对象
  ClassLoader systemClassLoader;

  // 无参构造器（初始化系统 ClassLoader 对象）
  ClassLoaderWrapper() {
    try {
      // 为systemClassLoader对象设置当前默认类加载器对象的系统类加载器
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
      // AccessControlException on Google App Engine
    }
  }

  /**
   * Get a resource as a URL using the current class path
   * 使用当前类路径获取资源作为URL
   *
   * @param resource
   *          - the resource to locate
   *
   * @return the resource or null
   */
  public URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /**
   * Get a resource from the classpath, starting with a specific class loader
   * 从特定的类装入器开始，从类路径获取资源
   *
   * @param resource
   *          - the resource to find
   * @param classLoader
   *          - the first classloader to try
   *
   * @return the stream or null
   */
  public URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /**
   * Get a resource from the classpath
   * 获得指定资源的 InputStream 对象
   * @param resource
   *          - the resource to find
   *
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /**
   * Get a resource from the classpath, starting with a specific class loader
   * 从特定的类装入器开始，从类路径获取资源
   * @param resource
   *          - the resource to find
   * @param classLoader
   *          - the first class loader to try
   *
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /**
   * Find a class on the classpath (or die trying)
   * 在类路径上找到一个类（否则尝试失败）
   *
   * @param name
   *          - the class to look for
   *
   * @return - the class
   *
   * @throws ClassNotFoundException
   *           Duh.
   */
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /**
   * Find a class on the classpath, starting with a specific classloader (or die trying)
   * 在类路径上找到一个类，从一个特定的类装入器开始（或者尝试一下）
   *
   * @param name
   *          - the class to look for
   * @param classLoader
   *          - the first classloader to try
   *
   * @return - the class
   *
   * @throws ClassNotFoundException
   *           Duh.
   */
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /**
   * Try to get a resource from a group of classloaders
   * 尝试从一组类加载器中获取资源
   *
   * @param resource
   *          - the resource to get
   * @param classLoader
   *          - the classloaders to examine
   *
   * @return the resource or null
   */
  InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
// 遍历 ClassLoader 数组
    for (ClassLoader cl : classLoader) {
      if (null != cl) {

        // 获得 InputStream ，不带 /
        // try to find the resource as passed
        InputStream returnValue = cl.getResourceAsStream(resource);

        // 获得 InputStream ，带 /
        // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
        if (null == returnValue) {
          returnValue = cl.getResourceAsStream("/" + resource);
        }

        // 成功获得到，返回
        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /**
   * Get a resource as a URL using the current class path
   * 使用当前类路径获取资源作为URL
   *
   * @param resource
   *          - the resource to locate
   * @param classLoader
   *          - the class loaders to examine
   *
   * @return the resource or null
   */
  URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

    // 声明出参
    URL url;

    // 遍历类加载器对象数组
    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        // 获得 URL ，不带 /
        // look for the resource as passed in...
        url = cl.getResource(resource);

        // 获得 URL ，带 /
        // ...but some class loaders want this leading "/", so we'll add it
        // and try again if we didn't find the resource
        if (null == url) {
          url = cl.getResource("/" + resource);
        }

        // 成功获得到，返回url
        // "It's always in the last place I look for it!"
        // ... because only an idiot would keep looking for it after finding it, so stop looking already.
        if (null != url) {
          return url;
        }

      }

    }

    // didn't find it anywhere.
    return null;

  }

  /**
   * Attempt to load a class from a group of classloaders
   * 获得指定类名对应的类
   *
   * @param name
   *          - the class to load
   * @param classLoader
   *          - the group of classloaders to examine
   *
   * @return the class
   *
   * @throws ClassNotFoundException
   *           - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
   */
  Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {
    // 遍历 ClassLoader 数组
    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        try {
          // 获得类
          return Class.forName(name, true, cl);

        } catch (ClassNotFoundException e) {
          // we'll ignore this until all classloaders fail to locate the class
        }

      }

    }

    // 获取不到 抛出异常
    throw new ClassNotFoundException("Cannot find class: " + name);

  }

  // 获取ClassLoader数组
  ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    // 返回一个类加载器对象数组，按顺序如下所示
    // 1.参数传入的类加载器：通常表示当前操作特定的或业务相关的类加载器
    // 2.默认类加载器
    // 3.当前线程上下文类加载器：常用于解决类加载器委托机制导致的问题(父加载器无法加载子加载器可见的类)
    // 4.当前类对象的类加载器：在标准Java应用中通常是AppClassLoader，在Web应用中可能是WebappClassLoader
    // 5.系统类加载器
    return new ClassLoader[] { classLoader, defaultClassLoader, Thread.currentThread().getContextClassLoader(),
        getClass().getClassLoader(), systemClassLoader };
  }

}
