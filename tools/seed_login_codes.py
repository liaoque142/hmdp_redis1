#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
预置 1000 个登录验证码到 Redis，供 TokenGenerator 使用。

为什么需要这个脚本：
    UserServiceImpl.sendCode() 用的是 RandomUtil.randomNumbers(6) 随机码，
    项目里并不存在固定的 "123456"；而 login() 只是把 Redis 里的
    login:code:{phone} 和请求体里的 code 做字符串比较。
    所以只要把 "123456" 直接写进 Redis，login 就会通过，并且会自动把
    手机号对应的用户 INSERT 进 tb_user（见 creatUserWithPhone）。

用法（必须在启动应用、跑 TokenGenerator 之前执行一次）：
    python tools/seed_login_codes.py

注意：login() 成功后不会删除 login:code:*，所以这 1000 个 key 只需要
      预置一次；跑完压测后它们会自己过期，不需要手动清理。
"""
import redis

# 以下四项与 src/main/resources/application.yaml 的 spring.redis.* 保持一致
REDIS_HOST = "192.168.15.128"
REDIS_PORT = 6379
REDIS_PASSWORD = "123456"
REDIS_DB = 0            # yaml 里没有配 spring.redis.database，默认就是 db 0

PHONE_PREFIX = "138"    # 手机号段需满足 RegexPatterns.PHONE_REGEX
PHONE_COUNT = 1000
CODE = "123456"         # 必须和 TokenGenerator.java 里的 code 一致
CODE_TTL = 3600         # 秒。业务代码默认是 2 分钟，这里给足，避免 1000 次串行登录中途过期


def main():
    r = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        password=REDIS_PASSWORD,
        db=REDIS_DB,
        decode_responses=True,
    )
    print("已连接 Redis %s:%d db=%d, PING=%s" % (REDIS_HOST, REDIS_PORT, REDIS_DB, r.ping()))

    phones = [PHONE_PREFIX + format(i, "08d") for i in range(1, PHONE_COUNT + 1)]

    pipe = r.pipeline(transaction=False)
    for phone in phones:
        pipe.set("login:code:" + phone, CODE, ex=CODE_TTL)
    pipe.execute()

    # 回读校验，确保真的写进去了
    ok = 0
    check = r.pipeline(transaction=False)
    for phone in phones:
        check.get("login:code:" + phone)
    for value in check.execute():
        if value == CODE:
            ok += 1

    print("已预置验证码: login:code:{phone} = %s, TTL = %d 秒" % (CODE, CODE_TTL))
    print("手机号范围: %s ~ %s (共 %d 个)" % (phones[0], phones[-1], len(phones)))
    print("回读校验: %d/%d 正确" % (ok, len(phones)))
    if ok != len(phones):
        raise SystemExit("预置失败，请检查 Redis host / 密码 / db 是否与 application.yaml 一致")

    print("")
    print("下一步：启动应用（JDK 8），确认 8081 有监听后，再跑 TokenGenerator.generateTokens()")


if __name__ == "__main__":
    main()
