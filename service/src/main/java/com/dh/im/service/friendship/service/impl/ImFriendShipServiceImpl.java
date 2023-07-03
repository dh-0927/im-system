package com.dh.im.service.friendship.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dh.im.codec.park.friendship.*;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.*;
import com.dh.im.common.enums.command.FriendshipEventCommand;
import com.dh.im.common.model.RequestBase;
import com.dh.im.common.model.SyncReq;
import com.dh.im.common.model.SyncResp;
import com.dh.im.service.friendship.dao.ImFriendShipEntity;
import com.dh.im.service.friendship.dao.mapper.ImFriendShipMapper;
import com.dh.im.service.friendship.model.callback.AddFriendAfterCallbackDto;
import com.dh.im.service.friendship.model.callback.AddFriendBlackAfterCallbackDto;
import com.dh.im.service.friendship.model.callback.DeleteFriendAfterCallbackDto;
import com.dh.im.service.friendship.model.req.*;
import com.dh.im.service.friendship.model.resp.CheckFriendShipResp;
import com.dh.im.service.friendship.model.resp.ImportFriendShipResp;
import com.dh.im.service.friendship.service.ImFriendShipRequestService;
import com.dh.im.service.friendship.service.ImFriendShipService;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.user.dao.ImUserDataEntity;
import com.dh.im.service.user.model.req.GetUserInfoReq;
import com.dh.im.service.user.service.ImUserService;
import com.dh.im.service.utils.CallbackService;
import com.dh.im.service.utils.MessageProducer;
import com.dh.im.service.utils.WriteUserSeq;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ImFriendShipServiceImpl implements ImFriendShipService {


    @Autowired
    private ImFriendShipMapper imFriendShipMapper;

    @Autowired
    private ImUserService imUserService;

    @Autowired
    @Lazy
    private ImFriendShipRequestService imFriendShipRequestService;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private CallbackService callbackService;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private WriteUserSeq writeUserSeq;

    @Autowired
    private RedisSeq redisSeq;


    @Override
    public ResponseVO importFriendShip(ImportFriendShipReq req) {

        if (req.getFriendItem().size() > 100) {
            return ResponseVO.errorResponse(FriendShipErrorCode.IMPORT_SIZE_BEYOND);
        }

        List<String> successId = new ArrayList<>();
        List<String> errorId = new ArrayList<>();

        req.getFriendItem().forEach(item -> {

            ImFriendShipEntity entity = new ImFriendShipEntity();
            entity.setAppId(req.getAppId());
            entity.setFromId(req.getFromId());
            BeanUtils.copyProperties(item, entity);

            try {
                int count = imFriendShipMapper.insert(entity);
                if (count == 1) {
                    successId.add(item.getToId());
                }
            } catch (Exception e) {
                errorId.add(item.getToId());
                e.printStackTrace();
            }
        });

        ImportFriendShipResp response = new ImportFriendShipResp();
        response.setSuccessId(successId);
        response.setErrorId(errorId);

        return ResponseVO.successResponse(response);
    }

    @Override
//    @Transactional
    public ResponseVO addFriend(AddFriendReq req) {

        String fromId = req.getFromId();
        String toId = req.getToItem().getToId();
        Integer appId = req.getAppId();
        // 判断双方是否都存在
        ResponseVO<ImUserDataEntity> fromUser = imUserService.getSingleUserInfo(fromId, appId);
        ResponseVO<ImUserDataEntity> toUser = imUserService.getSingleUserInfo(toId, appId);
        if (fromUser.getData() == null || toUser.getData() == null) {
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }
        // 之前回调
        if ((appConfig.isAddFriendBeforeCallback())) {
            ResponseVO responseVO = callbackService.beforeCallback(req.getAppId(), Constants.CallbackCommand.AddFriendBefore,
                    JSONObject.toJSONString(req));
            if (!responseVO.isOk()) {
                return responseVO;
            }
        }

        // 双方都存在，判断是否需要认证
        if (toUser.getData().getFriendAllowType() == AllowFriendTypeEnum.NOT_NEED.getCode()) {
            // 无需验证
            return doAddFriend(req, fromId, req.getToItem(), req.getAppId());
        } else {
            // 首先在friendship表中查找两人关系
            LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
            lqw.eq(ImFriendShipEntity::getAppId, appId)
                    .eq(ImFriendShipEntity::getFromId, fromId)
                    .eq(ImFriendShipEntity::getToId, toId);

            ImFriendShipEntity entity = imFriendShipMapper.selectOne(lqw);
            if (entity == null || entity.getStatus() != FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()) {
                // 插入一条好友申请数据
                ResponseVO response = imFriendShipRequestService.addFriendshipRequest(fromId, req.getToItem(), appId);
                if (!response.isOk()) {
                    return response;
                }
            } else {
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_YOUR_FRIEND);
            }
        }

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO updateFriend(UpdateFriend req) {

        String fromId = req.getFromId();
        String toId = req.getToItem().getToId();
        // 判断双方是否都存在
        GetUserInfoReq userInfoReq = new GetUserInfoReq();
        userInfoReq.setUserIds(Arrays.asList(fromId, toId));
        if (imUserService.getUserInfo(userInfoReq).getData().getFailUser().size() == 0) {
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }

        ResponseVO responseVO = doUpdateFriend(fromId, req.getToItem(), req.getAppId());
        if (responseVO.isOk()) {

            UpdateFriendPack updateFriendPack = new UpdateFriendPack();
            updateFriendPack.setRemark(req.getToItem().getRemark());
            updateFriendPack.setToId(req.getToItem().getToId());
            messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(),
                    FriendshipEventCommand.FRIEND_UPDATE, updateFriendPack, req.getAppId());

            if (appConfig.isModifyFriendAfterCallback()) {
                AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
                callbackDto.setFromId(req.getFromId());
                callbackDto.setToItem(req.getToItem());
                callbackService.beforeCallback(req.getAppId(),
                        Constants.CallbackCommand.UpdateFriendAfter, JSONObject
                                .toJSONString(callbackDto));
            }
        }

        return responseVO;
    }

    @Override
    public ResponseVO deleteFriend(DeleteFriendReq req) {

        // 首先在friendship表中查找两人关系
        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        Integer appId = req.getAppId();
        String fromId = req.getFromId();
        lqw.eq(ImFriendShipEntity::getAppId, appId)
                .eq(ImFriendShipEntity::getFromId, fromId)
                .eq(ImFriendShipEntity::getToId, req.getToId());

        ImFriendShipEntity entity = imFriendShipMapper.selectOne(lqw);
        // 如果不是好友
        if (entity == null) {
            return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_NOT_YOUR_FRIEND);
        }
        // 如果已被删除
        if (!(entity.getStatus() == FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode())) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_DELETED);
        }
        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);

        // 正常删除
        entity.setStatus(FriendShipStatusEnum.FRIEND_STATUS_DELETE.getCode());
        entity.setFriendSequence(seq);
        imFriendShipMapper.update(entity, lqw);
        writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);

        DeleteFriendPack deleteFriendPack = new DeleteFriendPack();
        deleteFriendPack.setFromId(fromId);
        deleteFriendPack.setSequence(seq);
        deleteFriendPack.setToId(req.getToId());
        messageProducer.sendToUser(fromId,
                req.getClientType(), req.getImei(),
                FriendshipEventCommand.FRIEND_DELETE,
                deleteFriendPack, appId);

        //之后回调
        if (appConfig.isAddFriendAfterCallback()) {
            DeleteFriendAfterCallbackDto callbackDto = new DeleteFriendAfterCallbackDto();
            callbackDto.setFromId(fromId);
            callbackDto.setToId(req.getToId());
            callbackService.beforeCallback(appId,
                    Constants.CallbackCommand.DeleteFriendAfter, JSONObject
                            .toJSONString(callbackDto));
        }
        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO deleteAllFriend(@NotNull DeleteFriendReq req) {
        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getAppId, req.getAppId())
                .eq(ImFriendShipEntity::getFromId, req.getFromId())
                .eq(ImFriendShipEntity::getStatus, FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());

        ImFriendShipEntity imFriendShipEntity = new ImFriendShipEntity();
        imFriendShipEntity.setStatus(FriendShipStatusEnum.FRIEND_STATUS_DELETE.getCode());

        imFriendShipMapper.update(imFriendShipEntity, lqw);

        DeleteAllFriendPack deleteFriendPack = new DeleteAllFriendPack();
        deleteFriendPack.setFromId(req.getFromId());
        messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(), FriendshipEventCommand.FRIEND_ALL_DELETE,
                deleteFriendPack, req.getAppId());

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO<ImFriendShipEntity> getRelationFriend(GetRelationReq req) {

        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getAppId, req.getAppId())
                .eq(ImFriendShipEntity::getFromId, req.getFromId())
                .eq(ImFriendShipEntity::getToId, req.getToId());

        ImFriendShipEntity entity = imFriendShipMapper.selectOne(lqw);
        if (entity == null) {
            return ResponseVO.errorResponse(FriendShipErrorCode.REPEATSHIP_IS_NOT_EXIST);
        }

        return ResponseVO.successResponse(entity);
    }

    @Override
    public ResponseVO<List<ImFriendShipEntity>> getAllFriendShip(GetAllFriendShipReq req) {

        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getAppId, req.getAppId())
                .eq(ImFriendShipEntity::getFromId, req.getFromId());

        return ResponseVO.successResponse(imFriendShipMapper.selectList(lqw));
    }

    /**
     * 单向校验：判断 from 好友列表是否有 to（两种情况，有或没有）
     * 双向校验：既会检查 from 好友列表是否有 to， 也会检查 to 好友列表是否有 from（四种情况）
     */
    @Override
    public ResponseVO checkFriendShip(CheckFriendShipReq req) {

        List<CheckFriendShipResp> response = new ArrayList<>();
        // 判断是双向校验还是单向校验
        if (req.getCheckType() == CheckFriendShipTypeEnum.SINGLE.getType()) {
            // 如果是单向校验
            response = imFriendShipMapper.checkFriendShipSingle(req);
        } else {
            // 否则是双向校验
            response = imFriendShipMapper.checkFriendShipBoth(req);
        }
        // 对结果进行处理
        List<String> ids = new ArrayList<>();
        if (response != null) {
            ids = response.stream()
                    .map(CheckFriendShipResp::getToId)
                    .collect(Collectors.toList());
        }

        for (String id : req.getToIds()) {
            if (!ids.contains(id)) {
                CheckFriendShipResp res = new CheckFriendShipResp();
                res.setFromId(req.getFromId());
                res.setToId(id);
                res.setStatus(0);
                response.add(res);
            }
        }

        return ResponseVO.successResponse(response);
    }

    @Override
    public ResponseVO addBlack(AddFriendShipBlackReq req) {
        Integer appId = req.getAppId();
        String fromId = req.getFromId();
        String toId = req.getToId();
        // 首先在friendship表中查找两人关系
        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getAppId, appId)
                .eq(ImFriendShipEntity::getFromId, fromId)
                .eq(ImFriendShipEntity::getToId, toId);

        ImFriendShipEntity entity = imFriendShipMapper.selectOne(lqw);
        if (entity == null) {
            // 插入拉黑数据
            entity = new ImFriendShipEntity();
            // 封装数据
            long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);
            entity.setFriendSequence(seq);
            entity.setAppId(appId);
            entity.setFromId(fromId);
            entity.setToId(toId);
            entity.setStatus(0);
            entity.setBlack(FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode());
            entity.setCreateTime(System.currentTimeMillis());

            if (imFriendShipMapper.insert(entity) != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_BLACK_ERROR);
            }
            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);
            return ResponseVO.successResponse();
        }
        // 如果已经拉黑
        if (entity.getBlack() == FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode()) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_BLACK);
        }
        // 更新状态为拉黑状态
        ImFriendShipEntity friendShipEntity = new ImFriendShipEntity();
        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);
        friendShipEntity.setFriendSequence(seq);
        friendShipEntity.setBlack(FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode());
        int update = imFriendShipMapper.update(friendShipEntity, lqw);
        if (update != 1) {
            return ResponseVO.errorResponse(FriendShipErrorCode.ADD_BLACK_ERROR);
        }
        writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);

        AddFriendBlackPack addFriendBlackPack = new AddFriendBlackPack();
        addFriendBlackPack.setFromId(req.getFromId());
        addFriendBlackPack.setSequence(seq);
        addFriendBlackPack.setToId(req.getToId());
        //发送tcp通知
        messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(),
                FriendshipEventCommand.FRIEND_BLACK_ADD, addFriendBlackPack, req.getAppId());

        //之后回调
        if (appConfig.isAddFriendShipBlackAfterCallback()) {
            AddFriendBlackAfterCallbackDto callbackDto = new AddFriendBlackAfterCallbackDto();
            callbackDto.setFromId(req.getFromId());
            callbackDto.setToId(req.getToId());
            callbackService.beforeCallback(req.getAppId(),
                    Constants.CallbackCommand.AddBlackAfter, JSONObject
                            .toJSONString(callbackDto));
        }

        return ResponseVO.successResponse();

    }

    @Override
    public ResponseVO deleteBlack(DeleteBlackReq req) {
        Integer appId = req.getAppId();
        String fromId = req.getFromId();
        String toId = req.getToId();
        // 首先在friendship表中查找两人关系
        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getAppId, appId)
                .eq(ImFriendShipEntity::getFromId, fromId)
                .eq(ImFriendShipEntity::getToId, toId);
        ImFriendShipEntity entity = imFriendShipMapper.selectOne(lqw);
        if (entity == null) {
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }
        if (entity.getBlack() != null && entity.getBlack() == FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_NOT_YOUR_BLACK);
        }
        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);
        ImFriendShipEntity update = new ImFriendShipEntity();
        update.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
        imFriendShipMapper.update(update, lqw);
        writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);


        DeleteBlackPack deleteFriendPack = new DeleteBlackPack();
        deleteFriendPack.setFromId(req.getFromId());
        deleteFriendPack.setSequence(seq);
        deleteFriendPack.setToId(req.getToId());
        messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(), FriendshipEventCommand.FRIEND_BLACK_DELETE,
                deleteFriendPack, req.getAppId());

        //之后回调
        if (appConfig.isAddFriendShipBlackAfterCallback()) {
            AddFriendBlackAfterCallbackDto callbackDto = new AddFriendBlackAfterCallbackDto();
            callbackDto.setFromId(req.getFromId());
            callbackDto.setToId(req.getToId());
            callbackService.beforeCallback(req.getAppId(),
                    Constants.CallbackCommand.DeleteBlack, JSONObject
                            .toJSONString(callbackDto));
        }

        return ResponseVO.successResponse();

    }


    @Override
    public ResponseVO checkBlack(CheckFriendShipReq req) {

        List<CheckFriendShipResp> response = new ArrayList<>();
        // 判断是双向校验还是单向校验
        if (req.getCheckType() == CheckFriendShipTypeEnum.SINGLE.getType()) {
            // 如果是单向校验
            response = imFriendShipMapper.checkFriendShipBlackSingle(req);
        } else {
            // 否则是双向校验
            response = imFriendShipMapper.checkFriendShipBlackBoth(req);
        }
        // 对结果进行处理
        List<String> ids = new ArrayList<>();
        if (response != null) {
            ids = response.stream()
                    .map(CheckFriendShipResp::getToId)
                    .collect(Collectors.toList());
        }

        for (String id : req.getToIds()) {
            if (!ids.contains(id)) {
                CheckFriendShipResp res = new CheckFriendShipResp();
                res.setFromId(req.getFromId());
                res.setToId(id);
                res.setStatus(0);
                response.add(res);
            }
        }

        return ResponseVO.successResponse(response);
    }

    @Override
    public ResponseVO syncFriendshipList(SyncReq req) {

        if (req.getMaxLimit() > 100) {
            req.setMaxLimit(100);
        }

        SyncResp<ImFriendShipEntity> resp = new SyncResp<>();
        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getFromId, req.getOperator())
                .eq(ImFriendShipEntity::getAppId, req.getAppId())
                .gt(ImFriendShipEntity::getFriendSequence, req.getLastSequence())
                .last("limit " + req.getMaxLimit())
                .orderByAsc(ImFriendShipEntity::getFriendSequence);

        List<ImFriendShipEntity> list = imFriendShipMapper.selectList(lqw);

        if (CollectionUtils.isNotEmpty(list)) {
            ImFriendShipEntity maxSeqEntity = list.get(list.size() - 1);
            resp.setDataList(list);
            Long maxSeq = imFriendShipMapper.getFriendShipMaxSeq(req.getAppId(), req.getOperator());
            resp.setMaxSequence(maxSeq);
            resp.setCompleted(maxSeqEntity.getFriendSequence() >=  maxSeq);
            return ResponseVO.successResponse(resp);
        }
        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);
    }


    @Transactional
    public ResponseVO doUpdateFriend(String fromId, FriendDto friendDto, Integer appId) {

        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);

        LambdaUpdateWrapper<ImFriendShipEntity> luw = new LambdaUpdateWrapper<>();
        luw.set(ImFriendShipEntity::getAddSource, friendDto.getAddSource())
                .set(ImFriendShipEntity::getRemark, friendDto.getRemark())
                .set(ImFriendShipEntity::getExtra, friendDto.getExtra())
                .set(ImFriendShipEntity::getFriendSequence, seq)
                .eq(ImFriendShipEntity::getFromId, fromId)
                .eq(ImFriendShipEntity::getToId, friendDto.getToId())
                .eq(ImFriendShipEntity::getAppId, appId);

        imFriendShipMapper.update(null, luw);
        writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);
        return ResponseVO.successResponse();
    }

    @Transactional
    public ResponseVO doAddFriend(RequestBase requestBase, String fromId, FriendDto friendDto, Integer appId) {

        // 首先在friendship表中查找两人关系
        LambdaQueryWrapper<ImFriendShipEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipEntity::getAppId, appId)
                .eq(ImFriendShipEntity::getFromId, fromId)
                .eq(ImFriendShipEntity::getToId, friendDto.getToId());

        ImFriendShipEntity entity = imFriendShipMapper.selectOne(lqw);

        long seq = 0L;
        if (entity == null) {
            // 如果为空，直接插入新的关系
            entity = new ImFriendShipEntity();
            seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship/* + ":" + fromId*/);
            BeanUtils.copyProperties(friendDto, entity);
            entity.setAppId(appId);
            entity.setFriendSequence(seq);
            entity.setFromId(fromId);
            entity.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            entity.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
            entity.setCreateTime(System.currentTimeMillis());

            int count = imFriendShipMapper.insert(entity);
            if (count != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
            }
            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);
        } else {
            // 如果不为空，判断是否已添加好友
            if (FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode() == entity.getStatus()) {
                // 如果已添加，返回已添加
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_YOUR_FRIEND);
            }

            // 如果未添加，执行更新操作
            String remark = friendDto.getRemark();
            String addResource = friendDto.getAddSource();
            String extra = friendDto.getExtra();
            if (StringUtils.isNotBlank(remark)) {
                entity.setRemark(remark);
            }
            if (StringUtils.isNotBlank(addResource)) {
                entity.setAddSource(addResource);
            }
            if (StringUtils.isNotBlank(extra)) {
                entity.setExtra(extra);
            }
            seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship/* + ":" + fromId*/);
            entity.setFriendSequence(seq);
            entity.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            int update = imFriendShipMapper.update(entity, lqw);
            if (update != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
            }
            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship, seq);

        }

        // 插入另一条
        LambdaQueryWrapper<ImFriendShipEntity> toLqw = new LambdaQueryWrapper<>();
        toLqw.eq(ImFriendShipEntity::getAppId, appId);
        toLqw.eq(ImFriendShipEntity::getFromId, friendDto.getToId());
        toLqw.eq(ImFriendShipEntity::getToId, fromId);
        ImFriendShipEntity toItem = imFriendShipMapper.selectOne(toLqw);
        if (toItem == null) {
            toItem = new ImFriendShipEntity();
            toItem.setFriendSequence(seq);
            toItem.setAppId(appId);
            toItem.setFromId(friendDto.getToId());
            BeanUtils.copyProperties(friendDto, toItem);
            toItem.setToId(fromId);
            toItem.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            toItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
            toItem.setCreateTime(System.currentTimeMillis());
//            toItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
            imFriendShipMapper.insert(toItem);
            writeUserSeq.writeUserSeq(appId, friendDto.getToId(), Constants.SeqConstants.Friendship, seq);

        } else {
            if (FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode() != toItem.getStatus()) {
                ImFriendShipEntity update = new ImFriendShipEntity();
                toItem = new ImFriendShipEntity();
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
                imFriendShipMapper.update(update, toLqw);
                writeUserSeq.writeUserSeq(appId, friendDto.getToId(), Constants.SeqConstants.Friendship, seq);
            }
        }

        //发送给from
        AddFriendPack addFriendPack = new AddFriendPack();
        BeanUtils.copyProperties(friendDto, addFriendPack);
        addFriendPack.setSequence(seq);
        if (requestBase != null) {
            messageProducer.sendToUser(fromId, requestBase.getClientType(),
                    requestBase.getImei(), FriendshipEventCommand.FRIEND_ADD, addFriendPack,
                    requestBase.getAppId());
        } else {
            // 发给所有端
            messageProducer.sendToUser(fromId,
                    FriendshipEventCommand.FRIEND_ADD, addFriendPack, appId);
        }
        // 发送给toItem
        AddFriendPack addFriendToPack = new AddFriendPack();
        BeanUtils.copyProperties(toItem, addFriendPack);
        messageProducer.sendToUser(toItem.getFromId(),
                FriendshipEventCommand.FRIEND_ADD, addFriendToPack, appId);

        AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
        callbackDto.setFromId(fromId);
        callbackDto.setToItem(friendDto);
        if ((appConfig.isAddFriendBeforeCallback())) {
            callbackService.callback(appId, Constants.CallbackCommand.AddFriendAfter,
                    JSONObject.toJSONString(callbackDto));
        }

        return ResponseVO.successResponse();
    }
}
