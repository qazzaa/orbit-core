package com.orbit.core.manager;

import javax.management.openmbean.OpenDataException;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: ehot
 * Date: 17.02.17
 * Time: 10:11
 * To change this template use File | Settings | File Templates.
 */
public interface ManagerMBean {
    public Map<String, String> callMessage(String GUID);
    public void rollbackMessage(String GUID);
    public int messageState(String GUID);
    public void inputMessage(long reg_num, String input, String operation, int class_id, int priority);
    public String listInputQueue() throws InterruptedException;
    public void classTimeout();
    public int getInputQueueCount();
    public int getFreeQueueCount();
    public void setdelaymsec(int delay);
    public int getdelaymsec();
    public void setXmx(int Xmx);
    public int getXmx();
    public int getRunsCount();
    public int getType();
    public void setType(int t);
    public String getTypeValue();

}
