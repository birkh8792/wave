package com.wave.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wave.common.WaveConstants;
import com.wave.exception.WaveException;
import com.wave.user.dao.UserAccountDao;
import com.wave.user.dao.UserInfoDao;
import com.wave.user.dao.entity.AccountEntity;
import com.wave.user.dao.entity.UserInfoEntity;
import com.wave.user.dto.UserInfoDto;
import com.wave.user.dto.req.UserInfoModifyReqDto;
import com.wave.user.dto.req.UserInfoUpdateReqDto;
import com.wave.user.service.UserInfoService;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserInfoServiceImpl implements UserInfoService{

    @Autowired
    UserInfoDao userInfoDao;

    @Autowired
    UserAccountDao userAccountDao;

    @Autowired
    RedissonClient redissonClient;

    private final String USER_INFO_KEY = "USER_INFO:";

    @Override
    public void registerUser(UserInfoModifyReqDto registryReqDto) throws WaveException {
        // 查询是否有同名mobile
        QueryWrapper<UserInfoEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mobile_no", registryReqDto.getMobileNo()).eq("status", WaveConstants.NORMAL_STATUS);
        List<UserInfoEntity> entityList = userInfoDao.selectList(queryWrapper);
        if (!CollectionUtils.isEmpty(entityList)) {
            throw new WaveException(WaveException.INVALID_PARAM, "账号已注册");
        }
        // 如有返回错误
        UserInfoEntity userInfoEntity = new UserInfoEntity();
        userInfoEntity.setMobileNo(registryReqDto.getMobileNo());
        userInfoEntity.setUserName(registryReqDto.getMobileNo());
        userInfoDao.insert(userInfoEntity);
    }

    @Override
    public void userInfoUpdate(UserInfoUpdateReqDto reqDto) throws WaveException {
        // 查询userId，如果没有，则增加用户信息
        UserInfoDto userInfo = getUserInfoByAccount(reqDto.getAccount());
        UserInfoEntity userInfoEntity = new UserInfoEntity();
        userInfoEntity.setMobileNo(reqDto.getMobileNo());
        userInfoEntity.setUserName(reqDto.getMobileNo());
        userInfoEntity.setImageUrl(reqDto.getImageUrl());
        if (null == userInfo || null == userInfo.getUserId()) {
            // 新增用户信息
            userInfoDao.insert(userInfoEntity);
            // 更新account信息
            UpdateWrapper<AccountEntity> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("account", reqDto.getAccount()).eq("status", 0);

            AccountEntity accountEntity = new AccountEntity();
            accountEntity.setUserId(userInfoEntity.getId());

            userAccountDao.update(accountEntity, updateWrapper);
            return;
        }
        // 更新用户信息
        userInfoEntity.setId(userInfo.getUserId());
        userInfoDao.updateById(userInfoEntity);
    }

    @Override
    public UserInfoDto getUserInfo(String userId) throws WaveException {
        // 查询缓存
        RBucket<UserInfoDto> bucket = redissonClient.getBucket(USER_INFO_KEY + userId);
        UserInfoDto userInfoDto = bucket.get();
        if (null != userInfoDto) {
            return userInfoDto;
        }
        QueryWrapper<UserInfoEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userId).eq("status", WaveConstants.NORMAL_STATUS);
        List<UserInfoEntity> entityList = userInfoDao.selectList(queryWrapper);
        boolean empty = CollectionUtils.isEmpty(entityList);
        if (!empty && entityList.size() > 1) {
            throw new WaveException(WaveException.SERVER_ERROR, "多重账号");
        }
        UserInfoDto resDto = new UserInfoDto();
        if (!empty) {
            BeanUtils.copyProperties(entityList.get(0), resDto);
        }
        // 放入缓存，异步？  resDt为空也放入缓存，防止缓存穿透
        bucket.set(resDto, RandomUtils.nextInt(0, 120), TimeUnit.MINUTES);
        return resDto;
    }

    @Override
    public UserInfoDto getUserInfoByAccount(String account) throws WaveException {

        // 查询缓存
        RBucket<UserInfoDto> bucket = redissonClient.getBucket(USER_INFO_KEY + account);
        UserInfoDto userInfoDto = bucket.get();
        if (null != userInfoDto) {
            return userInfoDto;
        }
        // 查库获取userId
        QueryWrapper<AccountEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        queryWrapper.eq("status", WaveConstants.NORMAL_STATUS);
        List<AccountEntity> accounts = userAccountDao.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(accounts)) {
            throw new WaveException(WaveException.INVALID_PARAM, "用户未注册");
        }
        if (accounts.size() > 1) {
            throw new WaveException(WaveException.SERVER_ERROR, "多用户");
        }
        Long userId = accounts.get(0).getUserId();

        // 查询user
        userInfoDto = getUserInfo(userId.toString());
        bucket.set(userInfoDto, RandomUtils.nextInt(60, 120), TimeUnit.SECONDS);
        return userInfoDto;
    }
}
