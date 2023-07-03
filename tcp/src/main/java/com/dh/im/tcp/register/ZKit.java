package com.dh.im.tcp.register;


import com.dh.im.common.constant.Constants;
import org.I0Itec.zkclient.ZkClient;

public class ZKit {

    private ZkClient zkClient;

    public ZKit(ZkClient zkClient) {
        this.zkClient = zkClient;
    }

    // im-coreRoot/tcp/ip:port

    public void createRootNode() {
        // 判断根节点是否存在
        if (!zkClient.exists(Constants.ImCoreZkRoot)) {
            zkClient.createPersistent(Constants.ImCoreZkRoot);
        }
        // 判断Tcp节点是否存在
        if (!zkClient.exists(Constants.ImCoreZkRoot + Constants.ImCoreZkRootTcp)) {
            zkClient.createPersistent(Constants.ImCoreZkRoot + Constants.ImCoreZkRootTcp);
        }
        // 判断webSocket是否存在
        if (!zkClient.exists(Constants.ImCoreZkRoot + Constants.ImCoreZkRootWeb)) {
            zkClient.createPersistent(Constants.ImCoreZkRoot + Constants.ImCoreZkRootWeb);
        }
    }

    public void createNode(String path) {
        if (!zkClient.exists(path)) {
            zkClient.createPersistent(path);
        }
    }
}
