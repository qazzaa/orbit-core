package com.orbit.core.supervisor;


import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.io.IOException;
import java.util.Set;

public interface ResourceMappingMBean {
    public void closeNodes();
    public boolean quickTest();
    public void listenStatus();

    public String getVersion() throws IOException;
    public String getStatus();
    public void setActive();
    public void setInActive();
    public int getNodes();
    public int getErrorNodes();
    public int getCPU();
    public int getRAM();
    public int getAvgFreeCpu();
    public long getAvgFreeMem();
    public int getFreeCPU();
    public int getFreeRAM();
    public String getLastResponding();
    public void resetNodes();

    public String getCpuLoad() throws IOException;
    public String getMemLoad() throws IOException;
    public String getFreeMem() throws IOException;
    public String getTotalMem() throws IOException;
    public String getLoadAvg() throws IOException;
    public int getSysAvailProcessors() throws IOException;
}
