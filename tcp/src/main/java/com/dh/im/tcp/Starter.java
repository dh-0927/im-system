package com.dh.im.tcp;

import com.dh.im.codec.config.BootstrapConfig;
import com.dh.im.tcp.receiver.MessageReceiver;
import com.dh.im.tcp.redis.RedisManager;
import com.dh.im.tcp.register.RegistryZk;
import com.dh.im.tcp.register.ZKit;
import com.dh.im.tcp.server.ImServer;
import com.dh.im.tcp.server.ImWebSocketServer;
import com.dh.im.tcp.utils.MqFactory;
import org.I0Itec.zkclient.ZkClient;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Starter {
    public static void main(String[] args) throws FileNotFoundException {


        if (args.length > 0) {
            start(args[0]);
        }
    }

    private static void start(String path) {
        try {
            FileInputStream fileInputStream = new FileInputStream(path);
            BootstrapConfig config = new Yaml().loadAs(fileInputStream, BootstrapConfig.class);

            new ImServer(config.getIm()).start();
            new ImWebSocketServer(config.getIm()).start();

            RedisManager.init(config);
            MqFactory.init(config.getIm().getRabbitmq());
            MessageReceiver.init(config.getIm().getBrokerId().toString());

            registryZK(config);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(500);
        }
    }

    public static void registryZK(BootstrapConfig config) {
        try {

            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            ZkClient zkClient = new ZkClient(config.getIm().getZkConfig().getZkAddr(),
                    config.getIm().getZkConfig().getZkConnectTimeOut());
            ZKit zKit = new ZKit(zkClient);

            RegistryZk registryZk = new RegistryZk(zKit, hostAddress, config.getIm());
            new Thread(registryZk).start();

        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
