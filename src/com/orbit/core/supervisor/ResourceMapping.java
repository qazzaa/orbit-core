package com.orbit.core.supervisor;

import com.orbit.core.kernel.Kernel;
import com.orbit.core.kernel.VM;
import com.orbit.core.utils.utils;

import javax.management.*;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;

public class ResourceMapping implements ResourceMappingMBean {
    public Kernel kernel;
    public int status;
    public int id;
    public int core;
    public int memory;
    public int cpu;
    public int used_cpu;
    public int ram;
    public long used_ram;
    public int priority;
    public int nodes;
    public int used_nodes;
    public String name;
    public String ip;
    public String cmd;
    public String login;
    public String password;
    public String guest_user;
    public String guest_password;
    public String api;
    public int nm_port;
    public String nm_cmd;
    public String rd;
    public String clName;
    public List<String> CpuMetric;
    public List<String> MemMetric;
    public int avgFreeCpu = 0;
    public long avgFreeMem = 0;
    public long totalMem = 0;
    public Timestamp lookUpTime = new Timestamp(0);
    public ResourceListener listener = null;

    public Vector<ResourceMapping> resources;

    public ResourceMapping(Kernel knl) {
        kernel = knl;
    }

    public void config() {
        try {
            resources=new Vector<>();
            Statement st = kernel.ds.conn.createStatement();
            ResultSet rs = st.executeQuery("select RESOURCES.*," +
                    " RESOURCES_MAP.* " +
                    " from RESOURCES " +
                    " inner join RESOURCES_MAP " +
                    " on RESOURCES_MAP.RESOURCES = RESOURCES.NAME " +
                    " where RESOURCES_MAP.NETWORK_ID="+kernel.currentVM.vmNetwork +
                    " and RESOURCES.ACTIVE=1" +
                    " order by RESOURCES_MAP.PRIORITY desc");
            while (rs.next()) {
                ResourceMapping resource=new ResourceMapping(kernel);
                resource.status = 0;
                resource.id = rs.getInt("ID");
                resource.priority = rs.getInt("PRIORITY");
                resource.name = rs.getString("RESOURCES");
                resource.core = rs.getInt("CORE");
                resource.memory = rs.getInt("MEMORY");
                resource.cpu = rs.getInt("CPU");
                resource.ram = rs.getInt("RAM");
                resource.ip = rs.getString("IP");
                resource.nodes = rs.getInt("NODES");
                resource.cmd = rs.getString("CMD");
                resource.login = rs.getString("LOGIN");
                resource.password = rs.getString("PASSWORD");
                resource.guest_user = rs.getString("GUEST_USER");
                resource.guest_password = rs.getString("GUEST_PASSWORD");
                resource.api = rs.getString("API");
                resource.nm_port = rs.getInt("NM_PORT");
                resource.nm_cmd = rs.getString("NM_CMD");
                resource.rd = rs.getString("RD");
                resource.clName = rs.getString("CLASS");
                resource.CpuMetric = new ArrayList<>();
                resource.MemMetric = new ArrayList<>();
                ObjectName onResource = new ObjectName(packageName + ":type=resources,name=" + resource.name);
                if (kernel.mBeanServer.isRegistered(onResource))
                    kernel.mBeanServer.unregisterMBean(onResource);
                kernel.mBeanServer.registerMBean(resource, onResource);
                resources.add(resource);
            }
            rs.close();
            st.close();
            synchronized (resources) {
                for (ResourceMapping resource : resources)
                    resource.listenStatus();
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
    }

    @Override
    public void listenStatus() {
        if(listener != null)
            listener.timer.cancel();
        try {
            totalMem = Long.parseLong(getTotalMem());
        } catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
        listener = new ResourceListener(this, kernel);
        listener.timer.schedule(listener, 0, 500);
    }

    @Override
    public String getVersion() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (String) kernel.getAttribute(kernel.onNodeManager, rmi, "Version");
    }

    @Override
    public String getStatus() {
        switch (status) {
            case 0 : return "INACTIVE";
            default : return "ACTIVE";
        }
    }

    @Override
    public void setActive() {
        try {
            for (ResourceMapping resource : kernel.supervisor.resource.resourceMapping.resources) {
                if (resource.name.equals(name))
                    if (resource.status == 0)
                        resource.status = 1;
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
    }

    @Override
    public void setInActive() {
        try {
            for (ResourceMapping resource : kernel.supervisor.resource.resourceMapping.resources) {
                if (resource.name.equals(name))
                    if (resource.status == 1)
                        resource.status = 0;
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
    }

    @Override
    public String getLastResponding() {
        return utils.set_date(lookUpTime);
    }

    @Override
    public void closeNodes() {
        List<VM> vms = new ArrayList<>();
        try {
            String str = "select ID, IP, PORT, PRIORITY, VM_NAME, NETWORK_ID, CLASS_ID, VM_TYPE, " +
                    "CORE, MEMORY, STATE, NAME " +
                    "from ACTIVE_VM where NAME = '" + name + "'" +
                    " order by PRIORITY desc";
            Statement st = kernel.ds.conn.createStatement();
            ResultSet rs = st.executeQuery(str);
            while (rs.next()) {
                VM vm = new VM(kernel);
                vm.idClass = rs.getInt("CLASS_ID");
                vm.vmNetwork = rs.getInt("NETWORK_ID");
                vm.id = rs.getInt("ID");
                vm.ipAdr = rs.getString("IP");
                vm.port = rs.getInt("PORT");
                vm.vmType = rs.getString("VM_TYPE");
                vm.vmPriority = rs.getInt("PRIORITY");
                vm.vmName = rs.getString("VM_NAME");
                vm.core = rs.getInt("CORE");
                vm.memory = rs.getInt("MEMORY");
                vm.setHealth(VM.iHealth(rs.getString("STATE")));
                vm.vmResource = rs.getString("NAME");
                vm.rmi = "service:jmx:rmi:///jndi/rmi://" + vm.ipAdr + ":" + vm.port + "/jmxrmi";
                vm.lookUpTime = new Timestamp(System.currentTimeMillis());
                vms.add(vm);
            }
            rs.close();
            st.close();
            for (VM vm : vms) 
                vm.terminate();
        }
        catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
    }

    @Override
    public boolean quickTest() {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        try {
            kernel.getAttribute(kernel.onNodeManager, rmi, "State");
        } catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
            return false;
        }
        return true;
    }

    @Override
    public int getNodes() {
        int cnt=0;
        try {
            String str = "select count(*) from ACTIVE_VM where " +
                    "NAME = '" + name + "' " +
                    "and " +
                    "STATE <> '" + VM.sHealth(VM.SHUTDOWN) + "'";
            Statement st = kernel.ds.conn.createStatement();
            ResultSet rs = st.executeQuery(str);
            while (rs.next())
                cnt = rs.getInt(1);
            rs.close();
            st.close();
        }
        catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
        return cnt;
    }

    @Override
    public int getErrorNodes() {
        int cnt=0;
        try {
            String str = "select count(*) from ACTIVE_VM where " +
                    "NAME = '" + name + "' " +
                    "and " +
                    "STATE in ('" + VM.sHealth(VM.FAILED) + "','" + VM.sHealth(VM.NOTRESPONDING) + "')";
            Statement st = kernel.ds.conn.createStatement();
            ResultSet rs = st.executeQuery(str);
            while (rs.next())
                cnt = rs.getInt(1);
            rs.close();
            st.close();
        }
        catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
        return cnt;
    }

    @Override
    public void resetNodes() {
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs) {
                if(vm.vmResource.equals(name)) {
                    vm.setHealthRemote(vm.vmHealth());
                    if (vm.vmHealth() == VM.SUSPENDING)
                        vm.stopNode();
                    if (vm.vmHealth() == VM.FAILED
                            || vm.vmHealth() == VM.NOTRESPONDING) {
                        vm.setHealth(VM.WARNING);
                        try {
                            vm.call(kernel.currentVM.id);
                            vm.listenStatus();
                        } catch (Exception e) {
                            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
                            vm.setHealthRemote(VM.SHUTDOWN);
                            vm.listenStatus();
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getCPU() {
        return cpu;
    }

    @Override
    public int getRAM() {
        return ram;
    }

    @Override
    public int getAvgFreeCpu() {
        return avgFreeCpu;
    }

    @Override
    public long getAvgFreeMem() {
        return avgFreeMem;
    }

    @Override
    public int getFreeCPU() {
        return getCPU()-getNodes();
    }

    @Override
    public int getFreeRAM() {
        if(cpu>0)
            return ram - getNodes()*ram/cpu;
        return ram;
    }

    @Override
    public String getCpuLoad() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (String) kernel.getAttribute(kernel.onNodeManager, rmi, "CpuLoad");
    }

    @Override
    public String getMemLoad() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (String) kernel.getAttribute(kernel.onNodeManager, rmi, "RamLoad");
    }

    @Override
    public String getFreeMem() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (String) kernel.getAttribute(kernel.onNodeManager, rmi, "FreeMem");
    }

    @Override
    public String getTotalMem() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (String) kernel.getAttribute(kernel.onNodeManager, rmi, "TotalMem");
    }

    @Override
    public String getLoadAvg() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (String) kernel.getAttribute(kernel.onNodeManager, rmi, "LoadAvg");
    }

    @Override
    public int getSysAvailProcessors() throws IOException {
        String rmi="service:jmx:rmi:///jndi/rmi://"+ip+":"+nm_port+"/jmxrmi";
        return (int) kernel.getAttribute(kernel.onNodeManager, rmi, "SysAvailProcessors");
    }

    public boolean check() {
        long mTs = new Date().getTime() - lookUpTime.getTime();
        if(mTs>kernel.supervisor.resource.MaxLookUpSec*1000) return false;
        return true;
    }

    public Vector<ResourceMapping> getResByClass(int idCl) {
        Vector<ResourceMapping> resourceListeners = new Vector<>();
        try{
            String cName = kernel.ds.getClassName(idCl);
            synchronized (resources) {
                for (ResourceMapping res : resources)
                    if(res.clName.equals(cName)
                        && res.status>0)
                        resourceListeners.add(res);
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCEMAP : ERROR] " + name, e);
        }
        if(resourceListeners.size()==0) return null;
        return resourceListeners;
    }

    public Comparator<ResourceMapping> free_cpu = new Comparator<ResourceMapping>() {
        @Override
        public int compare(ResourceMapping o1, ResourceMapping o2) {
            if(o1.used_cpu>o2.used_cpu) return -1;
            if(o1.used_cpu==o2.used_cpu) return 0;
            return 1;
        }
    };

    public Comparator<ResourceMapping> avg_load = new Comparator<ResourceMapping>() {
        @Override
        public int compare(ResourceMapping o1, ResourceMapping o2) {
            if(o1.getNodes()<o2.getNodes()) return -1;
            if(o1.getNodes()==o2.getNodes()) return 0;
            return 1;
        }
    };
}
