package com.dh.im.common.route.algorithm.ramdom;

import com.dh.im.common.enums.UserErrorCode;
import com.dh.im.common.exception.ApplicationException;
import com.dh.im.common.route.RouteHandle;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomHandle implements RouteHandle {

    @Override
    public String routeServer(List<String> values, String key) {

        int size = values.size();
        if (size == 0) {
            throw new ApplicationException(UserErrorCode.SERVER_NOT_AVAILABLE);
        }
        int i = ThreadLocalRandom.current().nextInt(size);
        return values.get(i);
    }

}
