package com.orbit.core.schedule;

public interface RunTimerTaskMBean {
    public void stop();
    public void run();
    public String getInfo();
    public int getPeriod();
    public String getName();
    public String getState();
    public String getStarted();
}
