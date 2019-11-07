package com.shuangyueliao.rpc.sample.client.service.impl;

import com.shuangyueliao.rpc.common.RpcClientService;
import com.shuangyueliao.rpc.myinterface.PayService;
import com.shuangyueliao.rpc.sample.client.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * @author shuangyueliao
 * @create 2019/10/23 14:22
 * @Version 0.1
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private PayService payService;

    @Override
    public void order() {
        log.info("开启订单");
        int result = payService.calculate(100, 200);
        log.info("此订单需要支付金额{}元", result);
        log.info("关闭订单");
    }

    public static void main(String[] args) {
        Set<Class<?>> typesAnnotatedWith = new Reflections("com.shuangyueliao.rpc.myinterface").getTypesAnnotatedWith(RpcClientService.class);
        typesAnnotatedWith.forEach(System.out::println);
    }
}
