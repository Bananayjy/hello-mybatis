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
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 解析器工具类，用于获得指定目录符合条件的类
 * <p>
 * ResolverUtil is used to locate classes that are available in the/a class path and meet arbitrary conditions. The two
 * most common conditions are that a class implements/extends another class, or that is it annotated with a specific
 * annotation. However, through the use of the {@link Test} class it is possible to search using arbitrary conditions.
 * ResolverUtil用于定位/a类路径中可用且满足任意条件的类。最常见的两种情况是一个类实现/扩展另一个类，或者用特定的注释对其进行注释。但是，
 * 通过使用{@link Test}类，可以使用任意条件进行搜索。
 * <p>
 * A ClassLoader is used to locate all locations (directories and jar files) in the class path that contain classes
 * within certain packages, and then to load those classes and check them. By default the ClassLoader returned by
 * {@code Thread.currentThread().getContextClassLoader()} is used, but this can be overridden by calling
 * {@link #setClassLoader(ClassLoader)} prior to invoking any of the {@code find()} methods.
 * ClassLoader用于定位类路径中包含某些包中的类的所有位置（目录和jar文件），然后加载这些类并检查它们。默认情况下，
 * 使用{@code Thread.currentThread(). getcontextclassloader()}返回的ClassLoader，
 * 但在调用任何{@code find()}方法之前，可以通过调用{@link #setClassLoader(ClassLoader)}来覆盖它。
 * <p>
 * General searches are initiated by calling the {@link #find(Test, String)} and supplying a package name and a Test
 * instance. This will cause the named package <b>and all sub-packages</b> to be scanned for classes that meet the test.
 * There are also utility methods for the common use cases of scanning multiple packages for extensions of particular
 * classes, or classes annotated with a specific annotation.
 * 一般的搜索是通过调用{@link #find(Test, String)}并提供一个包名和一个Test实例来启动的。这将导致扫描命名包<b>和所有子包</b>以查找满足测试的类。
 * 还有一些实用方法用于扫描多个包以查找特定类的扩展，或者用特定注释注释的类的常见用例。
 * <p>
 * The standard usage pattern for the ResolverUtil class is as follows:
 * ResolverUtil类的标准使用模式如下：
 *
 * <pre>
 * ResolverUtil&lt;ActionBean&gt; resolver = new ResolverUtil&lt;ActionBean&gt;();
 * resolver.findImplementation(ActionBean.class, pkg1, pkg2);
 * resolver.find(new CustomTest(), pkg1);
 * resolver.find(new CustomTest(), pkg2);
 * Collection&lt;ActionBean&gt; beans = resolver.getClasses();
 * </pre>
 *
 * @author Tim Fennell
 *
 * @param <T>
 *          the generic type
 */
public class ResolverUtil<T> {

  /**
   * An instance of Log to use for logging in this class.
   */
  private static final Log log = LogFactory.getLog(ResolverUtil.class);

  /**
   * A simple interface that specifies how to test classes to determine if they are to be included in the results
   * produced by the ResolverUtil.
   * 匹配判断接口：一个简单的接口，指定如何测试类，以确定是否将它们包含在ResolverUtil生成的结果中。
   */
  public interface Test {

    /**
     * Will be called repeatedly with candidate classes. Must return True if a class is to be included in the results,
     * false otherwise.
     * 将与候选类一起重复调用。如果要在结果中包含一个类，则必须返回True，否则返回false
     *
     * @param type
     *          the type
     *
     * @return true, if successful
     */
    boolean matches(Class<?> type);
  }

  /**
   * A Test that checks to see if each class is assignable to the provided class. Note that this test will match the
   * parent type itself if it is presented for matching.
   *
   * 检查每个类是否可分配给所提供的类。注意，这个测试将匹配父类型本身，如果它是为匹配而呈现的。
   *
   */
  public static class IsA implements Test {

    /** The parent. */
    private final Class<?> parent;

    /**
     * Constructs an IsA test using the supplied Class as the parent class/interface.
     *
     * @param parentType
     *          the parent type
     */
    public IsA(Class<?> parentType) {
      this.parent = parentType;
    }

    /** Returns true if type is assignable to the parent type supplied in the constructor. */
    @Override
    public boolean matches(Class<?> type) {
      return type != null && parent.isAssignableFrom(type);
    }

    @Override
    public String toString() {
      return "is assignable to " + parent.getSimpleName();
    }
  }

  /**
   * A Test that checks to see if each class is annotated with a specific annotation. If it is, then the test returns
   * true, otherwise false.
   * 用于检查每个类是否都用特定的注释进行了注释。如果是，则测试返回true，否则返回false。
   */
  public static class AnnotatedWith implements Test {

    /** The annotation. */
    // 注解
    private final Class<? extends Annotation> annotation;

    /**
     * Constructs an AnnotatedWith test for the specified annotation type.
     * 有参构造器：为指定的注释类型构造一个AnnotatedWith测试
     *
     * @param annotation
     *          the annotation 注释类型
     */
    public AnnotatedWith(Class<? extends Annotation> annotation) {
      this.annotation = annotation;
    }

    /** Returns true if the type is annotated with the class provided to the constructor. */
    // 如果使用提供给构造函数的类对类型进行了注释，则返回true
    @Override
    public boolean matches(Class<?> type) {
      return type != null && type.isAnnotationPresent(annotation);
    }

    @Override
    public String toString() {
      return "annotated with @" + annotation.getSimpleName();
    }
  }

  /** The set of matches being accumulated. */
  // 正在累积的匹配集：即匹配到的内容
  private Set<Class<? extends T>> matches = new HashSet<>();

  /**
   * The ClassLoader to use when looking for classes. If null then the ClassLoader returned by
   * Thread.currentThread().getContextClassLoader() will be used.
   * 查找类时要使用的ClassLoader。如果为空，则使用Thread.currentThread(). getcontextclassloader（）返回的ClassLoader。
   */
  private ClassLoader classloader;

  /**
   * Provides access to the classes discovered so far. If no calls have been made to any of the {@code find()} methods,
   * this set will be empty.
   * 提供对目前发现的类的访问。如果没有对任何{@code find()}方法进行调用，则该集合将为空
   *
   * @return the set of classes that have been discovered. 已发现的类的集合。
   */
  public Set<Class<? extends T>> getClasses() {
    return matches;
  }

  /**
   * Returns the classloader that will be used for scanning for classes. If no explicit ClassLoader has been set by the
   * calling, the context class loader will be used.
   * 返回将用于扫描类的类加载器。如果调用方没有设置显式ClassLoader，则将使用上下文类加载器
   *
   * @return the ClassLoader that will be used to scan for classes 返回类加载器：将用于扫描类的ClassLoader
   */
  public ClassLoader getClassLoader() {
    return classloader == null ? Thread.currentThread().getContextClassLoader() : classloader;
  }

  /**
   * Sets an explicit ClassLoader that should be used when scanning for classes. If none is set then the context
   * classloader will be used.
   * 设置在扫描类时应该使用的显式ClassLoader。如果没有设置，则将使用上下文类加载器
   *
   * @param classloader
   *          a ClassLoader to use when scanning for classes 扫描类时使用的就是成员变量：ClassLoader
   */
  public void setClassLoader(ClassLoader classloader) {
    this.classloader = classloader;
  }

  /**
   * Attempts to discover classes that are assignable to the type provided. In the case that an interface is provided
   * this method will collect implementations. In the case of a non-interface class, subclasses will be collected.
   * Accumulated classes can be accessed by calling {@link #getClasses()}.
   * 尝试发现可分配给所提供类型的类。在提供接口的情况下，此方法将收集实现。在非接口类的情况下，将收集子类。可以通过调用{@link #getClasses()}来访问累积的类。
   *
   * @param parent
   *          the class of interface to find subclasses or implementations of 用于查找其子类或实现的接口类
   * @param packageNames
   *          one or more package names to scan (including subpackages) for classes 要扫描一个或多个包名（包括子包）以查找类
   *
   * @return the resolver util
   */
  public ResolverUtil<T> findImplementations(Class<?> parent, String... packageNames) {
    if (packageNames == null) {
      return this;
    }

    // 将需要查找的对象封装为Test对象
    Test test = new IsA(parent);
    for (String pkg : packageNames) { // 便利需要查找的包名
      // 调用find方法进行匹配结果
      find(test, pkg);
    }

    return this;
  }

  /**
   * Attempts to discover classes that are annotated with the annotation. Accumulated classes can be accessed by calling
   * {@link #getClasses()}. 尝试发现用注释注释的类。可以通过调用{@link #getClasses()}来访问累积的类
   *
   * @param annotation
   *          the annotation that should be present on matching classes 应该出现在匹配类上的注释
   * @param packageNames
   *          one or more package names to scan (including subpackages) for classes 要扫描一个或多个包名（包括子包）以查找类
   *
   * @return the resolver util
   */
  public ResolverUtil<T> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
    if (packageNames == null) {
      return this;
    }

    Test test = new AnnotatedWith(annotation);
    for (String pkg : packageNames) {
      find(test, pkg);
    }

    return this;
  }

  /**
   * 获取指定包下，符合条件的类
   *
   * Scans for classes starting at the package provided and descending into subpackages. Each class is offered up to the
   * Test as it is discovered, and if the Test returns true the class is retained. Accumulated classes can be fetched by
   * calling {@link #getClasses()}.
   * 扫描从所提供的包开始到子包的类。每个类在被发现时都提供给Test，如果Test返回true，则保留该类。可以通过调用{@link #getClasses()}来获取累积的类
   *
   * @param test
   *          an instance of {@link Test} that will be used to filter classes Test的一个对象，用于过滤类
   * @param packageName
   *          the name of the package from which to start scanning for classes, e.g. {@code net.sourceforge.stripes} 开始扫描类的包的名称
   *
   * @return the resolver util 返回ResolverUtil对象
   */
  public ResolverUtil<T> find(Test test, String packageName) {
    // 获得包的路径
    String path = getPackagePath(packageName);

    try {
      // 通过VFS的getInstance获取其单例实例
      // 然后调用list获取路径下的所有文件
      List<String> children = VFS.getInstance().list(path);
      for (String child : children) {
        if (child.endsWith(".class")) { // 结尾是.class的字节码文件
          // 如果匹配，则添加到结果集
          addIfMatching(test, child);
        }
      }
    } catch (IOException ioe) {
      log.error("Could not read package: " + packageName, ioe);
    }

    return this;
  }

  /**
   * Converts a Java package name to a path that can be looked up with a call to
   * {@link ClassLoader#getResources(String)}.
   *
   * @param packageName
   *          The Java package name to convert to a path
   *
   * @return the package path
   */
  protected String getPackagePath(String packageName) {
    // 如果包名为null就直接返回null
    // 如果包名是xxx.xxx.xxx的，就替换为路径格式xxx/xxx/xxx
    return packageName == null ? null : packageName.replace('.', '/');
  }

  /**
   * Add the class designated by the fully qualified class name provided to the set of resolved classes if and only if
   * it is approved by the Test supplied.
   * 当且仅当提供的Test对象匹配时，将所提供的完全限定类名指定的类添加到已解析类集中。
   *
   * @param test
   *          the test used to determine if the class matches 匹配对象
   * @param fqn
   *          the fully qualified name of a class 类的完全限定名
   */
  @SuppressWarnings("unchecked")
  protected void addIfMatching(Test test, String fqn) {
    try {
      // 获得全类名
      String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');
      // 获取默认类加载器
      ClassLoader loader = getClassLoader();
      if (log.isDebugEnabled()) {
        log.debug("Checking to see if class " + externalName + " matches criteria [" + test + "]");
      }

      // 加载类
      Class<?> type = loader.loadClass(externalName);
      // 通过Test对象matches方法判断是否匹配，匹配则加入到结果集中
      if (test.matches(type)) {
        matches.add((Class<T>) type);
      }
    } catch (Throwable t) {
      log.warn("Could not examine class '" + fqn + "'" + " due to a " + t.getClass().getName() + " with message: "
          + t.getMessage());
    }
  }
}
