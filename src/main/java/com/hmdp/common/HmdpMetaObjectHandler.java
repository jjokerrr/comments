package com.hmdp.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class HmdpMetaObjectHandler implements MetaObjectHandler {
    /**
     * MybatisPlus字段自动填充规则
     *
     * @Parameter [metaObject]
     * @Return
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("插入{}的数据", metaObject.getClass().getName());
        metaObject.setValue("createTime", LocalDateTime.now());
        metaObject.setValue("updateTime", LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {

        log.info("插入{}的数据", metaObject.getClass().getName());

        metaObject.setValue("updateTime", LocalDateTime.now());
        metaObject.setValue("updateUser", UserHolder.getUser().getId());

    }
}
