/*
 * Copyright 2022-2024 u516eu73a5(190785909@qq.com)
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
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.chestnut.cms.search.CmsSearchConstants;
import com.chestnut.cms.search.service.ContentIndexService;
import com.chestnut.cms.search.vo.ESContentVO;
import com.chestnut.common.domain.R;
import com.chestnut.common.security.web.BaseRestController;
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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * u5f00u653eu7684u641cu7d22APIu63a5u53e3uff0cu65e0u9700u8ba4u8bc1u5373u53efu8bbfu95ee
 *
 * @author u516eu73a5
 */
@RequiredArgsConstructor
@RestController
public class PublicContentSearchController extends BaseRestController {

    private final ISiteService siteService;

    private final ICatalogService catalogService;

    private final ContentIndexService searchService;

    private final ElasticsearchClient esClient;

    private void checkElasticSearchEnabled() {
        if (!this.searchService.isElasticSearchAvailable()) {
            throw SearchErrorCode.ESConnectFail.exception();
        }
    }

    /**
     * u5f00u653eu641cu7d22APIu63a5u53e3uff0cu65e0u9700u8ba4u8bc1
     */
    @SaIgnore
    @GetMapping("/cms/search/public/contents")
    public R<?> publicSearchContents(@RequestParam(value = "query", required = false) String query,
                               @RequestParam(value = "onlyTitle", required = false, defaultValue = "false") Boolean onlyTitle,
                               @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
                               @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
                               @RequestParam(value = "contentType", required = false) String contentType) throws ElasticsearchException, IOException {
        this.checkElasticSearchEnabled();
        CmsSite site = this.siteService.getCurrentSite(ServletUtils.getRequest());
        String indexName = CmsSearchConstants.indexName(site.getSiteId().toString());
        SearchResponse<ObjectNode> sr = esClient.search(s -> {
            s.index(indexName) // u7d22u5f15
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
            s.sort(sort -> sort.field(f -> f.field("publishDate").order(SortOrder.Desc))); // u6392u5e8f: _score:desc + publishDate:desc
//            s.source(source -> source.filter(f -> f.excludes("fullText"))); // u8fc7u6ee4u5b57u6bb5
            s.from((pageNum - 1) * pageSize).size(pageSize);  // u5206u9875
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
        return this.bindDataTable(list, sr.hits().total().value());
    }
}
