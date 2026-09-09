package com.orbit.core.kernel;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public interface VMMBean extends KernelMBean {
    public int getInputQueueCount();
    public int getReceiveQueueCount();
    public int getFreeQueueCount();
    public int getRunsCount();
    public String listInputQueue() throws InterruptedException;
    public String listReceiveQueue() throws InterruptedException;
    public void rollbackMessage(String GUID) throws InterruptedException ;
    public int messageState(String GUID);

    public void startNode();

    public String getLastResponding();

    public int queryNode(String rName, int priority);

    public void install() throws SQLException;
    public boolean remove();
    public void update();
    public void migrate(int idMaster, int classId, int priority);

    public String getAnnotation();
    public int response() throws ReflectionException, MBeanException, InstanceNotFoundException, IOException, MalformedObjectNameException;
    public int response(int state, int fQueueCnt, int maxMsgCnt);

}
