package com.orbit.core.supervisor;

/**
 * Created with IntelliJ IDEA.
 * User: ehot
 * Date: 17.02.17
 * Time: 10:11
 * To change this template use File | Settings | File Templates.
 */
public interface SupervisorMBean {
    public void setdelaymsec(int delay);
    public int getdelaymsec();
    public void setMaxLookUpMin(int min);
    public int getMaxLookUpMin();
    public void setResourceManagement(boolean mode);
    public boolean getResourceManagement();

    public void resetNodesStates();
    public void resetResources();
}
