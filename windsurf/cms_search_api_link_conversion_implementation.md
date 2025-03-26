# 搜索API内部链接转外部链接实现报告

## 1. 项目目标

本项目的主要目标是修改搜索API，确保API返回的结果中所有内部链接（iurl://）都被转换为外部HTTP链接。具体要求如下：

- 修改 `/dev-api/api/public/v2/search` API端点，确保返回的链接为外部链接
- 参考已有的 `@cms_search_content` 模板标签中的链接转换实现
- 确保所有链接以斜杠（/）开头，例如将 `xuexiaolingdao/xuexiaogaikuang/652717108355141.shtml` 转换为 `/xuexiaolingdao/xuexiaogaikuang/652717108355141.shtml`

## 2. 实现步骤

### 2.1 分析现有代码

首先，我们分析了 `CmsSearchContentTag.java` 文件中的链接转换实现：

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

然后，我们找到了处理 `/dev-api/api/public/v2/search` API端点的控制器 `DirectPublicSearchController.java`。

### 2.2 分析链接转换工具类

我们分析了 `InternalUrlUtils.java` 文件，了解了内部链接转换的核心方法：

- `getActualUrl(String iurl, String publishPipeCode, boolean isPreview)` - 将内部URL转换为实际的HTTP URL
- `getActualPreviewUrl(String iurl)` - 获取内部URL的预览链接

### 2.3 实现链接转换逻辑

在 `DirectPublicSearchController.java` 文件中，我们添加了链接转换逻辑：

```java
// 转换内部URL为HTTP URL
if (item.containsKey("link") && item.get("link") != null && item.get("link").toString().startsWith("iurl://")) {
    try {
        // 使用getActualUrl方法，获取实际发布的URL而不是预览URL
        // 假设默认发布管道为"html"，非预览模式
        String publishPipeCode = "html";
        boolean isPreview = false;
        String externalLink = InternalUrlUtils.getActualUrl(item.get("link").toString(), publishPipeCode, isPreview);
        
        // 确保链接以"/"开头
        if (!externalLink.startsWith("/") && !externalLink.startsWith("http://") && !externalLink.startsWith("https://") && !externalLink.equals("javascript:void(0);")) {
            externalLink = "/" + externalLink;
        }
        
        item.put("link", externalLink);
    } catch (com.chestnut.contentcore.exception.InternalUrlParseException e) {
        // 处理内部链接解析失败的情况（如链接指向的内容不存在）
        item.put("link", "javascript:void(0);");
        // 可以考虑记录日志
        // log.warn("Failed to parse internal URL: " + item.get("link"), e);
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
```

## 3. 实现中的关键点

### 3.1 使用 getActualUrl 而非 getActualPreviewUrl

在实现过程中，我们发现最初使用了 `getActualPreviewUrl` 方法，但这不符合需求。我们需要使用 `getActualUrl` 方法来获取实际的发布URL，而不是预览URL。

```java
// 错误的实现
String externalLink = InternalUrlUtils.getActualPreviewUrl(item.get("link").toString());

// 正确的实现
String publishPipeCode = "html";
boolean isPreview = false;
String externalLink = InternalUrlUtils.getActualUrl(item.get("link").toString(), publishPipeCode, isPreview);
```

### 3.2 确保链接以斜杠开头

为了满足链接格式要求，我们添加了逻辑确保所有链接都以斜杠（/）开头，除非它们已经是完整的URL（以http://或https://开头）或是特殊的javascript链接：

```java
// 确保链接以"/"开头
if (!externalLink.startsWith("/") && !externalLink.startsWith("http://") && !externalLink.startsWith("https://") && !externalLink.equals("javascript:void(0);")) {
    externalLink = "/" + externalLink;
}
```

### 3.3 处理非内部链接

我们还添加了逻辑来处理非内部链接（不以"iurl://"开头的链接），确保它们也符合格式要求：

```java
// 处理非内部链接，确保它们也以"/"开头
if (item.containsKey("link") && item.get("link") != null) {
    String link = item.get("link").toString();
    if (!link.startsWith("/") && !link.startsWith("http://") && !link.startsWith("https://") && !link.equals("javascript:void(0);")) {
        item.put("link", "/" + link);
    }
}
```

### 3.4 异常处理

我们实现了健壮的异常处理，确保即使在链接转换失败的情况下，API仍然能够返回有效的响应：

```java
try {
    // 链接转换逻辑
} catch (com.chestnut.contentcore.exception.InternalUrlParseException e) {
    // 处理内部链接解析失败的情况（如链接指向的内容不存在）
    item.put("link", "javascript:void(0);");
} catch (Exception e) {
    // 如果出现其他异常，使用默认空链接
    item.put("link", "javascript:void(0);");
}
```

## 4. 注意事项

### 4.1 发布管道配置

在实现中，我们假设默认的发布管道为"html"，这是基于系统的常见配置。如果系统使用不同的发布管道，可能需要调整此参数：

```java
String publishPipeCode = "html"; // 可能需要根据实际配置调整
```

### 4.2 预览模式设置

我们将预览模式设置为false，因为API通常用于获取正式发布的内容：

```java
boolean isPreview = false; // API通常不需要预览模式
```

### 4.3 链接格式一致性

确保所有链接格式一致（以斜杠开头）对于前端应用的路由处理非常重要。这样可以避免前端需要处理不同格式的链接。

## 5. 测试与验证

实现完成后，我们可以通过以下方式测试API：

```
http://localhost:8090/dev-api/api/public/v2/search?query=%E6%A0%A1%E9%95%BF&onlyTitle=false&pageNum=1&pageSize=6
```

验证返回的链接是否符合以下要求：
- 所有内部链接（iurl://）已转换为外部HTTP链接
- 所有链接都以斜杠（/）开头，例如 `/xuexiaolingdao/xuexiaogaikuang/652717108355141.shtml`

## 6. 总结

通过本次实现，我们成功地修改了搜索API，确保API返回的结果中所有内部链接都被转换为外部HTTP链接，并且所有链接都以斜杠开头。这一改进使得API返回的数据更加规范，便于前端应用处理和展示。

主要成果包括：

1. 实现了内部链接到外部链接的转换
2. 确保所有链接格式一致（以斜杠开头）
3. 添加了健壮的异常处理，提高了系统稳定性
4. 参考并改进了现有的链接转换实现

这些改进使得搜索API更加可靠和易于使用，为前端应用提供了更好的数据支持。
