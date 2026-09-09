package com.orbit.core.schedule;

public interface RunTimerMBean {
    public void startByName(String Name);
    public void stopScheduledObjects();
    public void startScheduledObjects();
}
