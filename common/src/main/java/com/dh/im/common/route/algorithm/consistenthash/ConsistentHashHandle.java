package com.dh.im.common.route.algorithm.consistenthash;


import com.dh.im.common.route.RouteHandle;

import java.util.List;

public class ConsistentHashHandle implements RouteHandle {

    private AbstractConsistentHash abstractConsistentHash;

    public AbstractConsistentHash getAbstractConsistentHash() {
        return abstractConsistentHash;
    }

    public void setAbstractConsistentHash(AbstractConsistentHash abstractConsistentHash) {
        this.abstractConsistentHash = abstractConsistentHash;
    }

    @Override
    public String routeServer(List<String> values, String key) {
        return abstractConsistentHash.process(values, key);
    }
}
