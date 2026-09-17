# hm-dianping（黑马点评）

基于 Spring Boot + MyBatis-Plus + Redis 的仿大众点评项目，用于练习 Redis 在真实业务场景下的应用（缓存、分布式锁、全局唯一 ID、秒杀）。

> 学习项目，未完成，持续更新中。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Spring Boot | 2.3.12.RELEASE |
| MyBatis-Plus | 3.4.3 |
| MySQL | 5.x（驱动 mysql-connector-java 5.1.47） |
| Redis | Spring Data Redis + Lettuce 连接池 |
| Hutool | 5.7.17 |
| JDK | 1.8 |

## 已实现

- **短信登录**：验证码存 Redis（`login:code:`，2 分钟），登录签发 UUID token，用户信息以 Hash 存 Redis（`login:token:`，30 分钟），`RefreshTokenInterceptor` 负责续期与登录态校验。验证码为模拟发送，仅打印到控制台。
- **商户缓存**：
  - 缓存穿透——查库为空时写入空值并设短 TTL；
  - 缓存击穿——互斥锁（`queryWithMutext`）与逻辑过期（`queryWithLogicalExpire`，配合线程池异步重建）两种方案，封装在 `CacheClient` 中；
  - 缓存与数据库一致性——更新时先写库再删缓存。
- **商铺类型缓存**：Redis List + 过期时间。
- **优惠券秒杀**：校验秒杀时间窗口与库存，SQL 原子扣减库存（`stock = stock - 1`），`RedisIdWorker` 生成订单 ID。
- **全局唯一 ID**：`RedisIdWorker`，时间戳左移 32 位 + Redis 每日自增序列，配合线程池压测验证。

## 待完成（TODO）

- [ ] 优惠券秒杀：一人一单校验、乐观锁/悲观锁、Redis + Lua 预扣库存、异步下单
- [ ] 达人探店：`BlogServiceImpl`、`BlogCommentsServiceImpl` 目前为空实现，点赞无去重记录
- [ ] 好友关注 / 共同关注（`FollowController` 暂无接口）
- [ ] 附近商户（Redis GEO）
- [ ] 用户签到（Redis BitMap）
- [ ] UV 统计（HyperLogLog）
- [ ] 退出登录（接口暂返回“功能未完成”）

## 快速开始

### 1. 环境准备

- JDK 1.8、Maven
- MySQL（导入 `src/main/resources/db/hmdp.sql`，库名 `hmdp`）
- Redis

### 2. 修改配置

编辑 `src/main/resources/application.yaml`，按本机环境调整数据源与 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: root
    password: 123456
  redis:
    host: 192.168.15.128
    port: 6379
    password: 123456
```

### 3. 启动

```bash
mvn spring-boot:run
```

服务端口 **8081**。项目本身不含前端资源（无 `static` 目录），前端页面由 nginx 单独托管，图片上传目录见 `SystemConstants.IMAGE_UPLOAD_DIR`。

## 测试账号

登录使用手机号 + 验证码，验证码为模拟发送，**不接真实短信**。

测试手机号：**`13688888888`**（任意符合格式的手机号首次登录都会自动创建用户）。

获取验证码：调用 `POST /user/code?phone=13688888888`，验证码以 debug 日志输出（`logging.level.com.hmdp: debug`）打印在控制台，例如：

```
发送验证码成功，验证码：123456
```

拿到验证码后调用 `POST /user/login`，携带 `{"phone":"13688888888","code":"123456"}`，返回 token，后续请求需在请求头 `authorization` 中携带该 token。

> 说明：`13688888888` 是约定俗成的测试号；`db/hmdp.sql` 中预置的用户是 `13686869696`（小鱼同学）等，用它登录可直接复用库中已有的探店数据。

## 项目结构

```
src/main/java/com/hmdp/
├── config/          # MvcConfig、MybatisConfig、全局异常处理
├── controller/      # 接口层
├── dto/             # Result、UserDTO 等
├── entity/          # 实体类
├── mapper/          # MyBatis-Plus Mapper
├── service/impl/    # 业务实现
└── utils/           # RedisIdWorker、CacheClient、拦截器、常量等
src/main/resources/
├── application.yaml
├── db/hmdp.sql            # 建表 + 初始数据
└── mapper/VoucherMapper.xml
docs/                      # 学习笔记
```

## 学习笔记

- [`docs/缓存击穿问题与修复.txt`](docs/缓存击穿问题与修复.txt) — 互斥锁防缓存击穿时 `unlock(null)` 报 `non null key required` 的原因、复现与修复。
