package com.hmdp.utils;

import org.springframework.context.annotation.PropertySource;

public class SystemConstants {
    // 如果想将这一部分修改成直接加载配置类的配置信息，需要将该类转换为Spring的Bean对象，因为赋值操作是在Bean的初始化过程种完成的
    public static final String IMAGE_UPLOAD_DIR = "E:\\Study\\java\\javaProject\\hm-dianping\\hm-dianping\\nginx-1.18.0\\html\\hmdp\\imgs";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;

}
