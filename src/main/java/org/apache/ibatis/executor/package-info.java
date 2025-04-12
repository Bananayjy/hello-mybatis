/*
 *    Copyright 2009-2022 the original author or authors.
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
/**
 * Contains the statement executors.
 * statement包：实现向数据库发起SQL命令
 * parameter包：实现设置PreparedStatement的占位符参数
 * keygen 包，实现数据库主键生成( 获得 )的功能
 * resultset 包，实现 ResultSet 结果集的处理，将其映射成对应的结果对象。
 * result 包，结果的处理，被 resultset 包所调用
 * loader 包，实现延迟加载的功能
 * 根目录，Executor 接口及其实现类，作为 SQL 执行的核心入口
 */
package org.apache.ibatis.executor;
