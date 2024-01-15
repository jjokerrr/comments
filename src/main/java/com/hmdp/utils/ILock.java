package com.hmdp.utils;

public interface ILock {
    boolean tryLock(String key,Long TTL);
    boolean unLock(String Key);
}
