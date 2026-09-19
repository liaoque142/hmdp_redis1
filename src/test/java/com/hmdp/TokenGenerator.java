package com.hmdp;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

@Slf4j
@SpringBootTest
public class TokenGenerator {

    @Test
    public void generateTokens() {
        // 1. 目标文件路径（绝对路径，方便 JMeter 读取）
        String outputFile = "tokens.csv"; // 请改为你实际想保存的路径

        // 2. 项目接口地址（根据你本地实际端口修改）
        //    注意：本测试带 @SpringBootTest 但默认是 MOCK 环境，不会启动 Tomcat，
        //    所以必须先单独把 HmDianPingApplication 跑起来，保证 8081 有监听。
        String baseUrl = "http://localhost:8081";
        String loginUrl = baseUrl + "/user/login";

        // 给 RestTemplate 加上超时。默认实现没有超时，1000 次串行请求里只要有一次
        // 卡住，整个任务就会一直挂在那里。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        RestTemplate restTemplate = new RestTemplate(factory);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (int i = 0; i < 1000; i++) {
                // 生成不同的手机号，例如 13800000001 到 13800001000
                String phone = "138" + String.format("%08d", i + 1);
                // 必须和 tools/seed_login_codes.py 预置进 Redis 的 login:code:{phone} 一致
                String code = "123456";

                // 这里【不能】再调 /user/code：sendCode 生成的是 RandomUtil.randomNumbers(6)
                // 随机码，会把我们预置的 "123456" 覆盖掉，导致下面的 login 报"验证码错误"。
                // 验证码改由 tools/seed_login_codes.py 直接写进 Redis，跑本测试前先执行它。
                // try {
                //     restTemplate.postForEntity(baseUrl + "/user/code?phone=" + phone, null, String.class);
                // } catch (Exception e) {
                //     log.warn("发送验证码失败: {}", phone);
                // }

                // 3. 构建登录请求体 JSON
                JSONObject loginBody = new JSONObject();
                loginBody.set("phone", phone);
                loginBody.set("code", code);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> request = new HttpEntity<>(loginBody.toString(), headers);

                try {
                    // 4. 发送登录请求
                    ResponseEntity<String> response = restTemplate.postForEntity(loginUrl, request, String.class);
                    String body = response.getBody();
                    log.info("第 {} 个用户登录响应: {}", i + 1, body);

                    // 5. 解析响应中的 Token
                    // 黑马点评的 Result 结构通常是 {"success":true, "data":"token字符串", "errorMsg":null}
                    JSONObject jsonObject = JSONUtil.parseObj(body);
                    if (jsonObject.getBool("success")) {
                        String token = jsonObject.getStr("data");
                        // 6. 写入文件，一行一个 Token
                        writer.write(token);
                        writer.newLine();
                    } else {
                        log.error("第 {} 个用户登录失败: {}", i + 1, jsonObject.getStr("errorMsg"));
                    }
                } catch (Exception e) {
                    log.error("请求异常，第 {} 个用户", i + 1, e);
                }
            }
            log.info("Token 生成完毕，文件位置: {}", outputFile);
        } catch (IOException e) {
            log.error("写入文件失败", e);
        }
    }
}