package com.dh.im.tcp.register;

import com.dh.im.codec.config.BootstrapConfig;
import com.dh.im.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegistryZk implements Runnable{

    private ZKit zKit;

    private String ip;

    private BootstrapConfig.TcpConfig tcpConfig;

    public RegistryZk(ZKit zKit, String ip, BootstrapConfig.TcpConfig tcpConfig) {
        this.zKit = zKit;
        this.ip = ip;
        this.tcpConfig = tcpConfig;
    }

    @Override
    public void run() {

        zKit.createRootNode();

        String tcpPath =
                Constants.ImCoreZkRoot + Constants.ImCoreZkRootTcp + "/" + ip + ":" + tcpConfig.getTcpPort();
        zKit.createNode(tcpPath);

        log.info("Registry zookeeper tcpPath success, msg=[{}]", tcpPath);


        String webPath =
                Constants.ImCoreZkRoot + Constants.ImCoreZkRootWeb + "/" + ip + ":" + tcpConfig.getWebSocketPort();
        zKit.createNode(webPath);

        log.info("Registry zookeeper webPath success, msg=[{}]", webPath);


    }
}
