# CMS预览模式链接生成机制分析

## 概述

在CMS后台管理系统中，预览模式允许用户在内容发布前查看内容的最终呈现效果。本报告分析了CMS系统如何生成预览链接，特别是对于内容（cms_content）类型的数据，如何生成类似 `/admin/cms/preview?type=content&dataId=652721915281477` 的预览链接。

## 前端实现

### 预览链接生成

在内容编辑器（contentEditor.vue）中，当用户点击预览按钮时，系统会调用`handlePreview`方法生成预览链接：

```javascript
handlePreview() {
  let routeData = this.$router.resolve({
    path: "/cms/preview",
    query: { type: "content", dataId: this.form.contentId },
  });
  window.open(routeData.href, '_blank');
}
```

这段代码创建了一个前端路由，格式为`/cms/preview?type=content&dataId={contentId}`，然后在新标签页中打开这个链接。

### 预览页面实现

当用户访问预览链接时，会加载`preview.vue`组件，该组件负责展示预览内容：

```javascript
export default {
  name: "ContentCorePreview",
  data() {
    return {
      type: this.$route.query.type,       // 从URL参数获取type
      dataId: this.$route.query.dataId,  // 从URL参数获取dataId
      publishPipes: [],
      selectedPublishPipe: undefined,
      previewUrl: undefined,
      // 其他配置...
    };
  },
  created() {
    this.loadPublishPipes();
  },
  methods: {
    loadPublishPipes() {
      getPublishPipeSelectData().then(response => {
        this.publishPipes = response.data.rows;
        this.selectedPublishPipe = response.data.rows[0].pipeCode;
        this.handlePublishPipeChange();
      });
    },
    handlePublishPipeChange() {
      this.previewUrl = process.env.VUE_APP_BASE_API + "/cms/preview/" + this.type + "/" + this.dataId 
        + "?pp=" + this.selectedPublishPipe + "&Authorization=Bearer " + getToken();
    }
    // 其他方法...
  }
};
```

`preview.vue`组件从URL查询参数中获取`type`和`dataId`，然后加载发布通道数据。当发布通道数据加载完成或发生变化时，会调用`handlePublishPipeChange`方法构建实际的预览URL：`/cms/preview/{type}/{dataId}?pp={publishPipeCode}&Authorization=Bearer {token}`。

## 后端实现

### 预览请求处理

后端通过`CoreController`类处理预览请求：

```java
@Priv(type = AdminUserType.TYPE)
@GetMapping("/cms/preview/{dataType}/{dataId}")
public void preview(@PathVariable("dataType") String dataType, @PathVariable("dataId") Long dataId,
                    @RequestParam(value = "pp") String publishPipe,
                    @RequestParam(value = "pi", required = false, defaultValue = "1") Integer pageIndex,
                    @RequestParam(value = "list", required = false, defaultValue = "N") String listFlag)
        throws IOException, TemplateException {
    HttpServletResponse response = ServletUtils.getResponse();
    response.setCharacterEncoding(Charset.defaultCharset().displayName());
    response.setContentType("text/html; charset=" + Charset.defaultCharset().displayName());
    IInternalDataType internalDataType = ContentCoreUtils.getInternalDataType(dataType);
    Assert.notNull(internalDataType, () -> ContentCoreErrorCode.UNSUPPORTED_INTERNAL_DATA_TYPE.exception(dataType));

    IInternalDataType.RequestData data = new IInternalDataType.RequestData(dataId, pageIndex, publishPipe,
            true, ServletUtils.getParamMap(ServletUtils.getRequest()));
    String pageData = internalDataType.getPageData(data);
    response.getWriter().write(pageData);
}
```

这个方法接收`dataType`、`dataId`、`publishPipe`和`pageIndex`等参数，然后根据数据类型获取对应的内部数据类型处理器，并调用其`getPageData`方法获取预览页面内容。

### 内部数据类型接口

`IInternalDataType`接口定义了内部数据类型的通用操作，包括生成预览链接的静态方法：

```java
/**
 * 获取内部数据预览地址
 * 预览路径规则：cms/preview/{内部数据类型}/{数据id}?pp={发布通道编码}&pi={页码}
 */
static String getPreviewPath(String type, Long id, String publishPipeCode, int pageIndex) {
    String path = "cms/preview/" + type + "/" + id + "?pp=" + publishPipeCode;
    if (pageIndex > 1) {
        path += "&pi=" + pageIndex;
    }
    return TemplateUtils.appendTokenParameter(path);
}
```

### 内容类型实现

对于内容（content）类型，`InternalDataType_Content`类实现了`IInternalDataType`接口：

```java
@RequiredArgsConstructor
@Component(IInternalDataType.BEAN_NAME_PREFIX + InternalDataType_Content.ID)
public class InternalDataType_Content implements IInternalDataType {

    public final static String ID = "content";

    private final IContentService contentService;

    private final IPublishService publishService;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getPageData(RequestData requestData) throws IOException, TemplateException {
        CmsContent content = contentService.dao().getById(requestData.getDataId());
        Assert.notNull(content, () -> CommonErrorCode.DATA_NOT_FOUND_BY_ID.exception("contentId", requestData.getDataId()));

        return this.publishService.getContentPageData(content, requestData.getPageIndex(), requestData.getPublishPipeCode(), requestData.isPreview());
    }

    // 其他方法...
}
```

这个类处理内容类型的预览请求，通过`getPageData`方法获取内容的预览页面数据。

## 预览链接生成流程

1. **前端触发**：用户在内容编辑页面点击预览按钮，调用`handlePreview`方法。

2. **前端路由生成**：生成格式为`/cms/preview?type=content&dataId={contentId}`的前端路由链接。

3. **预览页面加载**：用户访问预览链接，加载`preview.vue`组件。

4. **后端API调用**：`preview.vue`组件构建实际的后端API调用URL：`/cms/preview/{type}/{dataId}?pp={publishPipeCode}&Authorization=Bearer {token}`。

5. **后端处理**：`CoreController.preview`方法处理请求，根据数据类型调用对应的内部数据类型处理器。

6. **内容渲染**：对于内容类型，`InternalDataType_Content.getPageData`方法获取内容数据并渲染预览页面。

## 总结

在CMS后台管理系统中，预览模式链接的生成是一个前后端协作的过程。前端通过路由系统生成预览链接，后端通过内部数据类型处理器处理预览请求并返回渲染后的页面内容。这种设计使得系统能够灵活地处理不同类型的内容预览，同时保持统一的预览接口。
