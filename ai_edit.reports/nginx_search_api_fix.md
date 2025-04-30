# Nginx 配置修复 - 搜索API问题

## 问题描述

在客户端应用程序中访问 `/dev-api/api/public/v2/search` API 端点时无法获取响应，但在 Postman 中直接访问 `http://localhost:8090/dev-api/api/public/v2/search` 可以正常工作。

## 问题原因

检查 Nginx 配置文件 `C:\nginx\conf\conf.d\gzmdrw_site.conf` 后发现，`/dev-api/` 路径的代理配置导致了路径前缀被错误地处理。

原始配置：

```nginx
# Add /dev-api/ path proxy to backend
location /dev-api/ {
    proxy_pass http://localhost:8090/;  # 这里的末尾斜杠会导致路径前缀被去除
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

这个配置导致：
- 客户端请求: `http://localhost/dev-api/api/public/v2/search?query=校长...`
- 转发到后端: `http://localhost:8090/api/public/v2/search?query=校长...` (去除了 `/dev-api/` 前缀)

但是后端控制器在 `DirectPublicSearchController.java` 中配置为处理 `/dev-api/api/public/v2/search` 路径的请求。由于路径不匹配，所以请求失败。

## 解决方案

修改 Nginx 配置，保留完整路径前缀：

```nginx
location /dev-api/ {
    proxy_pass http://localhost:8090;  # 删除末尾斜杠
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

通过删除 `proxy_pass` 值中的末尾斜杠，Nginx 将保留完整的请求路径，包括 `/dev-api/` 前缀，确保请求正确转发到后端控制器。

## 修改后效果

- 客户端请求: `http://localhost/dev-api/api/public/v2/search?query=校长...`
- 转发到后端: `http://localhost:8090/dev-api/api/public/v2/search?query=校长...` (保留了完整路径)

这样就可以确保请求能够被正确路由到 `DirectPublicSearchController` 类中的 `search` 方法。

## Nginx 路径代理规则解释

在 Nginx 配置中，`proxy_pass` 指令的 URL 是否包含末尾斜杠，会对路径的处理方式产生显著影响：

1. **有末尾斜杠**: `proxy_pass http://localhost:8090/;`
   - 效果：移除匹配的 location 前缀，只转发剩余部分
   - 示例：`/dev-api/some/path` → `http://localhost:8090/some/path`

2. **无末尾斜杠**: `proxy_pass http://localhost:8090;`
   - 效果：保留完整原始请求路径
   - 示例：`/dev-api/some/path` → `http://localhost:8090/dev-api/some/path`

## 修改时间

2025-03-23
