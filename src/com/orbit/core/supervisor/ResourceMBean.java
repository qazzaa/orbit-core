package com.orbit.core.supervisor;


import java.util.List;
import java.util.Set;

public interface ResourceMBean {
    public Set<String> getResources();
    public Set<String> getClasses();

    public void releaseConfiguration();
    public void LoadCheck();

    public void stopVM(String Name);
    public void startVM(String Name);

    public void stopNodeManagement();
    public void startNodeManagement();
    public void closeNodeManagers();
    public void openNodeManagers();

    public void setLoadCheckInterval(int interval);
    public int getLoadCheckInterval();
    public void setOnetimeLaunchUnitMax(int size);
    public int getOnetimeLaunchUnitMax();

    public void setStartTimeOut(int interval);
    public int getStartTimeOut();

    public int queryNode(String rName, int priority);
    public boolean getReleaseResources();
    public void setReleaseResources(boolean flag);

    public void setResourceManagementType(int type);
    public int getResourceManagementType();
    public String getResourceManagementTypeValue();

    public int getCpuMultiplier();
    public void setCpuMultiplier(int multiplier);

    public List<String> getAgvLoad();
}
