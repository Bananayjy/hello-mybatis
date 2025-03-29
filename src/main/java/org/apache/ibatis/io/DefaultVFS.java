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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * A default implementation of {@link VFS} that works for most application servers.
 * VFS的默认实现，适用于大多数应用服务器
 *
 * @author Ben Gunter
 */
public class DefaultVFS extends VFS {
  private static final Log log = LogFactory.getLog(DefaultVFS.class);

  /** The magic header that indicates a JAR (ZIP) file. */
  private static final byte[] JAR_MAGIC = { 'P', 'K', 3, 4 };

  // isValid都返回true，表示默认支持VFS
  @Override
  public boolean isValid() {
    return true;
  }

  // 递归列出所有的资源
  @Override
  public List<String> list(URL url, String path) throws IOException {
    InputStream is = null;
    try {
      List<String> resources = new ArrayList<>();

      // First, try to find the URL of a JAR file containing the requested resource. If a JAR
      // file is found, then we'll list child resources by reading the JAR.
      // 首先，尝试查找包含所请求资源的JAR文件的URL。如果是JAR文件，然后我们将通过读取JAR列出子资源。
      URL jarUrl = findJarForResource(url);
      if (jarUrl != null) {
        is = jarUrl.openStream();
        if (log.isDebugEnabled()) {
          log.debug("Listing " + url);
        }
        resources = listResources(new JarInputStream(is), path);
      } else {
        List<String> children = new ArrayList<>();
        try {
          if (isJar(url)) {
            // Some versions of JBoss VFS might give a JAR stream even if the resource
            // referenced by the URL isn't actually a JAR
            // 某些版本的JBoss VFS可能会给出一个JAR流，即使URL引用的资源实际上不是JAR
            is = url.openStream();
            try (JarInputStream jarInput = new JarInputStream(is)) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              File destinationDir = new File(path);
              for (JarEntry entry; (entry = jarInput.getNextJarEntry()) != null;) {
                if (log.isDebugEnabled()) {
                  log.debug("Jar entry: " + entry.getName());
                }
                File entryFile = new File(destinationDir, entry.getName()).getCanonicalFile();
                if (!entryFile.getPath().startsWith(destinationDir.getCanonicalPath())) {
                  throw new IOException("Bad zip entry: " + entry.getName());
                }
                children.add(entry.getName());
              }
            }
          } else {
            /*
             * Some servlet containers allow reading from directory resources like a text file, listing the child
             * resources one per line. However, there is no way to differentiate between directory and file resources
             * just by reading them. To work around that, as each line is read, try to look it up via the class loader
             * as a child of the current resource. If any line fails then we assume the current resource is not a
             * directory.
             * 一些servlet容器允许从目录资源（如文本文件）中读取，每行列出一个子资源。但是，仅仅通过读取目录和文件资源是无法区分它们的。
             * 为了解决这个问题，在读取每一行时，尝试通过类加载器将其作为当前资源的子类进行查找。如果任何一行出错，则假定当前资源不是目录。
             */
            is = url.openStream();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
              for (String line; (line = reader.readLine()) != null;) {
                if (log.isDebugEnabled()) {
                  log.debug("Reader entry: " + line);
                }
                lines.add(line);
                if (getResources(path + "/" + line).isEmpty()) {
                  lines.clear();
                  break;
                }
              }
            } catch (InvalidPathException | FileSystemException e) {
              // #1974 #2598
              lines.clear();
            }
            if (!lines.isEmpty()) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              children.addAll(lines);
            }
          }
        } catch (FileNotFoundException e) {
          /*
           * For file URLs the openStream() call might fail, depending on the servlet container, because directories
           * can't be opened for reading. If that happens, then list the directory directly instead.
           */
          if (!"file".equals(url.getProtocol())) {
            // No idea where the exception came from so rethrow it
            throw e;
          }
          File file = new File(url.getFile());
          if (log.isDebugEnabled()) {
            log.debug("Listing directory " + file.getAbsolutePath());
          }
          if (file.isDirectory()) {
            if (log.isDebugEnabled()) {
              log.debug("Listing " + url);
            }
            children = Arrays.asList(file.list());
          }
        }

        // The URL prefix to use when recursively listing child resources
        // 递归列出子资源时使用的URL前缀
        String prefix = url.toExternalForm();
        if (!prefix.endsWith("/")) {
          prefix = prefix + "/";
        }

        // Iterate over immediate children, adding files and recurring into directories
        // 迭代直接子节点，添加文件并循环到目录中
        for (String child : children) {
          String resourcePath = path + "/" + child;
          resources.add(resourcePath);
          URL childUrl = new URL(prefix + child);
          resources.addAll(list(childUrl, resourcePath));
        }
      }

      return resources;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // Ignore
        }
      }
    }
  }

  /**
   * List the names of the entries in the given {@link JarInputStream} that begin with the specified {@code path}.
   * Entries will match with or without a leading slash.
   * 列出给定{@link JarInputStream}中以指定的{@code path}开头的条目的名称。条目将匹配有或没有前导斜杠。
   *
   * @param jar
   *          The JAR input stream
   * @param path
   *          The leading path to match
   *
   * @return The names of all the matching entries
   *
   * @throws IOException
   *           If I/O errors occur
   */
  protected List<String> listResources(JarInputStream jar, String path) throws IOException {
    // Include the leading and trailing slash when matching names
    // 在匹配名称时包括前导和尾斜杠
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    if (!path.endsWith("/")) {
      path = path + "/";
    }

    // Iterate over the entries and collect those that begin with the requested path
    // 遍历条目并收集以请求路径开头的条目
    List<String> resources = new ArrayList<>();
    for (JarEntry entry; (entry = jar.getNextJarEntry()) != null;) {
      if (!entry.isDirectory()) {
        // Add leading slash if it's missing
        StringBuilder name = new StringBuilder(entry.getName());
        if (name.charAt(0) != '/') {
          name.insert(0, '/');
        }

        // Check file name
        if (name.indexOf(path) == 0) {
          if (log.isDebugEnabled()) {
            log.debug("Found resource: " + name);
          }
          // Trim leading slash
          resources.add(name.substring(1));
        }
      }
    }
    return resources;
  }

  /**
   * Attempts to deconstruct the given URL to find a JAR file containing the resource referenced by the URL. That is,
   * assuming the URL references a JAR entry, this method will return a URL that references the JAR file containing the
   * entry. If the JAR cannot be located, then this method returns null.
   * 尝试解构给定的URL以查找包含该URL引用的资源的JAR文件。也就是说，假设URL引用一个JAR条目，
   * 该方法将返回一个引用包含该条目的JAR文件的URL。如果无法找到JAR，则此方法返回null。
   *
   * @param url
   *          The URL of the JAR entry. JAR条目的URL
   *
   * @return The URL of the JAR file, if one is found. Null if not. JAR文件的URL（如果找到的话）。否则为空
   *
   * @throws MalformedURLException
   *           the malformed URL exception
   */
  protected URL findJarForResource(URL url) throws MalformedURLException {
    // 日志打印
    if (log.isDebugEnabled()) {
      log.debug("Find JAR URL: " + url);
    }

    // If the file part of the URL is itself a URL, then that URL probably points to the JAR
    // 如果URL的文件部分本身是一个URL，那么该URL可能指向JAR
    boolean continueLoop = true;
    while (continueLoop) {
      try {
        url = new URL(url.getFile());
        if (log.isDebugEnabled()) {
          log.debug("Inner URL: " + url);
        }
      } catch (MalformedURLException e) {
        // This will happen at some point and serves as a break in the loop
        continueLoop = false;
      }
    }

    // Look for the .jar extension and chop off everything after that
    // 寻找.jar扩展名并删除之后的所有内容
    StringBuilder jarUrl = new StringBuilder(url.toExternalForm());
    int index = jarUrl.lastIndexOf(".jar");
    if (index < 0) { // 如果不以 .jar 结尾，则直接返回 null
      if (log.isDebugEnabled()) {
        log.debug("Not a JAR: " + jarUrl);
      }
      return null;
    }
    // 删除之后的所有内容
    jarUrl.setLength(index + 4);
    if (log.isDebugEnabled()) {
      log.debug("Extracted JAR URL: " + jarUrl);
    }

    // Try to open and test it
    // 尝试获取
    try {
      URL testUrl = new URL(jarUrl.toString());
      if (isJar(testUrl)) { // 判断是否为 Jar 文件
        return testUrl;
      }
      // WebLogic fix: check if the URL's file exists in the filesystem.
      if (log.isDebugEnabled()) {
        log.debug("Not a JAR: " + jarUrl);
      }
      jarUrl.replace(0, jarUrl.length(), testUrl.getFile());
      File file = new File(jarUrl.toString());

      // File name might be URL-encoded
      // 处理路径编码问题
      if (!file.exists()) {
        try {
          file = new File(URLEncoder.encode(jarUrl.toString(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException("Unsupported encoding?  UTF-8?  That's impossible.");
        }
      }

      // 判断文件存在
      if (file.exists()) {
        if (log.isDebugEnabled()) {
          log.debug("Trying real file: " + file.getAbsolutePath());
        }
        testUrl = file.toURI().toURL();
        if (isJar(testUrl)) { // 判断是否为 Jar 文件
          return testUrl;
        }
      }
    } catch (MalformedURLException e) {
      log.warn("Invalid JAR URL: " + jarUrl);
    }

    if (log.isDebugEnabled()) {
      log.debug("Not a JAR: " + jarUrl);
    }
    return null;
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
    return packageName == null ? null : packageName.replace('.', '/');
  }

  /**
   * Returns true if the resource located at the given URL is a JAR file.
   *
   * @param url
   *          The URL of the resource to test.
   *
   * @return true, if is jar
   */
  protected boolean isJar(URL url) {
    return isJar(url, new byte[JAR_MAGIC.length]);
  }

  /**
   * Returns true if the resource located at the given URL is a JAR file.
   * 如果位于给定URL的资源是JAR文件，则返回true（通过文件头的magic_number判断）
   *
   * @param url
   *          The URL of the resource to test.
   * @param buffer
   *          A buffer into which the first few bytes of the resource are read. The buffer must be at least the size of
   *          {@link #JAR_MAGIC}. (The same buffer may be reused for multiple calls as an optimization.)
   *
   * @return true, if is jar
   */
  protected boolean isJar(URL url, byte[] buffer) {
    try (InputStream is = url.openStream()) {
      // 读取文件头
      is.read(buffer, 0, JAR_MAGIC.length);
      // 判断文件头的magic_number 是否符合 jar
      if (Arrays.equals(buffer, JAR_MAGIC)) {
        if (log.isDebugEnabled()) {
          log.debug("Found JAR: " + url);
        }
        return true;
      }
    } catch (Exception e) {
      // Failure to read the stream means this is not a JAR
    }

    return false;
  }
}
