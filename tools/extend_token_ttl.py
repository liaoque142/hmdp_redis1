#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把 tokens.csv 里所有 token 的 TTL 从默认 30 分钟延长到 2 小时。

为什么需要这个脚本：
    UserServiceImpl.login() 用 RedisConstants.LOGIN_USER_TTL = 30 (分钟)
    设置 login:token:{token} 的过期时间。生成 1000 个 token 之后如果还要
    慢慢配 JMeter，很容易就过期了。
    压测真正跑起来以后，RefreshTokenInterceptor 每次请求会把 TTL 续回
    30 分钟，所以这里只需要补上「压测开始前」那段空档。

用法（跑完 TokenGenerator 之后执行）：
    python tools/extend_token_ttl.py
"""
import os

import redis

REDIS_HOST = "192.168.15.128"
REDIS_PORT = 6379
REDIS_PASSWORD = "123456"
REDIS_DB = 0

TOKENS_FILE = "tokens.csv"
TOKEN_TTL = 2 * 60 * 60      # 2 小时


def main():
    if not os.path.exists(TOKENS_FILE):
        raise SystemExit("找不到 %s，请先在 IDE 里跑 TokenGenerator.generateTokens()" % TOKENS_FILE)

    with open(TOKENS_FILE, encoding="utf-8") as f:
        tokens = [line.strip() for line in f if line.strip()]

    if not tokens:
        raise SystemExit("%s 是空的，请先跑 TokenGenerator.generateTokens()" % TOKENS_FILE)
    if len(set(tokens)) != len(tokens):
        print("警告: tokens.csv 里有重复 token (%d 行, %d 个唯一值)" % (len(tokens), len(set(tokens))))

    r = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        password=REDIS_PASSWORD,
        db=REDIS_DB,
        decode_responses=True,
    )

    pipe = r.pipeline(transaction=False)
    for token in tokens:
        pipe.expire("login:token:" + token, TOKEN_TTL)
    results = pipe.execute()

    renewed = sum(1 for x in results if x)
    print("tokens.csv 读到 %d 个 token" % len(tokens))
    print("成功续期到 %d 秒(2 小时): %d 个" % (TOKEN_TTL, renewed))
    if renewed != len(tokens):
        missing = [t for t, ok in zip(tokens, results) if not ok]
        print("有 %d 个 token 在 Redis 里不存在或已过期，例如: %s"
              % (len(missing), ", ".join(missing[:3])))
        raise SystemExit("续期不完整，请确认它们是在同一个 Redis(db=%d) 上生成的" % REDIS_DB)


if __name__ == "__main__":
    main()
