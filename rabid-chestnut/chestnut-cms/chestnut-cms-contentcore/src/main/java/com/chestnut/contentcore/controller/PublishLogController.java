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
package com.chestnut.contentcore.controller;

import com.chestnut.common.domain.R;
import com.chestnut.common.security.anno.Priv;
import com.chestnut.common.security.web.BaseRestController;
import com.chestnut.contentcore.publish.IPublishStrategy;
import com.chestnut.system.security.AdminUserType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布日志管理
 *
 * @author 兮玥
 * @email 190785909@qq.com
 */
@Priv(type = AdminUserType.TYPE)
@RestController
@RequiredArgsConstructor
@RequestMapping("/cms/publish/log")
public class PublishLogController extends BaseRestController {

	private final IPublishStrategy publishStrategy;

	/**
	 * 发布队列任务数量
	 */
	@GetMapping("/count")
	public R<?> getPublishTaskCount() {
		return R.ok(publishStrategy.getTaskCount());
	}

	/**
	 * 测试添加发布任务
	 */
	@Priv(type = AdminUserType.TYPE)
	@GetMapping("/test")
	public R<?> testAddTask() {
		try {
			// 使用站点发布类型
			publishStrategy.publish("site", "test-" + System.currentTimeMillis());
			return R.ok(publishStrategy.getTaskCount());
		} catch (Exception e) {
			e.printStackTrace();
			return R.fail("添加测试任务失败: " + e.getMessage());
		}
	}

	/**
	 * 清理发布队列
	 */
	@DeleteMapping("/clear")
	public R<?> clearPublishTask() {
		publishStrategy.cleanTasks();
		return R.ok();
	}
}
