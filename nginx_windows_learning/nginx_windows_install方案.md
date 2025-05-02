# Windows 下 Nginx 安装与配置完整方案

## 一、准备阶段
1. 下载并校验 Nginx for Windows（建议使用官方稳定版 zip）。
2. 规划目录结构
   - `C:\nginx` → Nginx 根目录
   - `C:\gzmdrw_site_master\wwwroot_release` → 静态站点根目录
   - `C:\gzmdrw_site_master\rabid-chestnut\chestnut-ui` → Vue 前端（端口 8080）
   - `C:\gzmdrw_site_master\rabid-chestnut` → Spring Boot 后端（端口 8090）
3. 确认系统已安装 VC++ 运行时（Nginx 需依赖）。

## 二、安装 Nginx
1. 解压 zip 至 `C:\`，得到 `C:\nginx\nginx.exe`。
2. 初步启动验证：`./nginx.exe` → 浏览器访问 `http://localhost:80` 出现默认页。

## 三、核心配置文件规划
- `C:\nginx\conf\nginx.conf` → 主配置文件
- `C:\nginx\conf\sites\gzmdrw.conf` → 站点级虚拟主机（习惯性拆分，方便维护）
- `C:\nginx\logs` → 日志目录

## 四、nginx.conf 主体思路
1. `worker_processes auto;` 自动匹配 CPU 核心
2. `include conf/sites/*.conf;` 分离虚拟主机
3. `http { … }` 内设置：
   - gzip 压缩、缓存、mime、日志格式
   - `ssi on; ssi_silent_errors on;` 开启 SSI 并抑制 404

## 五、虚拟主机 (gzmdrw.conf) 设计
```nginx
server {
    listen       80;
    server_name  gzmdrw.local;  # 内网或公网域名

    root  C:/gzmdrw_site_master/wwwroot_release;
    index index.shtml index.html;

    # SSI
    ssi            on;
    ssi_types      text/shtml;
    default_type   text/html;

    # Vue 管理后台 → 8080
    location /admin/ {
        proxy_pass         http://127.0.0.1:8080/;
        proxy_set_header   Host $host;
    }

    # Spring Boot API → 8090
    location ~ ^/(dev-api|prod-api)/ {
        proxy_pass         http://127.0.0.1:8090;
        proxy_set_header   Host $host;
    }

    # 其余静态资源
    location / {
        try_files $uri $uri/ =404;
    }

    # 访问日志 & 错误日志
    access_log  logs/gzmdrw_access.log  main;
    error_log   logs/gzmdrw_error.log   warn;
}
```

## 六、Windows 服务化（开机自启）
1. 使用 NSSM / WinSW / sc.exe 创建 Windows 服务指向 `nginx.exe`。
2. 设置恢复策略：失败后自动重启。

## 七、常用运维命令
```powershell
nginx -t          # 语法校验
nginx -s reload   # 平滑重载
nginx -s stop     # 快速停止
```

## 八、验证与调试
1. `curl -I http://localhost/admin/` → 状态码 200，响应头来自 Vue。
2. `curl -I http://localhost/dev-api/health` → 200 来自 Spring Boot。
3. 访问 *.shtml 检查 SSI 片段是否正确渲染。
4. 观察 `logs/*.log`，确认无 404/502。
