package com.orbit.core.kernel;

import javax.management.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: ehot
 * Date: 17.02.17
 * Time: 10:11
 * To change this template use File | Settings | File Templates.
 */
public interface KernelMBean {
    public int getMaxRunsCount();
    public void setMaxRunsCount(int count);
    public int getMaxMsgCount();
    public void setMaxMsgCount(int count);
    public int getSysAvailProcessors();
    public long getSysFreeMemory();
    public long getSysMaxMemory();
    public String getConnStr();
    public void setConnStr(String connStr);
    public String getLogPath();
    public void setLogPath(String Path);
    public void setmaxExceptions(int count);
    public int getmaxExceptions();
    public void saveConfiguration();
    public void loadConfiguration();
    public List<String> getExceptions();
    public List<String> getNodes();
    public int getNodesCount();
    public String getNodeClass();
    public void allCommand(String command) throws ReflectionException, MBeanException, InstanceNotFoundException, IOException;

    public void startSupervisor() throws MalformedObjectNameException, NotCompliantMBeanException,
            InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException,
            InterruptedException;
    public void stopSupervisor() throws InterruptedException, MBeanRegistrationException, InstanceNotFoundException;
    public void startManager() throws InterruptedException, MBeanRegistrationException,
            InstanceNotFoundException, InstanceAlreadyExistsException, NotCompliantMBeanException;
    public void stopManager() throws InterruptedException, MBeanRegistrationException, InstanceNotFoundException;
    public void restartSchedule() throws MBeanRegistrationException, InstanceNotFoundException, InstanceAlreadyExistsException, NotCompliantMBeanException;
    public void initVMs() throws SQLException;

    public void initialize() throws MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException;
    public void suspend() throws MBeanRegistrationException, InstanceNotFoundException, InterruptedException;
    public void terminate() throws MBeanRegistrationException, InstanceNotFoundException, InterruptedException;
    public void stopNode() throws IOException, InterruptedException, InstanceNotFoundException, MBeanRegistrationException;
    public void exit();

    public void clrExceptions();
    public void bat(String bat);
    public void resetDS();
    public void closeDS();
    public String getVersion();
    public String getState();
    public int call() throws ReflectionException, MBeanException, InstanceNotFoundException, IOException;
    public int call(int id) throws ReflectionException, MBeanException, InstanceNotFoundException, IOException;
    public void addStaticVM(int id);
}
