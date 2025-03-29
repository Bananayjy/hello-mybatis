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
package org.apache.ibatis.io;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * Provides a very simple API for accessing resources within an application server.
 * 提供一个非常简单的API，用于访问应用服务器中的资源
 * 虚拟文件系统( Virtual File System )抽象类，用来查找指定路径下的的文件们
 *
 * @author Ben Gunter
 */
public abstract class VFS {
  private static final Log log = LogFactory.getLog(VFS.class);

  /** The built-in implementations. */
  // 内置的 VFS 实现类的数组，目前 VFS 有 JBoss6VFS 和 DefaultVFS 两个实现
  private static final Class<?>[] IMPLEMENTATIONS = { JBoss6VFS.class, DefaultVFS.class };

  /**
   * The list to which implementations are added by {@link #addImplClass(Class)}.
   */
  // 自定义的 VFS 实现类的数组，可通过 {@link #addImplClass(Class)} 方法，进行添加。
  private static final List<Class<? extends VFS>> USER_IMPLEMENTATIONS = new ArrayList<>();

  /** Singleton instance holder. */
  private static class VFSHolder {
    // 懒汉式，线程安全的单例
    static final VFS INSTANCE = createVFS();

    @SuppressWarnings("unchecked")
    static VFS createVFS() {
      // Try the user implementations first, then the built-ins
      // 先尝试自定义的 VFS 实现类，再尝试内置的 VFS 实现类的数组，并将他们放入数组impls中
      List<Class<? extends VFS>> impls = new ArrayList<>(USER_IMPLEMENTATIONS);
      impls.addAll(Arrays.asList((Class<? extends VFS>[]) IMPLEMENTATIONS));

      // Try each implementation class until a valid one is found
      // 创建 VFS 对象，选择第一个符合的
      VFS vfs = null;
      for (int i = 0; vfs == null || !vfs.isValid(); i++) {
        Class<? extends VFS> impl = impls.get(i);
        try {
          // 通过反射方式场创建实例
          vfs = impl.getDeclaredConstructor().newInstance();
          // 打印无效的vfs
          if (!vfs.isValid() && log.isDebugEnabled()) {
            log.debug("VFS implementation " + impl.getName() + " is not valid in this environment.");
          }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
            | InvocationTargetException e) { // 异常处理
          log.error("Failed to instantiate " + impl, e);
          return null;
        }
      }

      if (log.isDebugEnabled()) {
        log.debug("Using VFS adapter " + vfs.getClass().getName());
      }

      // 返回第一个有效的VFS对象
      return vfs;
    }

    // 将构造器设置为私有的，禁止外部创建实例
    private VFSHolder() {
    }
  }

  /**
   * Get the singleton {@link VFS} instance. If no {@link VFS} implementation can be found for the current environment,
   * then this method returns null.
   * 获取单例{@link VFS}实例。如果当前环境中找不到{@link VFS}实现，则此方法返回null
   *
   * @return single instance of VFS VFS的单例
   */
  public static VFS getInstance() {
    return VFSHolder.INSTANCE;
  }

  /**
   * Adds the specified class to the list of {@link VFS} implementations. Classes added in this manner are tried in the
   * order they are added and before any of the built-in implementations.
   *
   * 将指定的类添加到{@link VFS}实现列表中。以这种方式添加的类将按照添加的顺序和在任何内置实现之前进行尝试。
   * @param clazz
   *          The {@link VFS} implementation class to add.  VFS的实现类
   */
  public static void addImplClass(Class<? extends VFS> clazz) {
    if (clazz != null) {
      USER_IMPLEMENTATIONS.add(clazz);
    }
  }

  /**
   * Get a class by name. If the class is not found then return null.
   * 按名称获取类。如果没有找到类，则返回null
   *
   * @param className
   *          the class name 类名称
   *
   * @return the class 类对象
   */
  protected static Class<?> getClass(String className) {
    try {
      // 获取当前线程的上下文类加载器，根据类的全名（包括包名）加载该类。它返回一个 Class 对象，表示该类的元数据
      return Thread.currentThread().getContextClassLoader().loadClass(className);
      // return ReflectUtil.findClass(className);
    } catch (ClassNotFoundException e) {
      if (log.isDebugEnabled()) {
        log.debug("Class not found: " + className);
      }
      return null;
    }
  }

  /**
   * Get a method by name and parameter types. If the method is not found then return null.
   * 根据名称和参数类型获取方法。如果没有找到该方法，则返回null。
   *
   * @param clazz
   *          The class to which the method belongs.  方法所属的类。
   * @param methodName
   *          The name of the method.  方法的名称
   * @param parameterTypes
   *          The types of the parameters accepted by the method.  方法接受的参数类型
   *
   * @return the method  对应的方法
   */
  protected static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
    if (clazz == null) {
      return null;
    }
    try {
      return clazz.getMethod(methodName, parameterTypes);
    } catch (SecurityException e) {
      log.error("Security exception looking for method " + clazz.getName() + "." + methodName + ".  Cause: " + e);
      return null;
    } catch (NoSuchMethodException e) {
      log.error("Method not found " + clazz.getName() + "." + methodName + ".  Cause: " + e);
      return null;
    }
  }

  /**
   * Invoke a method on an object and return whatever it returns.
   * 调用对象上的方法并返回它返回的任何值。
   *
   * @param <T>
   *          the generic type 泛型类型
   * @param method
   *          The method to invoke. 要调用的方法。
   * @param object
   *          The instance or class (for static methods) on which to invoke the method. 要在其上调用方法的实例或类（对于静态方法）
   * @param parameters
   *          The parameters to pass to the method. 传递给方法的参数
   *
   * @return Whatever the method returns. 无论方法返回什么
   *
   * @throws IOException
   *           If I/O errors occur
   * @throws RuntimeException
   *           If anything else goes wrong
   */
  @SuppressWarnings("unchecked")
  protected static <T> T invoke(Method method, Object object, Object... parameters)
      throws IOException, RuntimeException {
    try {
      return (T) method.invoke(object, parameters);
    } catch (IllegalArgumentException | IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IOException) {
        throw (IOException) e.getTargetException();
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * Get a list of {@link URL}s from the context classloader for all the resources found at the specified path.
   * 从上下文类加载器中获取在指定路径上找到的所有资源的{@link URL}列表
   *
   * @param path
   *          The resource path. 资源路径
   *
   * @return A list of {@link URL}s, as returned by {@link ClassLoader#getResources(String)}.
   *
   * @throws IOException
   *           If I/O errors occur
   */
  protected static List<URL> getResources(String path) throws IOException {
    return Collections.list(Thread.currentThread().getContextClassLoader().getResources(path));
  }

  /**
   * Return true if the {@link VFS} implementation is valid for the current environment.
   * 抽象方法：判断是否为合法的 VFS
   *
   * @return true, if is valid 如果合法放回true
   */
  public abstract boolean isValid();

  /**
   * Recursively list the full resource path of all the resources that are children of the resource identified by a URL.
   * 递归列出由URL标识的资源的所有子资源的完整资源路径
   *
   * @param url
   *          The URL that identifies the resource to list.
   * @param forPath
   *          The path to the resource that is identified by the URL. Generally, this is the value passed to
   *          {@link #getResources(String)} to get the resource URL.
   *
   * @return A list containing the names of the child resources.
   *
   * @throws IOException
   *           If I/O errors occur
   */
  protected abstract List<String> list(URL url, String forPath) throws IOException;

  /**
   * Recursively list the full resource path of all the resources that are children of all the resources found at the
   * specified path.
   * 递归列出在指定路径上找到的所有资源的子资源的所有资源的完整资源路径。
   *
   * @param path
   *          The path of the resource(s) to list. 要列出的资源的路径
   *
   * @return A list containing the names of the child resources. 包含子资源名称的列表
   *
   * @throws IOException
   *           If I/O errors occur
   */
  public List<String> list(String path) throws IOException {
    List<String> names = new ArrayList<>();
    // 通过类加载器的getResources方法，获取path资源的URL，并进行遍历
    for (URL url : getResources(path)) {
      // 调用list方法，这里默认实现类是DefaultVFS，递归列出所有的资源
      // 将资源加入到names中
      names.addAll(list(url, path));
    }
    return names;
  }
}
