package com.shuangyueliao.rpc.sample.server.config;

import com.shuangyueliao.rpc.register.ServiceRegister;
import com.shuangyueliao.rpc.server.RpcServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author shuangyueliao
 * @create 2019/10/30 13:36
 * @Version 0.1
 */
@Configuration
public class Config {
    @Value("${register.address}")
    private String serverAddress;
    @Autowired
    private ServiceRegister serviceRegister;

    @Bean
    public RpcServer getRpcServer() {
        return new RpcServer(serverAddress, serviceRegister);
    }
}
