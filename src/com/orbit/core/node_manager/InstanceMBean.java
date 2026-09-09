package com.orbit.core.node_manager;


import java.util.List;

public interface InstanceMBean {
    public void startNode(String cmd, String iName);
    public String getState();
    public List<String> getNodes();
    public int getSize();
    public void stop();
    public String getCpuLoad();
    public String getRamLoad();
    public String getFreeMem();
    public String getTotalMem();
    public String getLoadAvg();
    public int getSysAvailProcessors();
    public String getVersion();
}
