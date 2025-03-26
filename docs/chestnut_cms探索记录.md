当栏目发布后会在 wwwroot_release/gzmdrw_pc 下面生成一个栏目同名的文件夹存放 shtml 代码

数据按 recent 排序是实际是按照发布的日期从最新到最旧排序，但是在显示页面是默认按照文章创建日期显示。

## FreeMarker 模板与 JavaScript 交互的注意事项

1. **FreeMarker 变量访问限制**
   - 在此项目中，FreeMarker 模板无法直接访问 URL 参数（`RequestParameters`和`Request`对象不可用）
   - 建议：开发前先测试模板环境中哪些对象和方法可用，不要假设所有标准对象都可访问
2. **避免 FreeMarker 与 JavaScript 语法冲突**
   - FreeMarker 使用`${变量}`语法，与 JavaScript 的模板字符串语法冲突
   - 建议：在 JavaScript 代码中避免使用反引号模板字符串，改用字符串连接（`'字符串1' + 变量 + '字符串2'`）
3. **不要混合 FreeMarker 和 JavaScript 更新内容**
   - 错误示例：`<p>当前搜索条件: site_id=${Site.siteId} and title like '<span id="sql-condition"></span>'</p>`
   - 正确示例：`<p>当前搜索条件: <span id="full-sql-condition"></span></p>`，然后用 JavaScript 设置完整内容

## 客户端搜索实现的最佳实践

1. **使用隐藏字段存储状态**
   - 添加隐藏输入字段存储关键词：`<input type="hidden" id="search-keyword-hidden" value="">`
   - 页面加载时立即从 URL 获取参数并存储
2. **页面加载时初始化**
   - 使用立即执行函数在页面解析时就开始处理，不等待 DOMContentLoaded
   - 这样可以尽早设置关键数据，减少页面闪烁
3. **分离数据获取和 UI 更新**
   - 先获取和存储数据，再更新 UI 元素
   - 使用事件监听确保 DOM 元素存在后再操作它们
4. **客户端过滤替代服务器过滤**
   - 当服务器端过滤不可行时，可以加载所有内容然后在客户端过滤
   - 适用于数据量不大的情况，提供更好的用户体验

## 调试技巧

1. **添加详细的调试信息**
   - 在页面中显示关键变量值和状态信息
   - 使用 console.log 记录 JavaScript 执行过程
2. **分步骤测试**
   - 先确保基本功能工作，再添加复杂特性
   - 每解决一个问题后进行测试，避免多个问题叠加
3. **查询数据库**
   - 点击 navicat 中的数据库-表-在数据库中查找

## 关于参数设置（sys_config）重要信息

1. SiteApiUrl 例如 http://localhost:8090/ 实际是后台后端的 baseUrl
2. CMSBackendContext 例如 http://localhost/dev-api/ 实际是客户端访问后台时候得到的 html 页面中的 js 对前端发出的请求的 baseUrl
