package com.dh.im.service.friendship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dh.im.codec.park.friendship.ApproverFriendRequestPack;
import com.dh.im.codec.park.friendship.ReadAllFriendRequestPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ApproveFriendRequestStatusEnum;
import com.dh.im.common.enums.FriendShipErrorCode;
import com.dh.im.common.enums.command.FriendshipEventCommand;
import com.dh.im.common.exception.ApplicationException;
import com.dh.im.service.friendship.dao.ImFriendShipRequestEntity;
import com.dh.im.service.friendship.dao.mapper.ImFriendShipRequestMapper;
import com.dh.im.service.friendship.model.req.ApproveFriendRequestReq;
import com.dh.im.service.friendship.model.req.FriendDto;
import com.dh.im.service.friendship.model.req.ReadFriendShipRequestReq;
import com.dh.im.service.friendship.service.ImFriendShipRequestService;
import com.dh.im.service.friendship.service.ImFriendShipService;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.utils.MessageProducer;
import com.dh.im.service.utils.WriteUserSeq;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

@Service
public class ImFriendShipRequestServiceImpl implements ImFriendShipRequestService {

    @Autowired
    private ImFriendShipRequestMapper imFriendShipRequestMapper;

    @Autowired
    private ImFriendShipService imFriendShipService;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private RedisSeq redisSeq;

    @Autowired
    private WriteUserSeq writeUserSeq;

    @Override
    public ResponseVO addFriendshipRequest(String fromId, FriendDto dto, Integer appId) {
        // 首先查询是否有记录
        LambdaQueryWrapper<ImFriendShipRequestEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipRequestEntity::getAppId, appId)
                .eq(ImFriendShipRequestEntity::getFromId, fromId)
                .eq(ImFriendShipRequestEntity::getToId, dto.getToId());

        ImFriendShipRequestEntity entity = imFriendShipRequestMapper.selectOne(lqw);
        if (entity == null) {

            long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.FriendshipRequest);

            // 直接插入
            entity = new ImFriendShipRequestEntity();
            BeanUtils.copyProperties(dto, entity);
            entity.setFromId(fromId);
            entity.setSequence(seq);
            entity.setAppId(appId);
            long nowTime = System.currentTimeMillis();
            entity.setUpdateTime(nowTime);
            entity.setCreateTime(nowTime);
            entity.setReadStatus(0);
            entity.setApproveStatus(0);
            imFriendShipRequestMapper.insert(entity);

            writeUserSeq.writeUserSeq(appId, dto.getToId(), Constants.SeqConstants.FriendshipRequest, seq);

            return ResponseVO.successResponse();
        }
        String remark = dto.getRemark();
        if (StringUtils.isNotBlank(remark)) {
            entity.setRemark(remark);
        }
        String addSource = dto.getAddSource();
        if (StringUtils.isNotBlank(addSource)) {
            entity.setAddSource(addSource);
        }
        String addWording = dto.getAddWording();
        if (StringUtils.isNotBlank(addWording)) {
            entity.setAddWording(addWording);
        }

        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.FriendshipRequest);

        entity.setSequence(seq);
        entity.setUpdateTime(System.currentTimeMillis());
        imFriendShipRequestMapper.updateById(entity);

        writeUserSeq.writeUserSeq(appId, dto.getToId(), Constants.SeqConstants.FriendshipRequest, seq);

        //发送好友申请的tcp给接收方
        messageProducer.sendToUser(dto.getToId(),
                null, "", FriendshipEventCommand.FRIEND_REQUEST,
                entity, appId);

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO getFriendRequest(String fromId, Integer appId) {

        LambdaQueryWrapper<ImFriendShipRequestEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipRequestEntity::getAppId, appId);
        lqw.eq(ImFriendShipRequestEntity::getToId, fromId);

        List<ImFriendShipRequestEntity> requestList = imFriendShipRequestMapper.selectList(lqw);

        return ResponseVO.successResponse(requestList);
    }


    //A + B

    @Override
    @Transactional
    public ResponseVO approveFriendRequest(ApproveFriendRequestReq req) {
        Integer appId = req.getAppId();
        String operator = req.getOperator();

        ImFriendShipRequestEntity imFriendShipRequestEntity = imFriendShipRequestMapper.selectById(req.getId());
        if (imFriendShipRequestEntity == null) {
            throw new ApplicationException(FriendShipErrorCode.FRIEND_REQUEST_IS_NOT_EXIST);
        }

        if (!operator.equals(imFriendShipRequestEntity.getToId())) {
            //只能审批发给自己的好友请求
            throw new ApplicationException(FriendShipErrorCode.NOT_APPROVE_OTHER_MAN_REQUEST);
        }


        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.FriendshipRequest);

        ImFriendShipRequestEntity update = new ImFriendShipRequestEntity();
        update.setApproveStatus(req.getStatus());
        update.setUpdateTime(System.currentTimeMillis());
        update.setSequence(seq);
        update.setApproveStatus(ApproveFriendRequestStatusEnum.AGREE.getCode());
        update.setReadStatus(1);
        update.setId(req.getId());
        imFriendShipRequestMapper.updateById(update);
        writeUserSeq.writeUserSeq(appId, operator, Constants.SeqConstants.FriendshipRequest, seq);


        if (ApproveFriendRequestStatusEnum.AGREE.getCode() == req.getStatus()) {
            //同意 ===> 去执行添加好友逻辑
            FriendDto dto = new FriendDto();
            dto.setAddSource(imFriendShipRequestEntity.getAddSource());
            dto.setAddWording(imFriendShipRequestEntity.getAddWording());
            dto.setRemark(imFriendShipRequestEntity.getRemark());
            dto.setToId(imFriendShipRequestEntity.getToId());
            ResponseVO responseVO = imFriendShipService.doAddFriend(req, imFriendShipRequestEntity.getFromId(), dto, appId);
            if(!responseVO.isOk()){
                ApproverFriendRequestPack approverFriendRequestPack = new ApproverFriendRequestPack();
                approverFriendRequestPack.setId(req.getId());
                approverFriendRequestPack.setSequence(seq);
                approverFriendRequestPack.setStatus(req.getStatus());
                messageProducer.sendToUser(imFriendShipRequestEntity.getToId(),req.getClientType(),req.getImei(), FriendshipEventCommand
                        .FRIEND_REQUEST_APPROVER,approverFriendRequestPack, appId);
            }
            if (!responseVO.isOk() && responseVO.getCode() != FriendShipErrorCode.TO_IS_YOUR_FRIEND.getCode()) {
                return responseVO;
            }
        }


        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO readFriendShipRequestReq(ReadFriendShipRequestReq req) {
        Integer appId = req.getAppId();
        String fromId = req.getFromId();

        LambdaQueryWrapper<ImFriendShipRequestEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipRequestEntity::getAppId, appId);
        lqw.eq(ImFriendShipRequestEntity::getToId, fromId);


        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.FriendshipRequest);
        ImFriendShipRequestEntity update = new ImFriendShipRequestEntity();
        update.setSequence(seq);
        update.setReadStatus(1);
        if (imFriendShipRequestMapper.update(update, lqw) == 1) {
            writeUserSeq.writeUserSeq(appId, req.getOperator(), Constants.SeqConstants.FriendshipRequest, seq);
            //TCP通知
            ReadAllFriendRequestPack readAllFriendRequestPack = new ReadAllFriendRequestPack();
            readAllFriendRequestPack.setFromId(fromId);
            readAllFriendRequestPack.setSequence(seq);
            messageProducer.sendToUser(fromId,req.getClientType(),req.getImei(),FriendshipEventCommand
                    .FRIEND_REQUEST_READ,readAllFriendRequestPack, appId);
            return ResponseVO.successResponse();
        }

        return ResponseVO.errorResponse();
    }
}
