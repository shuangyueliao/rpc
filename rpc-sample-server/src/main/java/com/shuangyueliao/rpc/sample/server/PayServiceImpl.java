package com.shuangyueliao.rpc.sample.server;

import com.shuangyueliao.rpc.myinterface.PayService;
import com.shuangyueliao.rpc.common.RpcService;
import lombok.extern.slf4j.Slf4j;

/**
 * @author shuangyueliao
 * @create 2019/10/30 11:42
 * @Version 0.1
 */
@Slf4j
@RpcService(PayService.class)
public class PayServiceImpl implements PayService {
    @Override
    public int calculate(int a, int b) {
        int result = a + b;
        return result;
    }
}
