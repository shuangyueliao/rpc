package com.shuangyueliao.rpc.sample.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author shuangyueliao
 * @create 2019/10/23 14:55
 * @Version 0.1
 */
@SpringBootApplication(scanBasePackages = {"com.shuangyueliao.rpc"})
public class StartApp {
    public static void main(String[] args) {
        SpringApplication.run(StartApp.class, args);
    }

}
