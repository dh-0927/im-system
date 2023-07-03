package com.dh.im.service.message.dao.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.im.service.message.dao.ImMessageHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;

@Mapper
public interface ImMessageHistoryMapper extends BaseMapper<ImMessageHistoryEntity> {
    Integer insertBatchSomeColumn(Collection<ImMessageHistoryEntity> entityList);
}
