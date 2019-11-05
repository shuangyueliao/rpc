package com.shuangyueliao.rpc.myinterface;

import com.shuangyueliao.rpc.client.RpcClientService;

/**
 * @author shuangyueliao
 * @create 2019/10/30 14:19
 * @Version 0.1
 */
@RpcClientService
public interface PayService {
    int calculate(int a, int b);
}
