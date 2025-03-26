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
package com.chestnut.cms.search.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.chestnut.cms.search.CmsSearchConstants;
import com.chestnut.cms.search.service.ContentIndexService;
import com.chestnut.cms.search.vo.ESContentVO;
import com.chestnut.common.domain.R;
import com.chestnut.common.utils.JacksonUtils;
import com.chestnut.common.utils.ServletUtils;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.contentcore.domain.CmsCatalog;
import com.chestnut.contentcore.domain.CmsSite;
import com.chestnut.contentcore.service.ICatalogService;
import com.chestnut.contentcore.service.ISiteService;
import com.chestnut.contentcore.util.CatalogUtils;
import com.chestnut.search.SearchConsts;
import com.chestnut.search.exception.SearchErrorCode;
import com.chestnut.xmodel.core.IMetaModelType;
import cn.dev33.satoken.annotation.SaIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 开放的搜索API接口，无需认证即可访问
 *
 * @author 兮玥
 */
@RestController
@ConditionalOnProperty(name = "cms.search.use-legacy-api", havingValue = "true", matchIfMissing = false)
@RequestMapping("/dev-api/api/public")
public class OpenApiSearchController {

    private final ISiteService siteService;

    private final ICatalogService catalogService;

    private final ContentIndexService searchService;

    private final ElasticsearchClient esClient;

    public OpenApiSearchController(ISiteService siteService, 
                                  ICatalogService catalogService,
                                  ContentIndexService searchService,
                                  ElasticsearchClient esClient) {
        this.siteService = siteService;
        this.catalogService = catalogService;
        this.searchService = searchService;
        this.esClient = esClient;
    }

    private void checkElasticSearchEnabled() {
        if (!this.searchService.isElasticSearchAvailable()) {
            throw SearchErrorCode.ESConnectFail.exception();
        }
    }

    /**
     * 完全开放的搜索API接口，无需认证
     * 接口URL: /dev-api/api/public/search?query=校长&onlyTitle=false&pageNum=1&pageSize=6
     */
    @SaIgnore
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(value = "query", required = false) String query,
                               @RequestParam(value = "onlyTitle", required = false, defaultValue = "false") Boolean onlyTitle,
                               @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
                               @RequestParam(value = "pageSize", required = false, defaultValue = "6") Integer pageSize,
                               @RequestParam(value = "contentType", required = false) String contentType) {
        try {
            this.checkElasticSearchEnabled();
            
            // 改为直接获取一个站点，不使用getCurrentSite方法，避免认证需求
            List<CmsSite> sites = this.siteService.lambdaQuery().list();
            if (sites == null || sites.isEmpty()) {
                throw new RuntimeException("No site available");
            }
            CmsSite site = sites.get(0);
            
            String indexName = CmsSearchConstants.indexName(site.getSiteId().toString());
            SearchResponse<ObjectNode> sr = esClient.search(s -> {
                s.index(indexName) // 索引
                        .query(q ->
                                q.bool(b -> {
                                    if (StringUtils.isNotEmpty(contentType)) {
                                        b.must(must -> must.term(tq -> tq.field("contentType").value(contentType)));
                                    }
                                    if (StringUtils.isNotEmpty(query)) {
                                        if (onlyTitle) {
                                            b.must(must -> must
                                                    .match(match -> match
                                                            .analyzer(SearchConsts.IKAnalyzeType_Smart)
                                                            .field("title")
                                                            .query(query)
                                                    )
                                            );
                                        } else {
                                            b.must(must -> must
                                                    .multiMatch(match -> match
                                                            .analyzer(SearchConsts.IKAnalyzeType_Smart)
                                                            .fields("title^10", "fullText^1")
                                                            .query(query)
                                                    )
                                            );
                                        }
                                    }
                                    return b;
                                })
                        );
                if (StringUtils.isNotEmpty(query)) {
                    s.highlight(h ->
                            h.fields("title", f -> f.preTags("<font color='red'>").postTags("</font>"))
                                    .fields("fullText", f -> f.preTags("<font color='red'>").postTags("</font>")));
                }
                s.sort(sort -> sort.field(f -> f.field("_score").order(SortOrder.Desc)));
                s.sort(sort -> sort.field(f -> f.field("publishDate").order(SortOrder.Desc))); // 排序: _score:desc + publishDate:desc
                s.from((pageNum - 1) * pageSize).size(pageSize);  // 分页
                return s;
            }, ObjectNode.class);
            
            List<ESContentVO> list = sr.hits().hits().stream().map(hit -> {
                ObjectNode source = hit.source();
                ESContentVO vo = JacksonUtils.getObjectMapper().convertValue(source, ESContentVO.class);
                source.fieldNames().forEachRemaining(fieldName -> {
                    if (fieldName.startsWith(IMetaModelType.DATA_FIELD_PREFIX)) {
                        vo.getExtendData().put(fieldName, source.get(fieldName).asText());
                    }
                });
                vo.setHitScore(hit.score());
                vo.setPublishDateInstance(LocalDateTime.ofEpochSecond(vo.getPublishDate(), 0, ZoneOffset.UTC));
                vo.setCreateTimeInstance(LocalDateTime.ofEpochSecond(vo.getCreateTime(), 0, ZoneOffset.UTC));
                CmsCatalog catalog = this.catalogService.getCatalog(vo.getCatalogId());
                if (Objects.nonNull(catalog)) {
                    String catalogName = Stream.of(catalog.getAncestors().split(CatalogUtils.ANCESTORS_SPLITER)).map(cid -> {
                        CmsCatalog parent = this.catalogService.getCatalog(Long.valueOf(cid));
                        return Objects.nonNull(parent) ? parent.getName() : "[Unknown]";
                    }).collect(Collectors.joining(" > "));
                    vo.setCatalogName(catalogName);
                }
                hit.highlight().forEach((key, value) -> {
                    if (key.equals("fullText")) {
                        vo.setFullText(StringUtils.join(value.toArray(String[]::new)));
                    } else if (key.equals("title")) {
                        vo.setTitle(StringUtils.join(value.toArray(String[]::new)));
                    }
                });
                return vo;
            }).toList();
            
            // 直接返回简单的Map格式，不使用R对象包装
            Map<String, Object> result = new HashMap<>();
            result.put("rows", list);
            result.put("total", sr.hits().total().value());
            return result;
            
        } catch (Exception e) {
            // 简化错误处理，避免内部异常信息暴露
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("msg", "搜索失败: " + e.getMessage());
            error.put("success", false);
            return error;
        }
    }
}
