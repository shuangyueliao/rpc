package com.shuangyueliao.rpc.register;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author shuangyueliao
 * @create 2019/10/23 15:39
 * @Version 0.1
 */
@Slf4j
@Component
public class ServiceDiscovery {
    private String registerAddress;
    private ZooKeeper zk;
    @Value("${zookeeper.register.path.prefix}")
    private String dataPath;
    private volatile List<String> dataList = new ArrayList<String>();

    public ServiceDiscovery(@Value("${zookeeper.url}") String registerAddress) {
        try {
            this.registerAddress = registerAddress;
            zk = new ZooKeeper(registerAddress, 5000, (event) -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    log.info("zookeeper建立连接");
                    watchNode();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void watchNode() {
        try {
            // 获取所有子节点
            List<String> nodeList = zk.getChildren(dataPath,
                    event -> {
                        // 节点改变
                        if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                            watchNode();
                        }
                    });
            List<String> dataList = new ArrayList<String>();
            // 循环子节点
            for (String node : nodeList) {
                // 获取节点中的服务器地址
                byte[] bytes = zk.getData(dataPath + "/"
                        + node, false, null);
                // 存储到list中
                dataList.add(new String(bytes));
            }
            // 将节点信息记录在成员变量
            this.dataList = dataList;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String discover() {
        String data = null;
        int size = dataList.size();
        // 存在新节点，使用即可
        if (size > 0) {
            if (size == 1) {
                data = dataList.get(0);
            } else {
                data = dataList.get(ThreadLocalRandom.current().nextInt(size));
            }
        }
        return data;
    }
}
