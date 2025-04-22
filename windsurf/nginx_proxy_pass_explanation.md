# Nginx proxy_pass u8defu5f84u5904u7406u56feu89e3

## u95eeu9898u7684u672cu8d28

Nginx u4e2du7684 `proxy_pass` u6307u4ee4u662fu5426u5305u542bu672bu5c3eu659cu6760uff08`/`uff09u4f1au5bf9u8defu5f84u5904u7406u4ea7u751fu91cdu5927u5f71u54cdu3002

## u56feu89e3u8bf7u6c42u6d41u7a0b

### u4feeu590du524duff1au4f7fu7528u5e26u6709u672bu5c3eu659cu6760u7684 proxy_pass

```
location /dev-api/ {
    proxy_pass http://localhost:8090/;
}
```

```
u5ba2u6237u7aefu8bf7u6c42                 Nginx u8f6cu53d1                      u540eu7aefu670du52a1u5668
+----------------+     +--------------------------+     +----------------+
|                |     |                          |     |                |
| /dev-api/api/  |---->| u79fbu9664 /dev-api/ u524du7f00     |---->| /api/public/   |
| public/v2/     |     | u8f6cu53d1 /api/public/v2/...  |     | v2/search     |
| search?query=u6821u957f |     |                          |     | u25cf u627eu4e0du5230u8fd9u4e2au8defu5f84! |
|                |     |                          |     |                |
+----------------+     +--------------------------+     +----------------+
```

**u7b80u5355u89e3u91ca**uff1a

u5f53 `proxy_pass` u4e2du5305u542bu672bu5c3eu659cu6760u65f6uff0cNginx u4f1auff1a
1. u79fbu9664u8bf7u6c42u8defu5f84u4e2du4e0e `location` u5339u914du7684u90e8u5206uff08u5373 `/dev-api/`uff09
2. u7528u5220u9664u540eu7684u8defu5f84u66ffu6362 `proxy_pass` u4e2du7684u8defu5f84u90e8u5206

u56e0u6b64uff1a
- u539fu59cbu8bf7u6c42uff1a`http://localhost/dev-api/api/public/v2/search?query=u6821u957f`
- u8f6cu53d1u540eu53d8u6210uff1a`http://localhost:8090/api/public/v2/search?query=u6821u957f` uff08u5931u53bb `/dev-api/`uff09
- u4f46u540eu7aefu63a7u5236u5668u60f3u8981u7684u662fuff1a`http://localhost:8090/dev-api/api/public/v2/search?query=u6821u957f`

### u4feeu590du540euff1au4f7fu7528u4e0du5e26u672bu5c3eu659cu6760u7684 proxy_pass

```
location /dev-api/ {
    proxy_pass http://localhost:8090;
}
```

```
u5ba2u6237u7aefu8bf7u6c42                 Nginx u8f6cu53d1                      u540eu7aefu670du52a1u5668
+----------------+     +--------------------------+     +----------------+
|                |     |                          |     |                |
| /dev-api/api/  |---->| u4fddu7559u5b8cu6574u539fu59cbu8defu5f84      |---->| /dev-api/api/  |
| public/v2/     |     | u8f6cu53d1 /dev-api/api/        |     | public/v2/     |
| search?query=u6821u957f |     | public/v2/search?query=u6821u957f |     | search         |
|                |     |                          |     | u25cf u6210u529fu5339u914du63a7u5236u5668!  |
+----------------+     +--------------------------+     +----------------+
```

**u7b80u5355u89e3u91ca**uff1a

u5f53 `proxy_pass` u4e2du4e0du5305u542bu672bu5c3eu659cu6760u65f6uff0cNginx u4f1auff1a
1. u4fddu7559u5b8cu6574u7684u539fu59cbu8bf7u6c42u8defu5f84uff08u5305u542b `/dev-api/`uff09
2. u76f4u63a5u5c06u5b8cu6574u8defu5f84u8ffdu52a0u5230 `proxy_pass` u6307u5b9au7684u670du52a1u5668u5730u5740u540e

u56e0u6b64uff1a
- u539fu59cbu8bf7u6c42uff1a`http://localhost/dev-api/api/public/v2/search?query=u6821u957f`
- u8f6cu53d1u540eu53d8u6210uff1a`http://localhost:8090/dev-api/api/public/v2/search?query=u6821u957f` uff08u4fddu7559u4e86 `/dev-api/`uff09
- u8fd9u6b63u597du5339u914du4e86u540eu7aef `DirectPublicSearchController` u7684u5730u5740u6620u5c04

## u6838u5fc3u77e5u8bc6u70b9

### Nginx proxy_pass u8defu5f84u5904u7406u89c4u5219uff1a

1. **u6709u672bu5c3eu659cu6760 u2014 u66ffu6362u6a21u5f0f**
   ```nginx
   location /some/path/ {
       proxy_pass http://example.com/;
   }
   ```
   - u8bf7u6c42 `/some/path/page.html` u4f1au8f6cu53d1u5230 `http://example.com/page.html`
   - u539fu56e0uff1a`/some/path/` u88abu5220u9664uff0cu53eau4fddu7559u4f59u4e0bu7684u90e8u5206

2. **u65e0u672bu5c3eu659cu6760 u2014 u4fddu7559u6a21u5f0f**
   ```nginx
   location /some/path/ {
       proxy_pass http://example.com;
   }
   ```
   - u8bf7u6c42 `/some/path/page.html` u4f1au8f6cu53d1u5230 `http://example.com/some/path/page.html`
   - u539fu56e0uff1au539fu59cbu8defu5f84u4fddu6301u4e0du53d8

### u4e3au4ec0u4e48 Postman u7aefu53efu4ee5u6b63u5e38u5de5u4f5cuff1f

Postman u76f4u63a5u8bbfu95eeu4e86u540eu7aefu670du52a1 `http://localhost:8090/dev-api/api/public/v2/search`uff0cu7ed5u8fc7u4e86 Nginx u4ee3u7406uff0cu56e0u6b64u4e0du53d7u8fd9u4e2au95eeu9898u7684u5f71u54cdu3002

## u603bu7ed3

- u5728 Nginx u4e2du7684 `proxy_pass` u914du7f6eu4e2duff0cu770bu4f3cu5faeu5c0fu7684u672bu5c3eu659cu6760u53efu80fdu4f1au5bfcu81f4u5f88u5927u7684u8defu5f84u5904u7406u5deeu5f02
- u5f53u4f60u9700u8981u4fddu7559u539fu59cbu8defu5f84u65f6uff0cu4e0du8981u5728 `proxy_pass` u4e2du6dfbu52a0u672bu5c3eu659cu6760
- u59cbu7ec8u8981u786eu4fdd Nginx u4ee3u7406u7684u8defu5f84u4e0eu4f60u7684u540eu7aefu63a7u5236u5668u6620u5c04u8defu5f84u4e00u81f4
