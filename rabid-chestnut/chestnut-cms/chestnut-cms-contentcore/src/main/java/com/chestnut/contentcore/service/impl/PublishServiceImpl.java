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
package com.chestnut.contentcore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chestnut.common.async.AsyncTask;
import com.chestnut.common.async.AsyncTaskManager;
import com.chestnut.common.security.domain.LoginUser;
import com.chestnut.common.staticize.StaticizeService;
import com.chestnut.common.staticize.core.TemplateContext;
import com.chestnut.common.utils.Assert;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.common.utils.file.FileExUtils;
import com.chestnut.contentcore.core.IContent;
import com.chestnut.contentcore.core.IContentType;
import com.chestnut.contentcore.core.IPageWidget;
import com.chestnut.contentcore.fixed.dict.ContentStatus;
import com.chestnut.contentcore.enums.ContentCopyType;
import com.chestnut.contentcore.core.IPublishPipeProp;
import com.chestnut.contentcore.core.impl.CatalogType_Link;
import com.chestnut.contentcore.core.impl.PublishPipeProp_ContentTemplate;
import com.chestnut.contentcore.core.impl.PublishPipeProp_DefaultListTemplate;
import com.chestnut.contentcore.domain.*;
import com.chestnut.contentcore.enums.ContentCopyType;
import com.chestnut.contentcore.exception.ContentCoreErrorCode;
import com.chestnut.contentcore.listener.event.AfterContentPublishEvent;
import com.chestnut.contentcore.publish.IPublishStrategy;
import com.chestnut.contentcore.publish.staticize.CatalogStaticizeType;
import com.chestnut.contentcore.publish.staticize.ContentStaticizeType;
import com.chestnut.contentcore.publish.staticize.SiteStaticizeType;
import com.chestnut.contentcore.service.*;
import com.chestnut.contentcore.template.ITemplateType;
import com.chestnut.contentcore.template.impl.CatalogTemplateType;
import com.chestnut.contentcore.template.impl.ContentTemplateType;
import com.chestnut.contentcore.template.impl.SiteTemplateType;
import com.chestnut.contentcore.util.*;
import com.chestnut.system.fixed.dict.YesOrNo;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.context.support.AbstractApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;
import java.time.ZoneId;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.Timer;
import java.util.TimerTask;

@Service
@RequiredArgsConstructor
public class PublishServiceImpl implements IPublishService, ApplicationContextAware {

	private static final Logger logger = LoggerFactory.getLogger(PublishServiceImpl.class);

	private final ISiteService siteService;

	private final ICatalogService catalogService;

	private final IContentService contentService;

	private final ITemplateService templateService;

	private final IPublishPipeService publishPipeService;

	private final StaticizeService staticizeService;

	private final AsyncTaskManager asyncTaskManager;

	private final IPublishStrategy publishStrategy;

	private ApplicationContext applicationContext;

	// 添加任务超时时间（毫秒）
	private static final long TASK_TIMEOUT = 300000; // 5分钟
	private static final int MAX_RETRY_COUNT = 3;
	
	// 添加任务状态跟踪
	private final Map<Long, Boolean> taskStatusMap = new ConcurrentHashMap<>();
	private final Map<Long, Long> taskStartTimeMap = new ConcurrentHashMap<>();

	// 添加发布进度跟踪
	private final Map<String, PublishProgressInfo> publishProgressMap = new ConcurrentHashMap<>();

	// 添加任务进度信息
	private final Map<String, Object> taskProgressInfo = new ConcurrentHashMap<>();

	// 添加任务创建时间
	private final Map<String, Long> taskCreateTimes = new ConcurrentHashMap<>();

	// 添加任务未来
	private final Map<String, Future<?>> taskFutures = new ConcurrentHashMap<>();

	/**
	 * 发布进度信息类
	 */
	public static class PublishProgressInfo {
		private int totalCount;
		private int currentCount;
		private String currentItemInfo;
		private int percent;
		private List<String> recentLogs = new ArrayList<>();
		private final long startTime;

		public PublishProgressInfo(int totalCount) {
			this.totalCount = totalCount;
			this.currentCount = 0;
			this.percent = 0;
			this.startTime = System.currentTimeMillis();
		}

		public void incrementCount() {
			this.currentCount++;
			this.percent = (int) (((float) currentCount / totalCount) * 100);
		}

		public void addLog(String log) {
			if (recentLogs.size() >= 10) {
				recentLogs.remove(0);
			}
			recentLogs.add(log);
		}

		public int getTotalCount() {
			return totalCount;
		}

		public int getCurrentCount() {
			return currentCount;
		}

		public String getCurrentItemInfo() {
			return currentItemInfo;
		}

		public void setCurrentItemInfo(String currentItemInfo) {
			this.currentItemInfo = currentItemInfo;
		}

		public int getPercent() {
			return percent;
		}

		public List<String> getRecentLogs() {
			return recentLogs;
		}

		public long getElapsedTimeSeconds() {
			return (System.currentTimeMillis() - startTime) / 1000;
		}

		public String getFormattedElapsedTime() {
			long seconds = getElapsedTimeSeconds();
			long minutes = seconds / 60;
			seconds = seconds % 60;
			return String.format("%d分%d秒", minutes, seconds);
		}

		public String getEstimatedTimeRemaining() {
			if (currentCount == 0 || percent == 0) {
				return "计算中...";
			}
			
			long elapsedSeconds = getElapsedTimeSeconds();
			long estimatedTotalSeconds = (long) ((float) elapsedSeconds * 100 / percent);
			long remainingSeconds = estimatedTotalSeconds - elapsedSeconds;
			
			if (remainingSeconds <= 0) {
				return "即将完成";
			}
			
			long minutes = remainingSeconds / 60;
			long seconds = remainingSeconds % 60;
			return String.format("约%d分%d秒", minutes, seconds);
		}
	}

	/**
	 * 获取发布进度信息
	 * 
	 * @param taskId 任务ID
	 * @return 进度信息
	 */
	public PublishProgressInfo getPublishProgress(String taskId) {
		return publishProgressMap.get(taskId);
	}

	@Override
	public String getSitePageData(CmsSite site, String publishPipeCode, boolean isPreview)
			throws IOException, TemplateException {
		String indexTemplate = site.getIndexTemplate(publishPipeCode);
		File templateFile = this.templateService.findTemplateFile(site, indexTemplate, publishPipeCode);
		if (Objects.isNull(templateFile)) {
			throw ContentCoreErrorCode.TEMPLATE_EMPTY.exception(publishPipeCode, indexTemplate);
		}
		// 模板ID = 通道:站点目录:模板文件名
		String templateKey = SiteUtils.getTemplateKey(site, publishPipeCode, indexTemplate);
		TemplateContext context = new TemplateContext(templateKey, isPreview, publishPipeCode);
		// init template datamode
		TemplateUtils.initGlobalVariables(site, context);
		// init templateType data to datamode
		ITemplateType templateType = templateService.getTemplateType(SiteTemplateType.TypeId);
		templateType.initTemplateData(site.getSiteId(), context);

		long s = System.currentTimeMillis();
		try (StringWriter writer = new StringWriter()) {
			this.staticizeService.process(context, writer);
			return writer.toString();
		} finally {
			logger.debug("[{}]首页模板解析：{}\t耗时：{}ms", publishPipeCode, site.getName(), System.currentTimeMillis() - s);
		}
	}

	@Override
	public void publishSiteIndex(CmsSite site) {
		// 发布所有通道页面
		List<CmsPublishPipe> publishPipes = this.publishPipeService.getPublishPipes(site.getSiteId());
		Assert.isTrue(!publishPipes.isEmpty(), ContentCoreErrorCode.NO_PUBLISHPIPE::exception);

		// 直接使用静态化服务生成index.shtml文件
		try {
			logger.info("开始发布站点首页：{}", site.getName());
			publishStrategy.publish(SiteStaticizeType.TYPE, site.getSiteId().toString());
			logger.info("站点首页发布成功：{}", site.getName());
		} catch (Exception e) {
			logger.error("发布站点首页出错：" + site.getName(), e);
			throw new RuntimeException("发布站点首页失败：" + e.getMessage(), e);
		}
	}

	/**
	 * 发布全站，异步任务
	 * <p>
	 * 发布站点下所有栏目及指定状态内容
	 */
	@Override
	public AsyncTask publishAll(CmsSite site, String contentStatus, LoginUser operator) {
		return publishAll(site, contentStatus, operator, "SitePublish_" + UUID.randomUUID().toString());
	}

	/**
	 * 发布全站，异步任务，带任务ID
	 * <p>
	 * 发布站点下所有栏目及指定状态内容
	 */
	public AsyncTask publishAll(CmsSite site, String contentStatus, LoginUser operator, String taskId) {
		logger.info("[发布全站] 开始发布站点: {}, 内容状态: {}, 任务ID: {}", site.getSiteId(), contentStatus, taskId);
		
		// 创建进度信息对象
		PublishProgressInfo progressInfo = new PublishProgressInfo(100); // 初始化为100%进度
		publishProgressMap.put(taskId, progressInfo);
		progressInfo.addLog("[发布全站] 开始发布站点: " + site.getName());
		
		AsyncTask asyncTask = new AsyncTask() {
			@Override
			public void run0() throws InterruptedException {
				try {
					setTaskId("SitePublish_" + taskId);
					
					// 清除模板缓存
					templateService.clearSiteAllTemplateStaticContentCache(site);
					progressInfo.addLog("已清除模板缓存");
					
					// 获取所有栏目
					List<CmsCatalog> catalogs = catalogService.list(
						new LambdaQueryWrapper<CmsCatalog>()
							.eq(CmsCatalog::getSiteId, site.getSiteId())
							.ne(CmsCatalog::getCatalogType, CatalogType_Link.ID)
					);
					progressInfo.addLog("获取栏目数量: " + catalogs.size());
					
					// 获取符合条件的内容总数
					LambdaQueryWrapper<CmsContent> countWrapper = new LambdaQueryWrapper<CmsContent>()
						.eq(CmsContent::getSiteId, site.getSiteId())
						.in(CmsContent::getCatalogId, catalogs.stream()
							.map(catalog -> catalog.getCatalogId())
							.collect(Collectors.toList()));
					
					if (StringUtils.isNotEmpty(contentStatus)) {
						countWrapper.eq(CmsContent::getStatus, contentStatus);
					} else {
						countWrapper.in(CmsContent::getStatus, ContentStatus.TO_PUBLISHED, ContentStatus.PUBLISHED);
					}
					
					long contentTotal = contentService.dao().count(countWrapper);
					if (contentTotal > 0) {
						// 更新进度信息的总数
						progressInfo.totalCount = (int) (contentTotal + catalogs.size());
						progressInfo.addLog("待发布内容数量: " + contentTotal);
						
						// 分页获取内容以避免内存问题
						int pageSize = 100;
						int pageIndex = 1;
						int processedCount = 0;
						
						while (true) {
							LambdaQueryWrapper<CmsContent> wrapper = new LambdaQueryWrapper<CmsContent>()
								.eq(CmsContent::getSiteId, site.getSiteId())
								.in(CmsContent::getCatalogId, catalogs.stream()
									.map(catalog -> catalog.getCatalogId())
									.collect(Collectors.toList()));
							
							if (StringUtils.isNotEmpty(contentStatus)) {
								wrapper.eq(CmsContent::getStatus, contentStatus);
							} else {
								wrapper.in(CmsContent::getStatus, ContentStatus.TO_PUBLISHED, ContentStatus.PUBLISHED);
							}
							
							Page<CmsContent> page = new Page<>(pageIndex, pageSize);
							page = contentService.dao().page(page, wrapper);
							
							if (page.getRecords().isEmpty()) {
								break;
							}
							
							for (CmsContent content : page.getRecords()) {
								try {
									processedCount++;
									String currentInfo = String.format("正在发布内容 [%d/%d]: %s (ID: %d)", 
										processedCount, contentTotal, 
										StringUtils.substring(content.getTitle(), 0, 30), 
										content.getContentId());
									
									progressInfo.setCurrentItemInfo(currentInfo);
									progressInfo.incrementCount();
									int percent = (int)((processedCount * 100) / progressInfo.totalCount);
									setProgressInfo(percent, currentInfo);
									
									// 更新内容状态为已发布
									content.setStatus(ContentStatus.PUBLISHED);
									contentService.dao().updateById(content);
									
									// 直接调用静态化方法生成文件
									ContentStaticizeType staticizer = applicationContext.getBean(ContentStaticizeType.class);
									staticizer.staticize(content.getContentId().toString());
									
									// 重要: 触发内容发布事件，以便更新搜索索引
									try {
										// 检查应用上下文是否处于活动状态
										if (applicationContext instanceof AbstractApplicationContext) {
											AbstractApplicationContext context = (AbstractApplicationContext) applicationContext;
											if (context.isActive() && context.isRunning()) {
												// 只有当上下文活动且正在运行时才触发事件
												IContentType contentType = ContentCoreUtils.getContentType(content.getContentType());
												IContent<?> iContent = contentType.loadContent(content);
												applicationContext.publishEvent(new AfterContentPublishEvent(PublishServiceImpl.this, iContent));
												logger.info("[发布全站] 已触发内容索引更新: {}", content.getContentId());
											} else {
												logger.warn("[发布全站] 应用上下文已关闭或不活动，跳过触发内容索引更新: {}", content.getContentId());
											}
										} else {
											// 如果不是AbstractApplicationContext，则直接发布事件
											IContentType contentType = ContentCoreUtils.getContentType(content.getContentType());
											IContent<?> iContent = contentType.loadContent(content);
											applicationContext.publishEvent(new AfterContentPublishEvent(PublishServiceImpl.this, iContent));
											logger.info("[发布全站] 已触发内容索引更新: {}", content.getContentId());
										}
									} catch (Exception e) {
										logger.error("[发布全站] 触发内容索引更新失败: " + content.getContentId(), e);
									}
									
									String successMsg = String.format("[发布全站] 成功发布内容 [%d/%d]: %s", 
										processedCount, contentTotal, content.getTitle());
									logger.info(successMsg);
									progressInfo.addLog(successMsg);
								} catch (Exception e) {
									String errorMsg = "[发布全站] 发布内容失败: " + content.getContentId() + ", 原因: " + e.getMessage();
									logger.error(errorMsg, e);
									progressInfo.addLog(errorMsg);
								}
							}
							
							pageIndex++;
						}
					}
					
					// 发布所有栏目
					progressInfo.addLog("开始发布栏目，数量: " + catalogs.size());
					for (int i = 0; i < catalogs.size(); i++) {
						CmsCatalog catalog = catalogs.get(i);
						try {
							String currentInfo = String.format("正在发布栏目 [%d/%d]: %s", 
								i + 1, catalogs.size(), catalog.getName());
							progressInfo.setCurrentItemInfo(currentInfo);
							progressInfo.incrementCount();
							
							int percent = (int)(((contentTotal + i + 1) * 100) / progressInfo.totalCount);
							setProgressInfo(percent, currentInfo);
							
							if (catalog.isStaticize()) {
								CatalogStaticizeType staticizer = applicationContext.getBean(CatalogStaticizeType.class);
								staticizer.staticize(catalog.getCatalogId().toString());
								
								String successMsg = String.format("[发布全站] 成功发布栏目 [%d/%d]: %s", 
									i + 1, catalogs.size(), catalog.getName());
								logger.info(successMsg);
								progressInfo.addLog(successMsg);
							} else {
								progressInfo.addLog("跳过未启用静态化的栏目: " + catalog.getName());
							}
						} catch (Exception e) {
							String errorMsg = "[发布全站] 发布栏目失败: " + catalog.getCatalogId() + ", 原因: " + e.getMessage();
							logger.error(errorMsg, e);
							progressInfo.addLog(errorMsg);
						}
					}
					
					// 最后发布首页
					try {
						progressInfo.setCurrentItemInfo("正在发布站点首页");
						setProgressInfo(99, "正在发布站点首页");
						
						// 发布站点首页
						asyncPublishSite(site);
						
						String completeMsg = "[发布全站] 完成发布站点: " + site.getName() + ", 总用时: " + progressInfo.getFormattedElapsedTime();
						logger.info(completeMsg);
						progressInfo.addLog(completeMsg);
						setProgressInfo(100, "发布完成");
						progressInfo.setCurrentItemInfo("发布已完成");
					} catch (Exception e) {
						String errorMsg = "[发布全站] 发布站点首页失败: " + e.getMessage();
						logger.error(errorMsg, e);
						progressInfo.addLog(errorMsg);
					}
					
				} catch (Exception e) {
					String errorMsg = "[发布全站] 发布失败: " + e.getMessage();
					logger.error(errorMsg, e);
					progressInfo.addLog(errorMsg);
					throw new RuntimeException("发布全站失败: " + e.getMessage(), e);
				}
			}
		};
		
		// 保留进度信息一段时间，然后清理
		new Thread(() -> {
			try {
				Thread.sleep(3600000); // 1小时后清理
				publishProgressMap.remove(taskId);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}).start();
		
		asyncTask.setTaskId("SitePublish_" + taskId);
		asyncTask.setType("SitePublish");
		asyncTask.setInterruptible(true);
		this.asyncTaskManager.execute(asyncTask);
		return asyncTask;
	}

	private void asyncPublishSite(CmsSite site) {
		try {
			// 设置异步上下文标志，用于绕过SaToken权限验证
			com.chestnut.common.security.util.SaTokenSecurityAsyncUtils.setupSaTokenAsyncContext();

			// 直接使用静态化服务生成index.shtml文件
			publishStrategy.publish(SiteStaticizeType.TYPE, site.getSiteId().toString());
			logger.info("异步发布站点首页成功：{}", site.getName());
		} catch (Exception e) {
			logger.error("异步发布站点首页出错：" + site.getName(), e);
		} finally {
			// 清除异步上下文标志
			com.chestnut.common.security.util.SaTokenSecurityAsyncUtils.clearSaTokenAsyncContext();
		}
	}

	@Override
	public String getCatalogPageData(CmsCatalog catalog, int pageIndex, boolean listFlag, String publishPipeCode, boolean isPreview)
			throws IOException, TemplateException {
		if (CatalogType_Link.ID.equals(catalog.getCatalogType())) {
			throw new RuntimeException("链接类型栏目无独立页面：" + catalog.getName());
		}
		String templateFilename = catalog.getListTemplate(publishPipeCode);
		if (!listFlag && pageIndex == 1) {
			// 获取首页模板
			String indexTemplate = catalog.getIndexTemplate(publishPipeCode);
			if (StringUtils.isNotEmpty(indexTemplate)) {
				templateFilename = indexTemplate;
			} else {
				listFlag = true;
			}
		}
		CmsSite site = this.siteService.getById(catalog.getSiteId());
		if (StringUtils.isEmpty(templateFilename)) {
			// 站点默认模板
			templateFilename = PublishPipeProp_DefaultListTemplate.getValue(publishPipeCode,
					site.getPublishPipeProps());
		}
		final String template = templateFilename;
		File templateFile = this.templateService.findTemplateFile(site, template, publishPipeCode);
		Assert.notNull(templateFile, () -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(publishPipeCode, template));

		long s = System.currentTimeMillis();
		// 生成静态页面
		String templateKey = SiteUtils.getTemplateKey(site, publishPipeCode, template);
		TemplateContext templateContext = new TemplateContext(templateKey, isPreview, publishPipeCode);
		templateContext.setPageIndex(pageIndex);
		// init template variables
		TemplateUtils.initGlobalVariables(site, templateContext);
		// init templateType variables
		ITemplateType templateType = templateService.getTemplateType(CatalogTemplateType.TypeId);
		templateType.initTemplateData(catalog.getCatalogId(), templateContext);
		// 分页链接
		if (listFlag) {
			String catalogLink = this.catalogService.getCatalogListLink(catalog, 1, publishPipeCode, isPreview);
			templateContext.setFirstFileName(catalogLink);
			templateContext.setOtherFileName(catalogLink + "&pi=" + TemplateContext.PlaceHolder_PageNo);
		}
		try (StringWriter writer = new StringWriter()) {
			this.staticizeService.process(templateContext, writer);
			return writer.toString();
		} finally {
			logger.debug("[{}]栏目页模板解析：{}，耗时：{}ms", publishPipeCode, catalog.getName(),
					(System.currentTimeMillis() - s));
		}
	}

	@Override
	public AsyncTask publishCatalog(CmsCatalog catalog, boolean publishChild, boolean publishDetail,
			final String publishStatus, final LoginUser operator) {
		List<CmsPublishPipe> publishPipes = publishPipeService.getPublishPipes(catalog.getSiteId());
		Assert.isTrue(!publishPipes.isEmpty(), ContentCoreErrorCode.NO_PUBLISHPIPE::exception);

		AsyncTask asyncTask = new AsyncTask() {

			@Override
			public void run0() throws InterruptedException {
				List<CmsCatalog> catalogs = new ArrayList<>();
				catalogs.add(catalog);
				// 是否包含子栏目
				if (publishChild) {
					LambdaQueryWrapper<CmsCatalog> q = new LambdaQueryWrapper<CmsCatalog>()
							.eq(CmsCatalog::getStaticFlag, YesOrNo.YES) // 可静态化
							.eq(CmsCatalog::getVisibleFlag, YesOrNo.YES) // 可见
							.likeRight(CmsCatalog::getAncestors, catalog.getAncestors());
					catalogs.addAll(catalogService.list(q));
				}
				// 先发布内容
				if (publishDetail) {
					for (CmsCatalog catalog : catalogs) {
						int pageSize = 100;
						long lastContentId = 0L;
						long total = contentService.dao().lambdaQuery().eq(CmsContent::getCatalogId, catalog.getCatalogId())
								.eq(CmsContent::getStatus, publishStatus)
								.ne(CmsContent::getLinkFlag, YesOrNo.YES)
								.count();
						int count = 1;
						while (true) {
							LambdaQueryWrapper<CmsContent> q = new LambdaQueryWrapper<CmsContent>()
									.eq(CmsContent::getCatalogId, catalog.getCatalogId())
									.eq(CmsContent::getStatus, publishStatus)
									.ne(CmsContent::getLinkFlag, YesOrNo.YES)
									.gt(CmsContent::getContentId, lastContentId)
									.orderByAsc(CmsContent::getContentId);
							Page<CmsContent> page = contentService.dao().page(new Page<>(0, pageSize, false), q);
							for (CmsContent xContent : page.getRecords()) {
								this.setProgressInfo((int) (count * 100 / total),
										"正在发布内容：" + catalog.getName() + "[" + count + " / " + total + "]");
								lastContentId = xContent.getContentId();
								IContentType contentType = ContentCoreUtils.getContentType(xContent.getContentType());
								IContent<?> content = contentType.newContent();
								content.setContentEntity(xContent);
								content.setOperator(operator);
								content.publish();
								this.checkInterrupt();
								count++;
							}
							if (page.getRecords().size() < pageSize) {
								break;
							}
						}
					}
				}
				// 发布栏目
				for (int i = 0; i < catalogs.size(); i++) {
					CmsCatalog catalog = catalogs.get(i);
					this.setProgressInfo((i * 100) / catalogs.size(), "正在发布栏目：" + catalog.getName());
					asyncPublishCatalog(catalog);
					this.checkInterrupt(); // 允许中断
				}
				// 发布站点
				this.setPercent(99);
				asyncPublishSite(siteService.getSite(catalog.getSiteId()));
				this.setProgressInfo(100, "发布完成");
			}
		};
		asyncTask.setType("Publish");
		asyncTask.setTaskId("Publish-Catalog-" + catalog.getCatalogId());
		asyncTask.setInterruptible(true);
		this.asyncTaskManager.execute(asyncTask);
		return asyncTask;
	}

	public void asyncPublishCatalog(final CmsCatalog catalog) {
		if (CatalogType_Link.ID.equals(catalog.getCatalogType())) {
			logger.info("跳过链接类型栏目：{}", catalog.getName());
			return; // 链接栏目直接跳过
		}
		if (!catalog.isStaticize()) {
			logger.info("跳过未开启静态化的栏目：{}", catalog.getName());
			return; // 不静态化的栏目直接跳过
		}
		try {
			// 设置异步上下文标志，用于绕过SaToken权限验证
			com.chestnut.common.security.util.SaTokenSecurityAsyncUtils.setupSaTokenAsyncContext();

			// 执行发布操作，直接使用静态化服务生成栏目页面
			logger.info("开始发布栏目：{}", catalog.getName());
			publishStrategy.publish(CatalogStaticizeType.TYPE, catalog.getCatalogId().toString());
			logger.info("栏目发布成功：{}", catalog.getName());
		} catch (Exception e) {
			logger.error("发布栏目出错：" + catalog.getName(), e);
		} finally {
			// 清除异步上下文标志
			com.chestnut.common.security.util.SaTokenSecurityAsyncUtils.clearSaTokenAsyncContext();
		}
	}

	private String getDetailTemplate(CmsSite site, CmsCatalog catalog, CmsContent content, String publishPipeCode) {
		String detailTemplate = PublishPipeProp_ContentTemplate.getValue(publishPipeCode,
				content.getPublishPipeProps());
		if (StringUtils.isEmpty(detailTemplate)) {
			// 无内容独立模板取栏目配置
			detailTemplate = this.publishPipeService.getPublishPipePropValue(
					IPublishPipeProp.DetailTemplatePropPrefix + content.getContentType(), publishPipeCode,
					catalog.getPublishPipeProps());
			if (StringUtils.isEmpty(detailTemplate)) {
				// 无栏目配置去站点默认模板配置
				detailTemplate = this.publishPipeService.getPublishPipePropValue(
						IPublishPipeProp.DefaultDetailTemplatePropPrefix + content.getContentType(), publishPipeCode,
						site.getPublishPipeProps());
			}
		}
		return detailTemplate;
	}

	@Override
	public String getContentPageData(CmsContent content, int pageIndex, String publishPipeCode, boolean isPreview)
			throws IOException, TemplateException {
		CmsSite site = this.siteService.getById(content.getSiteId());
		CmsCatalog catalog = this.catalogService.getCatalog(content.getCatalogId());
		if (content.isLinkContent()) {
			throw new RuntimeException("标题内容：" + content.getTitle() + "，跳转链接：" + content.getRedirectUrl());
		}
		// 查找模板
		final String detailTemplate = getDetailTemplate(site, catalog, content, publishPipeCode);
		File templateFile = this.templateService.findTemplateFile(site, detailTemplate, publishPipeCode);
		Assert.notNull(templateFile,
				() -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(publishPipeCode, detailTemplate));

		long s = System.currentTimeMillis();
		// 生成静态页面
		try (StringWriter writer = new StringWriter()) {
			IContentType contentType = ContentCoreUtils.getContentType(content.getContentType());
			// 模板ID = 通道:站点目录:模板文件名
			String templateKey = SiteUtils.getTemplateKey(site, publishPipeCode, detailTemplate);
			TemplateContext templateContext = new TemplateContext(templateKey, isPreview, publishPipeCode);
			templateContext.setPageIndex(pageIndex);
			// init template datamode
			TemplateUtils.initGlobalVariables(site, templateContext);
			// init templateType data to datamode
			ITemplateType templateType = this.templateService.getTemplateType(ContentTemplateType.TypeId);
			templateType.initTemplateData(content.getContentId(), templateContext);
			// 分页链接
			String contentLink = this.contentService.getContentLink(content, 1, publishPipeCode, isPreview);
			templateContext.setFirstFileName(contentLink);
			templateContext.setOtherFileName(contentLink + "&pi=" + TemplateContext.PlaceHolder_PageNo);
			// staticize
			this.staticizeService.process(templateContext, writer);
			logger.debug("[{}][{}]内容模板解析：{}，耗时：{}", publishPipeCode, contentType.getName(), content.getTitle(),
					System.currentTimeMillis() - s);
			return writer.toString();
		}
	}

	/**
	 * 内容发布
	 */
	@Override
	public void publishContent(List<Long> contentIds, LoginUser operator) {
		publishContent(contentIds, operator, "ContentBatchPublish_" + UUID.randomUUID().toString());
	}

	/**
	 * 内容发布
	 * 
	 * @param contentIds 内容ID列表
	 * @param operator 操作者
	 * @param taskId 任务ID
	 */
	public void publishContent(List<Long> contentIds, LoginUser operator, String taskId) {
		List<CmsContent> list = this.contentService.dao().listByIds(contentIds);
		if (list.isEmpty()) {
			return;
		}

		// 创建进度信息
		PublishProgressInfo progressInfo = new PublishProgressInfo(list.size());
		publishProgressMap.put(taskId, progressInfo);
		progressInfo.addLog("开始批量发布内容，总数: " + list.size());

		// 优化批量发布处理
		AsyncTask asyncTask = new AsyncTask() {
			@Override
			public void run0() throws Exception {
				try {
					// 发布内容 - 分批处理以避免一次性创建过多任务
					Set<Long> catalogIds = new HashSet<>();
					int batchSize = 10; // 每批处理的内容数量
					int totalSize = list.size();
					int totalPublished = 0;

					logger.info("开始批量发布内容，总数: {}, 任务ID: {}", totalSize, taskId);
					setTaskId("ContentPublish_" + taskId);
					
					for (int i = 0; i < totalSize; i += batchSize) {
						int endIndex = Math.min(i + batchSize, totalSize);
						List<CmsContent> batch = list.subList(i, endIndex);

						// 处理当前批次
						for (CmsContent cmsContent : batch) {
							Long contentId = cmsContent.getContentId();
							taskStatusMap.put(contentId, false);
							taskStartTimeMap.put(contentId, System.currentTimeMillis());
							
							try {
								// 设置进度信息
								String currentInfo = String.format("正在发布: %s (ID: %d)", 
									StringUtils.substring(cmsContent.getTitle(), 0, 30), contentId);
								progressInfo.setCurrentItemInfo(currentInfo);
								
								// 更新任务进度
								setProgressInfo(progressInfo.getPercent(), currentInfo);
								
								// 检查任务是否超时
								if (isTaskTimeout(contentId)) {
									String msg = "内容发布任务超时，跳过: " + contentId;
									logger.warn(msg);
									progressInfo.addLog(msg);
									continue;
								}
								
								// 记录栏目ID用于后续发布栏目
								catalogIds.add(cmsContent.getCatalogId());
								
								// 1. 更新内容状态为已发布
								if (!ContentStatus.isPublished(cmsContent.getStatus())) {
									cmsContent.setStatus(ContentStatus.PUBLISHED);
									contentService.dao().updateById(cmsContent);
									String msg = "更新内容状态为已发布，内容ID: " + cmsContent.getContentId();
									logger.info(msg);
									progressInfo.addLog(msg);
								}
								
								// 2. 使用重试机制发布内容
								boolean success = tryPublishWithRetry(cmsContent.getContentId().toString(), MAX_RETRY_COUNT);
								
								if (success) {
									// 3. 重要: 发布事件，以便更新搜索索引
									try {
										// 检查应用上下文是否处于活动状态
										if (applicationContext instanceof AbstractApplicationContext) {
											AbstractApplicationContext context = (AbstractApplicationContext) applicationContext;
											if (context.isActive() && context.isRunning()) {
												// 只有当上下文活动且正在运行时才触发事件
												IContentType contentType = ContentCoreUtils.getContentType(cmsContent.getContentType());
												IContent<?> iContent = contentType.loadContent(cmsContent);
												applicationContext.publishEvent(new AfterContentPublishEvent(PublishServiceImpl.this, iContent));
												logger.info("[发布全站] 已触发内容索引更新: {}", cmsContent.getContentId());
											} else {
												logger.warn("[发布全站] 应用上下文已关闭或不活动，跳过触发内容索引更新: {}", cmsContent.getContentId());
											}
										} else {
											// 如果不是AbstractApplicationContext，则直接发布事件
											IContentType contentType = ContentCoreUtils.getContentType(cmsContent.getContentType());
											IContent<?> iContent = contentType.loadContent(cmsContent);
											applicationContext.publishEvent(new AfterContentPublishEvent(PublishServiceImpl.this, iContent));
											logger.info("[发布全站] 已触发内容索引更新: {}", cmsContent.getContentId());
										}
									} catch (Exception e) {
										logger.error("[发布全站] 触发内容索引更新失败: " + cmsContent.getContentId(), e);
									}
									
									taskStatusMap.put(contentId, true);
									totalPublished++;
									progressInfo.incrementCount();
								} else {
									String msg = "内容发布失败，内容ID: " + contentId;
									logger.error(msg);
									progressInfo.addLog(msg);
								}
							} catch (Exception e) {
								String msg = "内容发布处理异常，内容ID: " + contentId + ", " + e.getMessage();
								logger.error(msg, e);
								progressInfo.addLog(msg);
							}
						}
					}
					
					progressInfo.setCurrentItemInfo("批量发布内容完成，成功发布：" + totalPublished + "/" + totalSize);
					logger.info("批量发布内容完成，成功率: {}/{}", totalPublished, totalSize);
					
					// 发布相关栏目
					if (!catalogIds.isEmpty()) {
						publishCatalogs(catalogIds, operator, progressInfo);
					}
				} catch (Exception e) {
					String errorMsg = "发布内容任务异常: " + e.getMessage();
					logger.error(errorMsg, e);
					progressInfo.addLog(errorMsg);
					throw e;
				}
			}
		};
		asyncTask.setType("PublishContent");
		this.asyncTaskManager.execute(asyncTask);
	}

	/**
	 * 检查任务是否超时
	 */
	private boolean isTaskTimeout(Long contentId) {
		Long startTime = taskStartTimeMap.get(contentId);
		if (startTime == null) {
			return false;
		}
		return System.currentTimeMillis() - startTime > TASK_TIMEOUT;
	}

	/**
	 * 使用重试机制发布内容
	 */
	private boolean tryPublishWithRetry(String id, int retryCount) {
		try {
			// 直接使用ContentStaticizeType实例进行静态化
			ContentStaticizeType contentStaticizeType = applicationContext.getBean(ContentStaticizeType.class);
			// 调用静态化方法，会先删除旧文件再重新生成
			contentStaticizeType.staticize(id);
			return true;
		} catch (Exception e) {
			if (retryCount > 0) {
				logger.warn("发布失败，重试中: " + id + ", 剩余重试次数: " + retryCount);
				try {
					Thread.sleep(1000); // 重试前等待1秒
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
				return tryPublishWithRetry(id, retryCount - 1);
			} else {
				logger.error("发布失败: " + id, e);
				return false;
			}
		}
	}

	/**
	 * 发布相关栏目
	 */
	private void publishCatalogs(Set<Long> catalogIds, LoginUser operator, PublishProgressInfo progressInfo) {
		int total = catalogIds.size();
		int current = 0;
		
		for (Long catalogId : catalogIds) {
			current++;
			try {
				CmsCatalog catalog = catalogService.getCatalog(catalogId);
				if (catalog != null) {
					String msg = String.format("发布栏目 [%d/%d]: %s (ID: %d)", 
						current, total, catalog.getName(), catalog.getCatalogId());
					logger.info(msg);
					progressInfo.addLog(msg);
					
					// 使用异步任务发布栏目
					publishCatalog(catalog, false, false, null, operator);
				}
			} catch (Exception e) {
				String msg = "发布栏目失败: " + catalogId + ", 原因: " + e.getMessage();
				logger.error(msg, e);
				progressInfo.addLog(msg);
			}
		}
		
		progressInfo.addLog("栏目发布任务已提交，共: " + total + " 个栏目");
	}

	@Override
	public void asyncPublishContent(IContent<?> content) {
		try {
			// 设置异步上下文标志，用于绕过SaToken权限验证
			com.chestnut.common.security.util.SaTokenSecurityAsyncUtils.setupSaTokenAsyncContext();
			
			CmsCatalog catalog = this.catalogService.getCatalog(content.getCatalogId());
			if (!catalog.isStaticize()) {
				// 即使不静态化，也要更新内容状态为已发布
				updateContentStatus(content.getContentEntity());
				applicationContext.publishEvent(new AfterContentPublishEvent(this, content));
				return;
			}
			List<CmsPublishPipe> publishPipeCodes = this.publishPipeService.getPublishPipes(content.getSiteId());
			if (publishPipeCodes.isEmpty()) {
				// 即使没有发布通道，也要更新内容状态为已发布
				updateContentStatus(content.getContentEntity());
				applicationContext.publishEvent(new AfterContentPublishEvent(this, content));
				return;
			}

			// 确保内容状态已更新为已发布
			updateContentStatus(content.getContentEntity());

			// 使用重试机制发布内容
			publishStrategy.publish(ContentStaticizeType.TYPE, content.getContentEntity().getContentId().toString());

			// 发布事件
			applicationContext.publishEvent(new AfterContentPublishEvent(this, content));

			// 关联内容静态化，映射的引用内容
			LambdaQueryWrapper<CmsContent> q = new LambdaQueryWrapper<CmsContent>()
					.eq(CmsContent::getCopyId, content.getContentEntity().getContentId())
					.eq(CmsContent::getCopyType, ContentCopyType.Mapping);
			List<CmsContent> mappingContents = contentService.dao().list(q);
			for (CmsContent mappingContent : mappingContents) {
				// 同步更新映射内容状态
				updateContentStatus(mappingContent);
				// 使用重试机制发布映射内容
				publishStrategy.publish(ContentStaticizeType.TYPE, mappingContent.getContentId().toString());
				
				// 为映射内容发布事件
				IContentType contentType = ContentCoreUtils.getContentType(mappingContent.getContentType());
				IContent<?> mappingContentObj = contentType.loadContent(mappingContent);
				applicationContext.publishEvent(new AfterContentPublishEvent(this, mappingContentObj));
			}
		} catch (Exception e) {
			logger.error("异步发布内容失败，内容ID: " + content.getContentEntity().getContentId(), e);
		} finally {
			// 清除异步上下文标志
			com.chestnut.common.security.util.SaTokenSecurityAsyncUtils.clearSaTokenAsyncContext();
		}
	}

	/**
	 * 更新内容状态为已发布
	 */
	private void updateContentStatus(CmsContent contentEntity) {
		if (!ContentStatus.isPublished(contentEntity.getStatus())) {
			contentEntity.setStatus(ContentStatus.PUBLISHED);
			contentService.dao().updateById(contentEntity);
			logger.info("更新内容状态为已发布，内容ID: {}", contentEntity.getContentId());
		}
	}

	@Override
	public String getContentExPageData(CmsContent content, String publishPipeCode, boolean isPreview)
			throws IOException, TemplateException {
		CmsSite site = this.siteService.getById(content.getSiteId());
		CmsCatalog catalog = this.catalogService.getCatalog(content.getCatalogId());
		if (!catalog.isStaticize() ) {
			throw new RuntimeException("栏目设置不静态化：" + content.getTitle());
		}
		if (content.isLinkContent()) {
			throw new RuntimeException("标题内容：" + content.getTitle() + "，跳转链接：" + content.getRedirectUrl());
		}
		String exTemplate = ContentUtils.getContentExTemplate(content, catalog, publishPipeCode);
		// 查找模板
		File templateFile = this.templateService.findTemplateFile(site, exTemplate, publishPipeCode);
		Assert.notNull(templateFile,
				() -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(publishPipeCode, exTemplate));

		long s = System.currentTimeMillis();
		// 生成静态页面
		try (StringWriter writer = new StringWriter()) {
			IContentType contentType = ContentCoreUtils.getContentType(content.getContentType());
			// 模板ID = 通道:站点目录:模板文件名
			String templateKey = SiteUtils.getTemplateKey(site, publishPipeCode, exTemplate);
			TemplateContext templateContext = new TemplateContext(templateKey, isPreview, publishPipeCode);
			// init template data mode
			TemplateUtils.initGlobalVariables(site, templateContext);
			// init templateType data to data mode
			ITemplateType templateType = this.templateService.getTemplateType(ContentTemplateType.TypeId);
			templateType.initTemplateData(content.getContentId(), templateContext);
			// staticize
			this.staticizeService.process(templateContext, writer);
			logger.debug("[{}][{}]内容扩展模板解析：{}，耗时：{}", publishPipeCode, contentType.getName(), content.getTitle(),
					System.currentTimeMillis() - s);
			return writer.toString();
		}
	}

	@Override
	public String getPageWidgetPageData(CmsPageWidget pageWidget, boolean isPreview)
			throws IOException, TemplateException {
		CmsSite site = this.siteService.getById(pageWidget.getSiteId());
		File templateFile = this.templateService.findTemplateFile(site, pageWidget.getTemplate(),
				pageWidget.getPublishPipeCode());
		Assert.notNull(templateFile,
				() -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(pageWidget.getName(), pageWidget.getCode()));

		// 生成静态页面
		try (StringWriter writer = new StringWriter()) {
			long s = System.currentTimeMillis();
			// 模板ID = 通道:站点目录:模板文件名
			String templateKey = SiteUtils.getTemplateKey(site, pageWidget.getPublishPipeCode(),
					pageWidget.getTemplate());
			TemplateContext templateContext = new TemplateContext(templateKey, isPreview,
					pageWidget.getPublishPipeCode());
			// init template global variables
			TemplateUtils.initGlobalVariables(site, templateContext);
			templateContext.getVariables().put(TemplateUtils.TemplateVariable_PageWidget, pageWidget);
			// init templateType data to datamode
			ITemplateType templateType = this.templateService.getTemplateType(SiteTemplateType.TypeId);
			templateType.initTemplateData(site.getSiteId(), templateContext);
			// staticize
			this.staticizeService.process(templateContext, writer);
			logger.debug("[{}]页面部件【{}#{}】模板解析耗时：{}ms", pageWidget.getPublishPipeCode(), pageWidget.getName(),
					pageWidget.getCode(), System.currentTimeMillis() - s);
			return writer.toString();
		}
	}

	@Override
	public void pageWidgetStaticize(IPageWidget pageWidget) {
		long s = System.currentTimeMillis();
		CmsPageWidget pw = pageWidget.getPageWidgetEntity();
		CmsSite site = this.siteService.getSite(pw.getSiteId());
		File templateFile = this.templateService.findTemplateFile(site, pw.getTemplate(), pw.getPublishPipeCode());
		if (Objects.isNull(templateFile)) {
			logger.warn(StringUtils.messageFormat("页面部件【{0}%s#{1}%s】模板未配置或文件不存在", pw.getName(), pw.getCode()));
			return;
		}
		try {
			// 静态化目录
			String dirPath = SiteUtils.getSiteRoot(site, pw.getPublishPipeCode()) + pw.getPath();
			FileExUtils.mkdirs(dirPath);
			// 自定义模板上下文
			String templateKey = SiteUtils.getTemplateKey(site, pw.getPublishPipeCode(), pw.getTemplate());
			TemplateContext templateContext = new TemplateContext(templateKey, false, pw.getPublishPipeCode());
			templateContext.setDirectory(dirPath);
			String staticFileName = PageWidgetUtils.getStaticFileName(pw, site.getStaticSuffix(pw.getPublishPipeCode()));
			templateContext.setFirstFileName(staticFileName);
			// init template datamode
			TemplateUtils.initGlobalVariables(site, templateContext);
			templateContext.getVariables().put(TemplateUtils.TemplateVariable_PageWidget, pw);
			// init templateType data to datamode
			ITemplateType templateType = templateService.getTemplateType(SiteTemplateType.TypeId);
			templateType.initTemplateData(site.getSiteId(), templateContext);
			// staticize
			this.staticizeService.process(templateContext);
			logger.debug("[{}]页面部件模板解析：{}，耗时：{}ms", pw.getPublishPipeCode(), pw.getCode(), System.currentTimeMillis() - s);
		} catch (TemplateException | IOException e) {
			logger.error(AsyncTaskManager.addErrMessage(StringUtils.messageFormat("[{0}]页面部件模板解析失败：{1}#{2}",
					pw.getPublishPipeCode(), pw.getName(), pw.getCode())), e);
		}
	}

	@Override
	public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public List<Map<String, Object>> checkPublishStatus(Long siteId) {
		CmsSite site = this.siteService.getSite(siteId);
		if (site == null) {
			throw new RuntimeException("站点不存在: " + siteId);
		}

		// 静态文件根目录
		String staticRoot = "D:/item/guanwang/gzmdrw_site_james/wwwroot_release/gzmdrw_pc/";
		
		// 查询所有已发布的内容
		List<CmsContent> contents = this.contentService.dao().lambdaQuery()
				.eq(CmsContent::getSiteId, siteId)
				.eq(CmsContent::getStatus, ContentStatus.PUBLISHED)
				.ne(CmsContent::getLinkFlag, YesOrNo.YES)
				.list();
		
		logger.info("站点 {} 共有 {} 篇已发布内容需要检查", site.getName(), contents.size());
		
		// 检查每篇内容对应的shtml文件是否存在且是否为最新
		List<Map<String, Object>> failedList = new ArrayList<>();
		for (CmsContent content : contents) {
			CmsCatalog catalog = catalogService.getCatalog(content.getCatalogId());
			if (catalog == null || !catalog.isStaticize() || CatalogType_Link.ID.equals(catalog.getCatalogType())) {
				// 跳过不需要静态化的内容或无效栏目
				continue;
			}
			
			// 检查文件是否存在
			String filePath = staticRoot + catalog.getPath() + content.getContentId() + ".shtml";
			File htmlFile = new File(filePath);
			
			boolean needRepublish = false;
			String reason = "";
			
			if (!htmlFile.exists() || htmlFile.length() == 0) {
				needRepublish = true;
				reason = "文件不存在或大小为0";
			} else {
				// 文件存在，检查修改时间
				long fileLastModified = htmlFile.lastModified();
				long contentUpdateTime = 0;
				if (content.getUpdateTime() != null) {
					contentUpdateTime = content.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				} else if (content.getPublishDate() != null) {
					contentUpdateTime = content.getPublishDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				}
				
				// 如果内容更新时间比文件修改时间新，则需要重新发布
				if (contentUpdateTime > fileLastModified) {
					needRepublish = true;
					reason = "内容已更新但文件未更新";
				}
			}
			
			if (needRepublish) {
				Map<String, Object> failInfo = new HashMap<>();
				failInfo.put("contentId", content.getContentId());
				failInfo.put("title", content.getTitle());
				failInfo.put("catalogName", catalog.getName());
				failInfo.put("staticPath", filePath);
				failInfo.put("publishDate", content.getPublishDate());
				failInfo.put("updateTime", content.getUpdateTime());
				failInfo.put("reason", reason);
				failedList.add(failInfo);
			}
		}
		
		logger.info("站点 {} 共有 {} 篇内容需要重新发布", site.getName(), failedList.size());
		return failedList;
	}

	/**
	 * 获取所有已发布的内容，用于检查静态文件状态
	 * 
	 * @param siteId 站点ID
	 * @return 已发布内容列表
	 */
	public List<Map<String, Object>> getPublishedContents(Long siteId) {
		List<Map<String, Object>> results = new ArrayList<>();
		
		LambdaQueryWrapper<CmsContent> q = new LambdaQueryWrapper<CmsContent>()
				.eq(CmsContent::getSiteId, siteId)
				.eq(CmsContent::getStatus, ContentStatus.PUBLISHED);
		
		List<CmsContent> contents = this.contentService.dao().list(q);
		if (contents != null && !contents.isEmpty()) {
			logger.info("Found {} published contents for site: {}", contents.size(), siteId);
			for (CmsContent content : contents) {
				Map<String, Object> map = new HashMap<>();
				map.put("contentId", content.getContentId());
				map.put("title", content.getTitle());
				map.put("staticPath", content.getStaticPath());
				map.put("catalogId", content.getCatalogId());
				map.put("contentType", content.getContentType());
				results.add(map);
			}
		}
		
		return results;
	}

	/**
	 * 获取当前活动任务数量
	 * 
	 * @return 活动任务数量
	 */
	public int getActiveTaskCount() {
		try {
			int size = publishProgressMap.size();
			logger.debug("当前发布进度映射大小: {}", size);
			
			// 检查其他集合状态，以便调试
			logger.debug("taskFutures 大小: {}", taskFutures.size());
			logger.debug("taskProgressInfo 大小: {}", taskProgressInfo.size());
			logger.debug("taskCreateTimes 大小: {}", taskCreateTimes.size());
			logger.debug("taskStatusMap 大小: {}", taskStatusMap.size());
			logger.debug("taskStartTimeMap 大小: {}", taskStartTimeMap.size());
			
			return size;
		} catch (Exception e) {
			logger.error("获取活动任务数量出错", e);
			return 0; // 出错时返回0
		}
	}
	
	/**
	 * 清空所有发布任务
	 */
	public boolean clearPublishTasks() {
		try {
			// 取消所有进行中的任务
			for (Map.Entry<String, Future<?>> entry : this.taskFutures.entrySet()) {
				String taskId = entry.getKey();
				Future<?> future = entry.getValue();
				
				if (!future.isDone()) {
					future.cancel(true);
					logger.info("Publish task cancelled: {}", taskId);
				}
			}
			
			// 清空相关数据结构
			this.taskFutures.clear();
			this.taskProgressInfo.clear();
			this.taskCreateTimes.clear();
			this.publishProgressMap.clear();
			this.taskStatusMap.clear();
			this.taskStartTimeMap.clear();
			
			logger.info("All publish tasks have been cleared successfully");
			return true;
		} catch (Exception e) {
			logger.error("Failed to clear publish tasks", e);
			return false;
		}
	}
	
	/**
	 * 创建测试任务（仅用于测试）
	 */
	public void createTestTask() {
		String testTaskId = "TestTask_" + UUID.randomUUID().toString();
		PublishProgressInfo progress = new PublishProgressInfo(100);
		progress.addLog("测试任务创建成功");
		publishProgressMap.put(testTaskId, progress);
	}

	@Override
	public Map<String, Object> getTaskDetails(String taskId) {
		Map<String, Object> details = new HashMap<>();
		if (StringUtils.isEmpty(taskId)) {
			return details;
		}
		
		Future<?> future = this.taskFutures.get(taskId);
		if (future == null) {
			details.put("status", "not_found");
			return details;
		}
		
		details.put("taskId", taskId);
		details.put("status", future.isDone() ? (future.isCancelled() ? "cancelled" : "completed") : "running");
		details.put("createTime", this.taskCreateTimes.getOrDefault(taskId, System.currentTimeMillis()));
		
		// 添加任务进度信息（如果有）
		Object progressInfo = this.taskProgressInfo.get(taskId);
		if (progressInfo != null) {
			details.put("progress", progressInfo);
		}
		
		return details;
	}
	
	@Override
	public boolean cancelTask(String taskId) {
		if (StringUtils.isEmpty(taskId)) {
			return false;
		}
		
		Future<?> future = this.taskFutures.get(taskId);
		if (future == null || future.isDone()) {
			return false;
		}
		
		boolean result = future.cancel(true);
		if (result) {
			logger.info("Publish task cancelled: {}", taskId);
			// 清理相关资源
			this.taskFutures.remove(taskId);
			this.taskProgressInfo.remove(taskId);
			this.taskCreateTimes.remove(taskId);
		}
		return result;
	}
	
	@Override
	public List<Map<String, Object>> getActiveTasks() {
		List<Map<String, Object>> tasks = new ArrayList<>();
		
		for (Map.Entry<String, Future<?>> entry : this.taskFutures.entrySet()) {
			String taskId = entry.getKey();
			Future<?> future = entry.getValue();
			
			if (!future.isDone()) {
				Map<String, Object> taskInfo = new HashMap<>();
				taskInfo.put("taskId", taskId);
				taskInfo.put("status", "running");
				taskInfo.put("createTime", this.taskCreateTimes.getOrDefault(taskId, System.currentTimeMillis()));
				
				// 添加任务进度信息（如果有）
				Object progressInfo = this.taskProgressInfo.get(taskId);
				if (progressInfo != null) {
					taskInfo.put("progress", progressInfo);
				}
				
				tasks.add(taskInfo);
			}
		}
		
		return tasks;
	}

	/**
	 * 发布待发布内容
	 * 
	 * @param contentIds 内容ID列表
	 * @param operator 操作者
	 * @param taskId 任务ID
	 */
	public void publishToPublishContent(List<Long> contentIds, LoginUser operator, String taskId) {
		List<CmsContent> list = this.contentService.dao().listByIds(contentIds);
		if (list.isEmpty()) {
			return;
		}

		// 创建进度信息
		PublishProgressInfo progressInfo = new PublishProgressInfo(list.size());
		publishProgressMap.put(taskId, progressInfo);
		progressInfo.addLog("开始批量发布待发布内容，总数: " + list.size());

		// 获取内容静态化处理器
		ContentStaticizeType staticizer = applicationContext.getBean(ContentStaticizeType.class);

		// 优化批量发布处理
		AsyncTask asyncTask = new AsyncTask() {
			@Override
			public void run0() throws Exception {
				try {
					// 发布内容 - 分批处理以避免一次性创建过多任务
					Set<Long> catalogIds = new HashSet<>();
					int batchSize = 10; // 每批处理的内容数量
					int totalSize = list.size();
					int totalPublished = 0;

					logger.info("开始批量发布待发布内容，总数: {}, 任务ID: {}", totalSize, taskId);
					setTaskId("ContentToPublishBatch_" + taskId);
					
					for (int i = 0; i < totalSize; i += batchSize) {
						int endIndex = Math.min(i + batchSize, totalSize);
						List<CmsContent> batch = list.subList(i, endIndex);

						// 处理当前批次
						for (CmsContent cmsContent : batch) {
							Long contentId = cmsContent.getContentId();
							taskStatusMap.put(contentId, false);
							taskStartTimeMap.put(contentId, System.currentTimeMillis());
							
							try {
								// 设置进度信息
								String currentInfo = String.format("正在发布: %s (ID: %d)", 
									StringUtils.substring(cmsContent.getTitle(), 0, 30), contentId);
								progressInfo.setCurrentItemInfo(currentInfo);
								
								// 更新任务进度
								setProgressInfo(progressInfo.getPercent(), currentInfo);
								
								// 检查任务是否超时
								if (isTaskTimeout(contentId)) {
									String msg = "内容发布任务超时，跳过: " + contentId;
									logger.warn(msg);
									progressInfo.addLog(msg);
									continue;
								}
								
								// 记录栏目ID用于后续发布栏目
								catalogIds.add(cmsContent.getCatalogId());
								
								// 1. 更新内容状态为已发布
								if (!ContentStatus.isPublished(cmsContent.getStatus())) {
									cmsContent.setStatus(ContentStatus.PUBLISHED);
									contentService.dao().updateById(cmsContent);
									String msg = "更新内容状态为已发布，内容ID: " + cmsContent.getContentId();
									logger.info(msg);
									progressInfo.addLog(msg);
								}
								
								// 2. 直接使用ContentStaticizeType生成静态文件
								staticizer.staticize(cmsContent.getContentId().toString());
								
								// 3. 重要: 发布事件，以便更新搜索索引
								try {
									// 检查应用上下文是否处于活动状态
									if (applicationContext instanceof AbstractApplicationContext) {
										AbstractApplicationContext context = (AbstractApplicationContext) applicationContext;
										if (context.isActive() && context.isRunning()) {
											// 只有当上下文活动且正在运行时才触发事件
											IContentType contentType = ContentCoreUtils.getContentType(cmsContent.getContentType());
											IContent<?> iContent = contentType.loadContent(cmsContent);
											applicationContext.publishEvent(new AfterContentPublishEvent(PublishServiceImpl.this, iContent));
											logger.info("[发布全站] 已触发内容索引更新: {}", cmsContent.getContentId());
										} else {
											logger.warn("[发布全站] 应用上下文已关闭或不活动，跳过触发内容索引更新: {}", cmsContent.getContentId());
										}
									} else {
										// 如果不是AbstractApplicationContext，则直接发布事件
										IContentType contentType = ContentCoreUtils.getContentType(cmsContent.getContentType());
										IContent<?> iContent = contentType.loadContent(cmsContent);
										applicationContext.publishEvent(new AfterContentPublishEvent(PublishServiceImpl.this, iContent));
										logger.info("[发布全站] 已触发内容索引更新: {}", cmsContent.getContentId());
									}
								} catch (Exception e) {
									logger.error("[发布全站] 触发内容索引更新失败: " + cmsContent.getContentId(), e);
								}
								
								taskStatusMap.put(contentId, true);
								totalPublished++;
								progressInfo.incrementCount();
							} catch (Exception e) {
								String msg = "内容发布处理异常，内容ID: " + contentId + ", " + e.getMessage();
								logger.error(msg, e);
								progressInfo.addLog(msg);
							}
						}
					}
					
					progressInfo.setCurrentItemInfo("批量发布待发布内容完成，成功发布：" + totalPublished + "/" + totalSize);
					logger.info("批量发布待发布内容完成，成功率: {}/{}", totalPublished, totalSize);
					
					// 发布相关栏目
					if (!catalogIds.isEmpty()) {
						publishCatalogs(catalogIds, operator, progressInfo);
					}
				} catch (Exception e) {
					String errorMsg = "发布待发布内容任务异常: " + e.getMessage();
					logger.error(errorMsg, e);
					progressInfo.addLog(errorMsg);
					throw e;
				}
			}
		};
		asyncTask.setType("PublishToPublishContent");
		this.asyncTaskManager.execute(asyncTask);
	}
}
