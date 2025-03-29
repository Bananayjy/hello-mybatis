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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Properties;

/**
 * A class to simplify access to resources through the classloader.
 *
 * @author Clinton Begin
 */
public class Resources {

  // 通过new实例化一个 ClassLoaderWrapper 对象
  private static final ClassLoaderWrapper classLoaderWrapper = new ClassLoaderWrapper();

  /**
   * Charset to use when calling getResourceAsReader. null means use the system default.
   * 调用getResourceAsReader时使用的字符集。Null表示使用系统默认值。
   */
  private static Charset charset;

  // 无参构造器（啥都不做……）
  private Resources() {
  }

  /**
   * Returns the default classloader (may be null).
   * 获取默认的类加载器（可能为null）
   *
   * @return The default classloader 默认的类加载器
   */
  public static ClassLoader getDefaultClassLoader() {
    return classLoaderWrapper.defaultClassLoader;
  }

  /**
   * Sets the default classloader
   * 修改默认的类加载器
   *
   * @param defaultClassLoader
   *          - the new default ClassLoader
   */
  public static void setDefaultClassLoader(ClassLoader defaultClassLoader) {
    classLoaderWrapper.defaultClassLoader = defaultClassLoader;
  }

  /**
   * getResourceXxx方法：获得指定资源的 URL
   * 下述有很多getResourceXxx获得指定的资源以Xxx形式，本质上都是调用classLoaderWrapper的方法，与部分需要封装成Xxx格式返回的
   * 1、getResourceURL：获取指定资源url {@link Resources#getResourceURL(String)}.
   * 2、getResourceAsStream
   * 3、getResourceAsProperties
   * 4、getResourceAsReader
   * 5、getResourceAsFile
   *
   * getUrlXxx方法：根据 URL 以某种格式获取指定资源
   * 1、getUrlAsStream：获取URL资源，并转换为流对象
   * 2、getUrlAsProperties （调用getUrlAsStream获取Stream后，并转化为Properties格式）
   * 3、getUrlAsReader（调用getUrlAsStream获取Stream后，并转化为Reader格式）
   */

  /**
   * Returns the URL of the resource on the classpath
   * 获得指定资源的 URL
   *
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static URL getResourceURL(String resource) throws IOException {
    // issue #625
    return getResourceURL(null, resource);
  }

  /**
   * Returns the URL of the resource on the classpath
   *
   * @param loader
   *          The classloader used to fetch the resource
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static URL getResourceURL(ClassLoader loader, String resource) throws IOException {
    // 调用classLoaderWrapper的getResourceAsURL方法获得指定资源的 URL
    URL url = classLoaderWrapper.getResourceAsURL(resource, loader);
    if (url == null) {
      throw new IOException("Could not find resource " + resource);
    }
    return url;
  }

  /**
   * Returns a resource on the classpath as a Stream object
   * 获得指定资源的 InputStream
   *
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static InputStream getResourceAsStream(String resource) throws IOException {
    return getResourceAsStream(null, resource);
  }

  /**
   * Returns a resource on the classpath as a Stream object
   *
   * @param loader
   *          The classloader used to fetch the resource
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static InputStream getResourceAsStream(ClassLoader loader, String resource) throws IOException {
    InputStream in = classLoaderWrapper.getResourceAsStream(resource, loader);
    if (in == null) {
      throw new IOException("Could not find resource " + resource);
    }
    return in;
  }

  /**
   * Returns a resource on the classpath as a Properties object
   *
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static Properties getResourceAsProperties(String resource) throws IOException {
    Properties props = new Properties();
    try (InputStream in = getResourceAsStream(resource)) {
      props.load(in);
    }
    return props;
  }

  /**
   * Returns a resource on the classpath as a Properties object
   *
   * @param loader
   *          The classloader used to fetch the resource
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static Properties getResourceAsProperties(ClassLoader loader, String resource) throws IOException {
    Properties props = new Properties();
    try (InputStream in = getResourceAsStream(loader, resource)) {
      props.load(in);
    }
    return props;
  }

  /**
   * Returns a resource on the classpath as a Reader object
   *
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static Reader getResourceAsReader(String resource) throws IOException {
    Reader reader;
    if (charset == null) {
      reader = new InputStreamReader(getResourceAsStream(resource));
    } else {
      reader = new InputStreamReader(getResourceAsStream(resource), charset);
    }
    return reader;
  }

  /**
   * Returns a resource on the classpath as a Reader object
   *
   * @param loader
   *          The classloader used to fetch the resource
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static Reader getResourceAsReader(ClassLoader loader, String resource) throws IOException {
    Reader reader;
    if (charset == null) {
      reader = new InputStreamReader(getResourceAsStream(loader, resource));
    } else {
      reader = new InputStreamReader(getResourceAsStream(loader, resource), charset);
    }
    return reader;
  }

  /**
   * Returns a resource on the classpath as a File object
   *
   * @param resource
   *          The resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static File getResourceAsFile(String resource) throws IOException {
    return new File(getResourceURL(resource).getFile());
  }

  /**
   * Returns a resource on the classpath as a File object
   *
   * @param loader
   *          - the classloader used to fetch the resource
   * @param resource
   *          - the resource to find
   *
   * @return The resource
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static File getResourceAsFile(ClassLoader loader, String resource) throws IOException {
    return new File(getResourceURL(loader, resource).getFile());
  }

  /**
   * Gets a URL as an input stream
   *
   * @param urlString
   *          - the URL to get
   *
   * @return An input stream with the data from the URL
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static InputStream getUrlAsStream(String urlString) throws IOException {
    URL url = new URL(urlString);
    URLConnection conn = url.openConnection();
    return conn.getInputStream();
  }

  /**
   * Gets a URL as a Reader
   *
   * @param urlString
   *          - the URL to get
   *
   * @return A Reader with the data from the URL
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static Reader getUrlAsReader(String urlString) throws IOException {
    Reader reader;
    if (charset == null) {
      reader = new InputStreamReader(getUrlAsStream(urlString));
    } else {
      reader = new InputStreamReader(getUrlAsStream(urlString), charset);
    }
    return reader;
  }

  /**
   * Gets a URL as a Properties object
   *
   * @param urlString
   *          - the URL to get
   *
   * @return A Properties object with the data from the URL
   *
   * @throws java.io.IOException
   *           If the resource cannot be found or read
   */
  public static Properties getUrlAsProperties(String urlString) throws IOException {
    Properties props = new Properties();
    try (InputStream in = getUrlAsStream(urlString)) {
      props.load(in);
    }
    return props;
  }

  /**
   * Loads a class
   *
   * @param className
   *          - the class to fetch
   *
   * @return The loaded class
   *
   * @throws ClassNotFoundException
   *           If the class cannot be found (duh!)
   */
  public static Class<?> classForName(String className) throws ClassNotFoundException {
    return classLoaderWrapper.classForName(className);
  }

  // 获取字符集的getter方法
  public static Charset getCharset() {
    return charset;
  }

  // 设置字符串的setter方法
  public static void setCharset(Charset charset) {
    Resources.charset = charset;
  }

}
