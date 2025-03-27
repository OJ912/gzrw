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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chestnut.cms.search.CmsSearchConstants;
import com.chestnut.common.utils.JacksonUtils;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.contentcore.domain.CmsCatalog;
import com.chestnut.contentcore.domain.CmsSite;
import com.chestnut.contentcore.mapper.CmsSiteMapper;
import com.chestnut.contentcore.service.ICatalogService;
import com.chestnut.contentcore.util.CatalogUtils;
import com.chestnut.contentcore.util.InternalUrlUtils;
import com.chestnut.search.SearchConsts;
import com.chestnut.search.exception.SearchErrorCode;
import com.chestnut.xmodel.core.IMetaModelType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
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
 * 完全开放的搜索API接口，无需认证即可访问
 * 直接使用Mapper访问数据库，绕过需要认证的服务方法
 * 
 * 这个控制器是对OpenApiSearchController的替代实现，采用了完全不同的方法：
 * 1. 直接使用CmsSiteMapper查询数据库获取站点信息，不通过ISiteService.getCurrentSite()方法
 * 2. 不继承BaseRestController，避免可能的认证依赖
 * 3. 简化了结果处理，直接构建Map对象返回
 * 
 * 原有的OpenApiSearchController已被禁用（通过@ConditionalOnProperty注解）
 *
 * @author 兮玥
 */
@RequiredArgsConstructor
@RestController
@RequestMapping({"/dev-api/api/public/v2", "/prod-api/api/public/v2"})
public class DirectPublicSearchController {

    private final CmsSiteMapper siteMapper;

    private final ICatalogService catalogService;

    private final ElasticsearchClient esClient;

    private void checkElasticSearchEnabled() {
        // 简化检查，只检查ES客户端是否为null
        if (esClient == null) {
            throw SearchErrorCode.ESConnectFail.exception();
        }
    }

    /**
     * 完全开放的搜索API接口，无需认证
     * 接口URL: /dev-api/api/public/v2/search?query=校长&onlyTitle=false&pageNum=1&pageSize=6
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(value = "query", required = false) String query,
                               @RequestParam(value = "onlyTitle", required = false, defaultValue = "false") Boolean onlyTitle,
                               @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
                               @RequestParam(value = "pageSize", required = false, defaultValue = "6") Integer pageSize,
                               @RequestParam(value = "contentType", required = false) String contentType,
                               @RequestParam(value = "preview", required = false, defaultValue = "false") Boolean preview) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            this.checkElasticSearchEnabled();
            
            // 直接使用Mapper查询数据库获取站点信息，不通过服务层
            LambdaQueryWrapper<CmsSite> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.last("LIMIT 1"); // 只获取第一个站点
            CmsSite site = siteMapper.selectOne(queryWrapper);
            
            if (site == null) {
                throw new RuntimeException("No site available");
            }
            
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
            
            // 处理搜索结果
            List<Map<String, Object>> list = sr.hits().hits().stream().map(hit -> {
                ObjectNode source = hit.source();
                Map<String, Object> item = new HashMap<>();
                
                // 复制所有字段
                source.fieldNames().forEachRemaining(fieldName -> {
                    if (fieldName.startsWith(IMetaModelType.DATA_FIELD_PREFIX)) {
                        // 扩展字段单独处理
                        Map<String, String> extendData = new HashMap<>();
                        extendData.put(fieldName, source.get(fieldName).asText());
                        item.put("extendData", extendData);
                    } else {
                        // 普通字段直接复制
                        item.put(fieldName, source.get(fieldName).asText());
                    }
                });
                
                // 添加额外信息
                item.put("hitScore", hit.score());
                
                // 处理时间字段
                if (item.containsKey("publishDate")) {
                    long publishDate = Long.parseLong(item.get("publishDate").toString());
                    item.put("publishDateInstance", LocalDateTime.ofEpochSecond(publishDate, 0, ZoneOffset.UTC).toString());
                }
                
                if (item.containsKey("createTime")) {
                    long createTime = Long.parseLong(item.get("createTime").toString());
                    item.put("createTimeInstance", LocalDateTime.ofEpochSecond(createTime, 0, ZoneOffset.UTC).toString());
                }
                
                // 处理目录信息
                if (item.containsKey("catalogId")) {
                    Long catalogId = Long.parseLong(item.get("catalogId").toString());
                    CmsCatalog catalog = catalogService.getCatalog(catalogId);
                    if (Objects.nonNull(catalog)) {
                        String catalogName = Stream.of(catalog.getAncestors().split(CatalogUtils.ANCESTORS_SPLITER)).map(cid -> {
                            CmsCatalog parent = catalogService.getCatalog(Long.valueOf(cid));
                            return Objects.nonNull(parent) ? parent.getName() : "[Unknown]";
                        }).collect(Collectors.joining(" > "));
                        item.put("catalogName", catalogName);
                    }
                }
                
                // 转换内部URL为HTTP URL
                if (item.containsKey("link") && item.get("link") != null && item.get("link").toString().startsWith("iurl://")) {
                    try {
                        String externalLink;
                        if (preview) {
                            // 预览模式下，使用getActualUrl方法，传入pc作为publishPipeCode
                            externalLink = InternalUrlUtils.getActualUrl(item.get("link").toString(), "pc", true);
                        } else {
                            // 非预览模式下，使用getActualUrl方法
                            externalLink = InternalUrlUtils.getActualUrl(item.get("link").toString(), "", false);
                        }
                        
                        // 确保链接以"/"开头
                        if (!externalLink.startsWith("/") && !externalLink.startsWith("http://") && !externalLink.startsWith("https://") && !externalLink.equals("javascript:void(0);")) {
                            externalLink = "/" + externalLink;
                        }
                        
                        item.put("link", externalLink);
                    } catch (com.chestnut.contentcore.exception.InternalUrlParseException e) {
                        // 处理内部链接解析失败的情况（如链接指向的内容不存在）
                        item.put("link", "javascript:void(0);");
                    } catch (Exception e) {
                        // 如果出现其他异常，使用默认空链接
                        item.put("link", "javascript:void(0);");
                    }
                } else if (item.containsKey("link") && item.get("link") != null) {
                    // 处理非内部链接，确保它们也以"/"开头
                    String link = item.get("link").toString();
                    if (!link.startsWith("/") && !link.startsWith("http://") && !link.startsWith("https://") && !link.equals("javascript:void(0);")) {
                        item.put("link", "/" + link);
                    }
                }
                
                // 处理高亮字段
                hit.highlight().forEach((key, value) -> {
                    if (key.equals("fullText") || key.equals("title")) {
                        item.put(key, StringUtils.join(value.toArray(String[]::new)));
                    }
                });
                
                return item;
            }).collect(Collectors.toList());
            
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("rows", list);
            result.put("total", sr.hits().total().value());
            
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "搜索失败: " + e.getMessage());
        }
        
        return result;
    }
}
