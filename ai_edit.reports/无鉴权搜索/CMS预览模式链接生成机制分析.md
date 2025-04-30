# CMS 预览模式链接生成机制分析

## 概述

CMS 系统的预览模式允许用户在内容发布前查看内容的最终呈现效果。本报告分析了系统如何生成预览链接，特别是对于内容（cms_content）类型的数据。

## 链接生成流程

### 1. 前端实现

前端通过以下步骤生成预览链接：

```javascript
// 在内容编辑器中生成预览链接
handlePreview() {
  let routeData = this.$router.resolve({
    path: "/cms/preview",
    query: { type: "content", dataId: this.form.contentId },
  });
  window.open(routeData.href, '_blank');
}
```

### 2. 预览页面处理

预览页面（preview.vue）负责构建实际的预览 URL：

```javascript
handlePublishPipeChange() {
  this.previewUrl = process.env.VUE_APP_BASE_API +
    "/cms/preview/" + this.type + "/" + this.dataId +
    "?pp=" + this.selectedPublishPipe +
    "&Authorization=Bearer " + getToken();
}
```

### 3. 后端处理

后端通过`CoreController`处理预览请求：

```java
@GetMapping("/cms/preview/{dataType}/{dataId}")
public void preview(
    @PathVariable("dataType") String dataType,
    @PathVariable("dataId") Long dataId,
    @RequestParam(value = "pp") String publishPipe,
    @RequestParam(value = "pi", required = false, defaultValue = "1") Integer pageIndex) {

    IInternalDataType internalDataType = ContentCoreUtils.getInternalDataType(dataType);
    IInternalDataType.RequestData data = new IInternalDataType.RequestData(
        dataId,
        pageIndex,
        publishPipe,
        true,
        ServletUtils.getParamMap(ServletUtils.getRequest())
    );
    String pageData = internalDataType.getPageData(data);
    response.getWriter().write(pageData);
}
```

### 4. 内容类型处理

对于内容（content）类型，`InternalDataType_Content`类处理预览请求：

```java
@Component(IInternalDataType.BEAN_NAME_PREFIX + InternalDataType_Content.ID)
public class InternalDataType_Content implements IInternalDataType {
    @Override
    public String getPageData(RequestData requestData) {
        CmsContent content = contentService.dao().getById(requestData.getDataId());
        return publishService.getContentPageData(
            content,
            requestData.getPageIndex(),
            requestData.getPublishPipeCode(),
            requestData.isPreview()
        );
    }
}
```

## 关键机制

1. **URL 格式**：

   - 前端预览 URL：`/cms/preview?type=content&dataId={contentId}`
   - 后端 API URL：`/cms/preview/{type}/{dataId}?pp={publishPipeCode}`

2. **参数传递**：

   - `type`: 内容类型（如"content"）
   - `dataId`: 内容 ID
   - `pp`: 发布通道编码
   - `pi`: 页码（可选）

3. **认证处理**：

   - 预览 URL 包含认证令牌
   - 通过`Authorization`头传递令牌

4. **发布通道**：
   - 支持多发布通道切换
   - 每个通道可有不同的模板

## 最佳实践

1. **链接生成**：

   - 使用路由系统生成预览链接
   - 保持 URL 格式一致性

2. **认证处理**：

   - 始终包含认证信息
   - 处理认证失效情况

3. **错误处理**：

   - 处理内容不存在的情况
   - 处理模板渲染错误

4. **性能优化**：
   - 缓存预览结果
   - 限制预览频率
