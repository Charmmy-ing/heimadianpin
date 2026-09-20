package com.hmdp.utils;

public interface Ilock {
    public boolean tryLock(Long timeout, String name);
    public void unlock();
}
