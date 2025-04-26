package com.chestnut.cms.search.servlet;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chestnut.cms.search.CmsSearchConstants;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.contentcore.domain.CmsCatalog;
import com.chestnut.contentcore.domain.CmsSite;
import com.chestnut.contentcore.mapper.CmsSiteMapper;
import com.chestnut.contentcore.service.ICatalogService;
import com.chestnut.contentcore.util.CatalogUtils;
import com.chestnut.search.SearchConsts;
import com.chestnut.search.exception.SearchErrorCode;
import com.chestnut.xmodel.core.IMetaModelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import java.io.IOException;
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
 * 使用原生Servlet实现，完全绕过Spring Security和SaToken认证
 */
@WebServlet(urlPatterns = "/dev-api/api/public/search")
public class PublicSearchServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Autowired
    private CmsSiteMapper siteMapper;

    @Autowired
    private ICatalogService catalogService;

    @Autowired
    private ElasticsearchClient esClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init() throws ServletException {
        // 初始化Spring依赖注入
        SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this, getServletContext());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 设置响应类型
        response.setContentType("application/json;charset=UTF-8");
        
        // 设置跨域头
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        // 如果是OPTIONS请求，直接返回200
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        // 获取请求参数
        String query = request.getParameter("query");
        Boolean onlyTitle = Boolean.parseBoolean(request.getParameter("onlyTitle"));
        Integer pageNum = Integer.parseInt(request.getParameter("pageNum") != null ? request.getParameter("pageNum") : "1");
        Integer pageSize = Integer.parseInt(request.getParameter("pageSize") != null ? request.getParameter("pageSize") : "6");
        String contentType = request.getParameter("contentType");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 检查ES客户端
            if (esClient == null) {
                throw SearchErrorCode.ESConnectFail.exception();
            }
            
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
        
        // 将结果写入响应
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }
}
