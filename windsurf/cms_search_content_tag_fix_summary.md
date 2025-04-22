# cms_search_content 模板标签修复总结

## 问题背景
- 需求：修改 `@cms_search_content` 模板标签，确保将内部链接 (iurl://) 转换为外部 HTTP 链接
- 转换示例：
  - 内部链接：`iurl://content?id=310000000000122`
  - 外部链接：`https://www.gzmdrw.cn/xiaoyuanfengguang/xuexiaogaikuang/310000000000122.shtml`

## 遇到的问题
1. **编译错误**：找不到 `CmsSearchConstants` 类
   - 解决方案：修正导入语句，从 `com.chestnut.cms.search.constants.CmsSearchConstants` 改为 `com.chestnut.cms.search.CmsSearchConstants`

2. **运行时错误**：在预览模式下，搜索页面出现 500 错误
   - 错误信息：`com.chestnut.contentcore.exception.InternalUrlParseException: Parse iurl failed: 数据不存在`
   - 原因：搜索结果中包含指向已不存在内容的内部链接

## 最终解决方案
为 `CmsSearchContentTag` 类添加了更健壮的异常处理：

```java
// 转换内部URL为HTTP URL
if (vo.getLink() != null && vo.getLink().startsWith("iurl://")) {
    try {
        // 获取模板上下文以确定是否处于预览模式
        TemplateContext context = FreeMarkerUtils.getTemplateContext(env);
        String publishPipeCode = context.getPublishPipeCode();
        boolean isPreview = context.isPreview();
        vo.setLink(InternalUrlUtils.getActualUrl(vo.getLink(), publishPipeCode, isPreview));
    } catch (com.chestnut.contentcore.exception.InternalUrlParseException e) {
        // 处理内部链接解析失败的情况（如链接指向的内容不存在）
        vo.setLink("javascript:void(0);");
        // 可以考虑记录日志：logger.warn("Failed to parse internal URL: " + vo.getLink(), e);
    } catch (Exception e) {
        // 如果无法获取模板上下文，则回退到预览URL
        try {
            vo.setLink(InternalUrlUtils.getActualPreviewUrl(vo.getLink()));
        } catch (com.chestnut.contentcore.exception.InternalUrlParseException ex) {
            // 如果预览URL也无法解析，使用默认空链接
            vo.setLink("javascript:void(0);");
        }
    }
}
```

## 解决方案要点
1. **特定异常处理**：为 `InternalUrlParseException` 添加专门的异常处理逻辑
2. **优雅降级**：当链接指向不存在的内容时，使用 `javascript:void(0);` 作为后备链接而非崩溃
3. **双重保障**：对于预览URL也添加了异常处理，确保即使在预览模式下也能正常工作
4. **渐进实现**：保留了原有的URL转换逻辑，仅添加了更健壮的错误处理

此实现确保了即使在某些内容已不存在的情况下，搜索页面也能正常加载和显示，提高了系统的稳定性。

## 文件位置
- 修改文件：`c:\gzmdrw_site_master\rabid-chestnut\chestnut-cms\chestnut-cms-search\src\main\java\com\chestnut\cms\search\template\tag\CmsSearchContentTag.java`
- 问题模板：`c:\gzmdrw_site_master\wwwroot_release\gzmdrw_pc\template\search\gzmdrw_search.template.html`

## 修改日期
- 2025年3月20日
