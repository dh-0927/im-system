package com.dh.im.service.config;

import com.dh.im.common.config.AppConfig;
import com.dh.im.common.enums.ImUrlRouteWayEnum;
import com.dh.im.common.enums.RouteHashMethodEnum;
import com.dh.im.common.route.RouteHandle;
import com.dh.im.common.route.algorithm.consistenthash.AbstractConsistentHash;
import com.dh.im.service.utils.SnowflakeIdWorker;
import org.I0Itec.zkclient.ZkClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Objects;

@Configuration
public class BeanConfig {

    @Autowired
    private AppConfig appConfig;

    @Bean
    public ZkClient buildZKClient() {
        return new ZkClient(appConfig.getZkAddr(), appConfig.getZkConnectTimeOut());
    }


    @Bean
    public RouteHandle routeHandle() throws Exception {
        Integer imRouteWay = appConfig.getImRouteWay();
        String routWay = "";

        ImUrlRouteWayEnum handler = ImUrlRouteWayEnum.getHandler(imRouteWay);
        routWay = Objects.requireNonNull(handler).getClazz();

        RouteHandle routeHandle = (RouteHandle) Class.forName(routWay).newInstance();
        if (handler == ImUrlRouteWayEnum.HASH) {

            Method setHash = Class.forName(routWay).getMethod("setAbstractConsistentHash", AbstractConsistentHash.class);
            Integer consistentHashWay = appConfig.getConsistentHashWay();
            String hashWay = "";

            RouteHashMethodEnum hashHandler = RouteHashMethodEnum.getHandler(consistentHashWay);
            hashWay = Objects.requireNonNull(hashHandler).getClazz();
            AbstractConsistentHash consistentHash
                    = (AbstractConsistentHash) Class.forName(hashWay).newInstance();
            setHash.invoke(routeHandle, consistentHash);
        }


        return routeHandle;

    }

    @Bean
    public EasySqlInjector easySqlInjector () {
        return new EasySqlInjector();
    }

    @Bean
    public SnowflakeIdWorker buildSnowflakeSeq() throws Exception {
        return new SnowflakeIdWorker(0);
    }


}
