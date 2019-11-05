package com.shuangyueliao.rpc.register;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author shuangyueliao
 * @create 2019/10/23 15:39
 * @Version 0.1
 */
@Component
@Slf4j
public class ServiceRegister {
    private String registerAddress;
    private ZooKeeper zk;
    private String dataPath;

    public ServiceRegister(@Value("${zookeeper.url}")String registerAddress, @Value("${zookeeper.register.path.prefix}")String dataPath) {
        this.registerAddress = registerAddress;
        this.dataPath = dataPath;
        try {
            zk = new ZooKeeper(registerAddress, 5000, (event) -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    log.info("zookeeper建立连接");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void register(String data) {
        if (data != null) {
            byte[] bytes = data.getBytes();
            try {
                if (zk.exists(dataPath, null) == null) {
                    zk.create(dataPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
                zk.create(dataPath + "/data", bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
