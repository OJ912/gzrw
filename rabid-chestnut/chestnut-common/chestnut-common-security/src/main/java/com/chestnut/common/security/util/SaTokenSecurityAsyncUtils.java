/*
 * Copyright 2022-2024 兮玥(190785909@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chestnut.common.security.util;

import cn.dev33.satoken.context.SaHolder;

/**
 * SaToken异步任务安全上下文工具类
 * 
 * 用于在异步任务中设置和清除安全上下文标志，以便绕过SaToken权限验证
 */
public class SaTokenSecurityAsyncUtils {
    
    /**
     * 异步上下文标志的键名
     */
    private static final String ASYNC_CONTEXT_KEY = "ASYNC_CONTEXT";
    
    /**
     * 设置异步上下文标志，用于绕过SaToken权限验证
     */
    public static void setupSaTokenAsyncContext() {
        SaHolder.getStorage().set(ASYNC_CONTEXT_KEY, true);
    }
    
    /**
     * 清除异步上下文标志
     */
    public static void clearSaTokenAsyncContext() {
        SaHolder.getStorage().delete(ASYNC_CONTEXT_KEY);
    }
    
    /**
     * 检查是否处于异步上下文中
     * 
     * @return 是否处于异步上下文中
     */
    public static boolean isAsyncContext() {
        return Boolean.TRUE.equals(SaHolder.getStorage().get(ASYNC_CONTEXT_KEY));
    }
}
