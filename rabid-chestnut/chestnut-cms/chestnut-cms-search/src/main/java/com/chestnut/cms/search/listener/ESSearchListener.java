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
package com.chestnut.cms.search.listener;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.chestnut.cms.search.service.ContentIndexService;
import com.chestnut.contentcore.core.IContent;
import com.chestnut.contentcore.domain.CmsContent;
import com.chestnut.contentcore.domain.CmsSite;
import com.chestnut.contentcore.listener.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ESSearchListener {

	private final ContentIndexService contentIndexService;

	@EventListener
	public void afterSiteDelete(AfterSiteDeleteEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		CmsSite site = event.getSite();
		contentIndexService.deleteIndex(site);
	}

	@EventListener
	public void afterSiteAdd(AfterSiteAddEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		contentIndexService.createIndex(event.getSite());
	}

	@EventListener
	public void afterContentDelete(AfterContentDeleteEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		CmsContent content = event.getContent().getContentEntity();
		try {
			log.info("Content deleted, removing from search index, content ID: {}", content.getContentId());
			this.contentIndexService.deleteContentDoc(content.getSiteId(), List.of(content.getContentId()));
		} catch (ElasticsearchException | IOException e) {
			log.error("Delete content from search index failed: {}", content.getContentId(), e);
		}
	}
	
	/**
	 * 监听内容保存事件，同步更新索引
	 */
	@EventListener
	public void afterContentSave(AfterContentSaveEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		
		IContent<?> content = event.getContent();
		log.info("Content saved, updating search index based on content status for content ID: {}", 
				content.getContentEntity().getContentId());
		
		// 根据内容状态处理索引
		this.contentIndexService.handleContentByStatus(content);
	}

	/**
	 * 监听内容待发布状态变更事件
	 */
	@EventListener
	public void afterContentToPublish(AfterContentToPublishEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		
		IContent<?> content = event.getContent();
		log.info("Content changed to TO_PUBLISH status, handling search index for content ID: {}", 
				content.getContentEntity().getContentId());
		
		this.contentIndexService.handleContentByStatus(content);
	}

	@EventListener
	public void afterContentPublish(AfterContentPublishEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		
		IContent<?> content = event.getContent();
		log.info("Content published, handling search index for content ID: {}", 
				content.getContentEntity().getContentId());
		
		this.contentIndexService.handleContentByStatus(content);
	}

	@EventListener
	public void afterContentOfflineEvent(AfterContentOfflineEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		
		IContent<?> content = event.getContent();
		log.info("Content set to offline, handling search index for content ID: {}", 
				content.getContentEntity().getContentId());
		
		this.contentIndexService.handleContentByStatus(content);
	}

	@EventListener
	public void afterCatalogMoveEvent(AfterCatalogMoveEvent event) {
		if (!contentIndexService.isElasticSearchAvailable()) {
			return;
		}
		try {
			this.contentIndexService.rebuildCatalog(event.getFromCatalog(), true);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
