package com.orbit.core.supervisor;

import com.orbit.core.kernel.Kernel;
import com.orbit.core.kernel.VM;
import com.orbit.core.manager.Message;

import javax.management.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class Resource extends TimerTask implements ResourceMBean {
    public int MaxTimeOutMin = 10;
    public int MaxLookUpSec = 5;
    public int CpuMultiplier = 1;
    public int OnetimeLaunchUnitMax = 25;
    public Map<Integer,Threshold> classes;
    public Kernel kernel;
    public Timer timer;
    ResourceMapping resourceMapping = null;
    public boolean ReleaseResources = true;

    public enum  Type {
        QUANTITY_BIND,
        PERCENTAGE_BIND
    }

    public Type type = Type.QUANTITY_BIND;

    public class Threshold {
        int VmThreshold;
        int MsgThreshold;
        int MaxVmThreshold;
        String Name;
    }

    public class Node {
        ResourceMapping resource;
        int core;
        int memory;
        int port;
        String name;
        String type;
        int network;
        int idClass;
        String rmi;
    }

    public void start() {
        if(resourceMapping == null)
            resourceMapping=new ResourceMapping(kernel);
        releaseConfiguration();
        timer = new Timer("Resource", false);
        timer.schedule(this, 0, MaxLookUpSec*1000);
    }

    public void stop() {
        timer.cancel();
    }

    @Override
    public void run() {
        kernel.writeLog("[RESOURCE : DEBUG] " + "Resource manager health normal");
        if(kernel.supervisor.resourceManagement)
            try {
                LoadCheck();
            } catch (Exception e) {
                kernel.writeLog("[RESOURCE : ERROR]", e);
            }
    }

    public Resource(Kernel knl) {
        kernel=knl;
        classes = new HashMap<>();
        timer = new Timer("Resource", false);
        if(kernel.ini.value("default") != null)
            setReleaseResources(Boolean.parseBoolean(kernel.ini.value("default").getOrDefault("supervisor.resource.ReleaseResources", String.valueOf(ReleaseResources))));
    }

    @Override
    public Set<String> getResources() {
        Set<String> res = new HashSet<>();
        synchronized (resourceMapping.resources) {
            for (ResourceMapping  resource : resourceMapping.resources)
                if(!res.contains(resource.name))
                    res.add(resource.name);
        }
        if(res.size()==0)
            res=null;
        return res;
    }

    @Override
    public Set<String> getClasses() {
        Set<String> res = new HashSet<>();
        synchronized (classes) {
            for (Integer clID : classes.keySet())
                if(!res.contains(classes.get(clID).Name))
                    res.add(classes.get(clID).Name);
        }
        if(res.size()==0)
            res=null;
        return res;
    }

    @Override
    public void releaseConfiguration() {
        Statement st = null;
        ResultSet rs = null;
        try {
            resourceMapping.config();
            synchronized (classes) {
                classes.clear();
                try {
                    st = kernel.ds.conn.createStatement();
                    rs = st.executeQuery("select CLASS.NAME," +
                            "  CLASS.THRESHOLD," +
                            "  CLASS.VM_THRESHOLD," +
                            "  CLASS.VM_MAX," +
                            "  CLASS.ID" +
                            " from CLASS " +
                            " inner join RESOURCES_MAP " +
                            " on CLASS.NAME = RESOURCES_MAP.CLASS " +
                            " where RESOURCES_MAP.NETWORK_ID = " + kernel.currentVM.vmNetwork +
                            " group by CLASS.NAME, " +
                            "  CLASS.THRESHOLD, " +
                            "  CLASS.VM_THRESHOLD, " +
                            "  CLASS.VM_MAX," +
                            "  CLASS.ID");
                    while (rs.next()) {
                        Threshold thr=new Threshold();
                        thr.MaxVmThreshold=rs.getInt("VM_MAX");
                        thr.VmThreshold=rs.getInt("VM_THRESHOLD");
                        thr.MsgThreshold=rs.getInt("THRESHOLD");
                        thr.Name=rs.getString("NAME");
                        classes.put(rs.getInt("ID"), thr);
                    }
                } catch (SQLException e) {
                    kernel.writeLog("[RESOURCE : ERROR] ", e);
                }
            }
        } catch (ConcurrentModificationException e) {
            kernel.writeLog("[RESOURCE : WARNING] " + "Concurrent modification", e);
        }
        finally {
            closeStatement(st, rs);
        }
    }

    @Override
    public void LoadCheck() {
        try {
            synchronized (classes) {
                for (Integer clID : classes.keySet()) {
                    int num = getThreshold(clID);
                    if(num > OnetimeLaunchUnitMax)
                        num = OnetimeLaunchUnitMax;
                    if(num != 0)
                        kernel.writeLog("[RESOURCE : DEBUG] " + "RESOURCE THRESHOLD=" + num + " FOR CLASS=" + kernel.ds.getClassName(clID));
                    if (num < 0)
                        while (num < 0) {
                            VM vm = getVMToStop(clID);
                            if (vm != null) {
                                vm.setHealth(VM.WARNING);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            stopVM(vm);
                                        } catch (Exception e) {
                                            kernel.writeLog("[RESOURCE : ERROR] ", e);
                                        }
                                    }
                                }).start();
                            } else
                                break;
                            num++;
                        }
                    if (num > 0)
                        while (num > 0) {
                            final VM fvm = getVMToStart(clID);
                            if (fvm != null) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            VM vm = fvm;
                                            startVM(vm);
                                            int sec = 0;
                                            while (vm.vmHealth() != VM.RUN) {
                                                Thread.sleep(1000);
                                                sec += 1;
                                                if (sec > kernel.supervisor.MaxLookUpMin * 60) {
                                                    vm.setHealthRemote(VM.NOTRESPONDING);
                                                    kernel.writeLog("[RESOURCE : WARNING] " + vm.vmName + " not responding");
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            kernel.writeLog("[RESOURCE : ERROR] ", e);
                                        }
                                    }
                                }).start();
                            } else
                                break;
                            num--;
                        }
                }
            }
        }catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
    }

    public int getThreshold(int classId) {
        int msgCount=0;
        int vmCount=0;
        Statement st = null;
        ResultSet rs = null;
        try {
            st = kernel.ds.conn.createStatement();
            rs = st.executeQuery("select count(*) from QUEUE where CLASS_ID="+classId+" " +
                    " and STATE<>" + Message.FAILED +
                    " and STATE<>" + Message.ROLLBACK);
            while (rs.next()) {
                msgCount=rs.getInt(1);
                break;
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        finally {
            closeStatement(st, rs);
        }
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs)
                if (vm.idClass == classId
                        && (vm.vmHealth() == VM.RUN
                        || vm.vmHealth() == VM.STARTING
                        || vm.vmHealth() == VM.SUSPENDING)) vmCount++;
        }
        if(kernel.currentVM.idClass==classId
                && kernel.currentVM.vmHealth()==VM.RUN) vmCount++;

        if(vmCount < classes.get(classId).VmThreshold)
            return (classes.get(classId).VmThreshold - vmCount);
        if(msgCount==0 && classes.get(classId).VmThreshold < vmCount)
            return (classes.get(classId).VmThreshold - vmCount);
        if(classes.get(classId).MsgThreshold==0)
            return msgCount-vmCount;
        if(vmCount==0 && msgCount>0
                && classes.get(classId).MsgThreshold>0)
            return (int) Math.ceil((double) msgCount/classes.get(classId).MsgThreshold);
//        if(vmCount==0 && msgCount>0
//                && classes.get(classId).MsgThreshold==0)
//            return 1;
        int cnt = 0;
        if(vmCount>0 && classes.get(classId).MsgThreshold>0) {
            if (msgCount / vmCount > classes.get(classId).MsgThreshold)
                cnt = (int) (Math.ceil((double) msgCount/classes.get(classId).MsgThreshold) - vmCount);
            if (msgCount < classes.get(classId).MsgThreshold * vmCount
                    && vmCount > classes.get(classId).VmThreshold)
                cnt = (int) (Math.ceil((double) msgCount/classes.get(classId).MsgThreshold) - vmCount);
        }
        if(cnt!=0) {
            if (classes.get(classId).MaxVmThreshold > 0)
                if ((vmCount+cnt) > classes.get(classId).MaxVmThreshold)
                    cnt = (vmCount + cnt) - classes.get(classId).MaxVmThreshold;
            if (classes.get(classId).VmThreshold > 0) {
                if ((vmCount + cnt) > classes.get(classId).VmThreshold)
                    return cnt;
                else
                    return (vmCount + cnt) - classes.get(classId).VmThreshold;
            } else
                return cnt;
        }
        return 0;
    }

    public VM getVMToStop(int classId) throws AttributeNotFoundException,
            MBeanException, ReflectionException, InstanceNotFoundException, IOException {
        Map<VM,Integer> vmMsgCnt = new HashMap<>();
        VM rVm=null;
        int min=0;
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs)
                if (vm.vmHealth() == VM.RUN && vm.idClass == classId)
                    if (vm.free_queue_cnt == vm.max_msg_cnt) {
                        vmMsgCnt.put(vm, vm.queue.size());
                        min = vm.queue.size();
                        rVm = vm;
                    }
            for(VM vm : vmMsgCnt.keySet())
                if(vmMsgCnt.get(vm)<min) {
                    min=vmMsgCnt.get(vm);
                    rVm=vm;
                }
        }
        if(rVm!=null) return rVm;
        return null;
    }

    public VM getVMToStop(ResourceMapping resourceMapping) throws AttributeNotFoundException,
            MBeanException, ReflectionException, InstanceNotFoundException, IOException {
        Map<VM,Integer> vmMsgCnt = new HashMap<>();
        VM rVm=null;
        int min=0;
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs)
                if (vm.vmHealth() == VM.RUN
                        && vm.vmResource.equals(resourceMapping.name))
                    if (vm.free_queue_cnt == vm.max_msg_cnt) {
                        vmMsgCnt.put(vm, vm.queue.size());
                        min = vm.queue.size();
                        rVm = vm;
                    }
            for(VM vm : vmMsgCnt.keySet())
                if(vmMsgCnt.get(vm)<min) {
                    min=vmMsgCnt.get(vm);
                    rVm=vm;
                }
        }
        if(rVm!=null) return rVm;
        return null;
    }

    public VM getVMToStart(int classId) {
        VM rVM=null;
        try {
            Node node = getNodeToStart(classId);
            if(node!=null) {
                kernel.writeLog("[RESOURCE : DEBUG] " + "Node="+node.name);
                rVM = getVMforNode(node);
                rVM.setHealth(VM.WARNING);
                kernel.writeLog("[RESOURCE : DEBUG] " + "VM="+rVM.vmName);
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        return rVM;
    }

    public VM getVMforNode(Node node) {
        VM rVm = null;
        try {
            rVm = new VM(kernel);
            rVm.node = node;
            rVm.vmName = node.name;
            rVm.vmType = node.type;
            rVm.ipAdr = node.resource.ip;
            rVm.master = kernel.currentVM;
            rVm.memory = node.memory;
            rVm.core = node.core;
            rVm.idClass = node.idClass;
            rVm.port = node.port;
            rVm.vmNetwork = node.network;
            rVm.vmResource = node.resource.name;
            String sNum=String.valueOf(node.resource.id)+"0"+String.valueOf(node.port);
//            int num = (rVm.idClass*1000) + node.port + node.resource.id;
            int num = Integer.parseInt(sNum);
//            synchronized (kernel.currentVM.VMs) {
//                for (int i=0;i<kernel.currentVM.VMs.size();i++)
//                    for (VM vm : kernel.currentVM.VMs)
//                        if (vm.idClass == rVm.idClass)
//                            if (vm.id == num)
//                                num++;
//            }
            rVm.id = num;
            rVm.vmPriority = node.resource.priority;
            rVm.rmi = "service:jmx:rmi:///jndi/rmi://" + rVm.ipAdr + ":" + rVm.port + "/jmxrmi";
            rVm.install();
            synchronized (kernel.currentVM.VMs) {
                kernel.currentVM.VMs.add(rVm);
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] " + rVm.vmName, e);
            rVm = null;
        }
        return  rVm;
    }

    public Node getNodeToStart(int classId) {
        Node rNode=null;
        Statement st = null;
        ResultSet rs = null;
        try {
            Vector<ResourceMapping> resources = resourceMapping.getResByClass(classId);
            if(resources != null) {
                for (ResourceMapping resource : resources) {
                    if (type == Type.QUANTITY_BIND) {
                        resource.used_cpu = resource.getFreeCPU();
                        resource.used_ram = resource.getFreeRAM();
                    }
                    if (type == Type.PERCENTAGE_BIND) {
                        resource.used_cpu = resource.avgFreeCpu;
                        if(CpuMultiplier>0)
                            resource.used_cpu = resource.cpu * CpuMultiplier - resource.getNodes();
                        resource.used_ram = resource.avgFreeMem;
                    }
                }
                ResourceMapping rResource = null;
                synchronized (kernel.currentVM.VMs) {
                    for (ResourceMapping resource : resources) {
                        resource.used_nodes = resource.nodes;
                        if (resource.nodes==-1)
                            resource.used_nodes = resource.cpu * CpuMultiplier;
                        for (VM vm : kernel.currentVM.VMs)
                            if (vm.vmResource.equals(resource.name)
                                    && vm.idClass == classId)
                                resource.used_nodes--;
                    }
                }
                if (type == Type.QUANTITY_BIND)
                    resources.sort(resourceMapping.free_cpu);
                if (type == Type.PERCENTAGE_BIND)
                    resources.sort(resourceMapping.avg_load);
                for (ResourceMapping resource : resources) {
                    if (type == Type.QUANTITY_BIND) {
                        if (resource.used_nodes > 0
                                && resource.used_cpu > 0
                                && resource.used_ram > 0)
                            if (resource.quickTest()) {
                                rResource = resource;
                                break;
                            }
                    }
                    if (type == Type.PERCENTAGE_BIND) {
                        if (resource.used_nodes > 0
                                && resource.used_cpu > 0
                                && resource.avgFreeCpu > 20
                                && resource.avgFreeMem > resource.totalMem / 20)
                            if (resource.quickTest()) {
                                rResource = resource;
                                break;
                            }
                    }
                }
                if (rResource != null) {
                    if (type == Type.QUANTITY_BIND)
                        kernel.writeLog("[RESOURCE : DEBUG] " + rResource.name + " cpu=" + rResource.used_cpu + " nodes=" + rResource.used_nodes);
                    if (type == Type.PERCENTAGE_BIND)
                        kernel.writeLog("[RESOURCE : DEBUG] " + rResource.name + " cpu=" + rResource.avgFreeCpu + "% nodes=" + rResource.used_nodes);
                    st = kernel.ds.conn.createStatement();
                    rs = st.executeQuery("select PORT from ACTIVE_VM where NAME='" + rResource.name + "' order by PORT asc");
                    Vector<Integer> ports = new Vector<>();
                    while (rs.next())
                        ports.add(rs.getInt("PORT"));
                    boolean flag=false;
                    int port = kernel.currentVM.port;
                    while(!flag) {
                        flag = true;
                        for (Integer p : ports)
                            if (port==p)
                                flag = false;
                        if(!flag)
                            port++;
                    }
                    rNode = new Node();
                    rNode.port = port;
                    if (rResource.core > rResource.used_cpu)
                        rNode.core = rResource.used_cpu;
                    else
                        rNode.core = rResource.core;
                    rNode.memory = rResource.memory;
                    rNode.type = VM.NODE;
                    rNode.name = rResource.ip + "-" + rNode.port;
                    rNode.resource = rResource;
                    rNode.network = kernel.currentVM.vmNetwork;
                    rNode.idClass = classId;
                    rNode.rmi = "service:jmx:rmi:///jndi/rmi://" + rResource.ip + ":" + rResource.nm_port + "/jmxrmi";
                } else {
                    kernel.writeLog("[RESOURCE : DEBUG] " + "Not enough cpu or free nodes for " + kernel.ds.getClassName(classId));
                    if(ReleaseResources)
                        resourceRelease(resources, classId);
                }
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        finally {
            closeStatement(st, rs);
        }
        return rNode;
    }

    public void resourceRelease(Vector<ResourceMapping> resources, int classId) {
        Statement st = null;
        ResultSet rs = null;
        try {
            for (ResourceMapping resource : resources) {
                VM vm=queryNodeLocal(resource);
                if(vm!=null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            vm.suspend();
                            vm.idClass = classId;
                            vm.vmPriority = resource.priority;
                            vm.update();
                            vm.initialize();
                        }
                    }).start();
                    return;
                }
            }
            st=kernel.ds.conn.createStatement();
            String str="select * from ACTIVE_VM where VM_TYPE='"+VM.CONTROL+"' " +
                    "and STATE='"+VM.sHealth(VM.RUN)+"' "+
                    "and VM_NAME<>'"+kernel.currentVM.vmName+"' "+
                    "order by PRIORITY desc";
            rs = st.executeQuery(str);
            Vector<Integer> iDs = new Vector<>();
            while (rs.next())
                iDs.add(rs.getInt("ID"));
            for(Integer id : iDs) {
                VM vmMaster=kernel.currentVM.getVM(id);
                if(vmMaster!=null)
                    for (ResourceMapping resource : resources) {
                        int idVm = vmMaster.queryNode(resource.name, resource.priority);
                        if(idVm>-1) {
                            VM vm=kernel.currentVM.getVM(idVm);
                            if(vm!=null) {
                                vm.onServer = new ObjectName(Kernel.packageName + ":type=nodes,resource=" + vm.vmResource +
                                        ",name=" + vm.vmName.toLowerCase());
                                kernel.invoke(vm.onServer, vmMaster.rmi, "migrate",
                                        new Object[]{kernel.currentVM.id,classId,resource.priority},
                                        new String[]{"int","int","int"});
                                return;
                            }
                        }
                    }
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        finally {
            closeStatement(st, rs);
        }
    }

    @Override
    public void startVM(String vmName) {
        Statement st = null;
        ResultSet rs = null;
        try {
            synchronized (kernel.currentVM.VMs) {
                for (VM vm : kernel.currentVM.VMs)
                    if (vm.vmName.equalsIgnoreCase(vmName)) {
                        vm.node = new Node();
                        vm.node.resource = new ResourceMapping(kernel);
                        st = kernel.ds.conn.createStatement();
                        rs = st.executeQuery("select RESOURCES.*," +
                                " RESOURCES_MAP.* " +
                                " from RESOURCES " +
                                " inner join RESOURCES_MAP " +
                                " on RESOURCES_MAP.RESOURCES = RESOURCES.NAME " +
                                " where RESOURCES.NAME='" + vm.vmResource + "' " +
                                " and RESOURCES_MAP.CLASS='" + kernel.ds.getClassName(vm.idClass) + "'");
                        while (rs.next()) {
                            vm.node.resource.name = rs.getString("NAME");
                            vm.node.resource.cpu = rs.getInt("CPU");
                            vm.node.resource.ram = rs.getInt("RAM");
                            vm.node.resource.ip = rs.getString("IP");
                            vm.node.resource.cmd = rs.getString("CMD");
                            vm.node.resource.login = rs.getString("LOGIN");
                            vm.node.resource.password = rs.getString("PASSWORD");
                            vm.node.resource.guest_user = rs.getString("GUEST_USER");
                            vm.node.resource.guest_password = rs.getString("GUEST_PASSWORD");
                            vm.node.resource.api = rs.getString("API");
                            vm.node.resource.nm_port = rs.getInt("NM_PORT");
                            vm.node.resource.nm_cmd = rs.getString("NM_CMD");
                            vm.node.resource.clName = rs.getString("CLASS");
                            vm.node.rmi = "service:jmx:rmi:///jndi/rmi://"+vm.node.resource.ip+":"+vm.node.resource.nm_port+"/jmxrmi";
                        }
                        if(!vm.node.resource.quickTest()) {
                            kernel.writeLog("[RESOURCE : WARNING] " + "Start procedure for "+vm.vmName+" failed. " +
                                    "NM "+vm.node.resource.name+" not ready.");
                            vm.setHealthRemote(VM.SHUTDOWN);
                        } else
                            startVM(vm);
                        break;
                    }
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        finally {
            closeStatement(st, rs);
        }
    }

    public void closeStatement(Statement st, ResultSet rs) {
        try {
            if(rs!=null) rs.close();
            if(st!=null) st.close();
        } catch (SQLException e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
    }

    @Override
    public void stopNodeManagement() {
        kernel.supervisor.resourceManagement = false;
        try {
            Thread.sleep(kernel.DELAY*2);
        } catch (InterruptedException e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs)
                stopVM(vm);
        }
    }

    @Override
    public void startNodeManagement() {
        kernel.supervisor.resourceManagement = true;
    }

    @Override
    public void closeNodeManagers() {
        Statement st = null;
        ResultSet rs = null;
        try {
            List<Node> nodes = new ArrayList<>();
            st = kernel.ds.conn.createStatement();
            rs = st.executeQuery("select RESOURCES.* from RESOURCES ");
            while (rs.next()) {
                Node node = new Node();
                node.resource = new ResourceMapping(kernel);
                node.resource.name = rs.getString("NAME");
                node.resource.cpu = rs.getInt("CPU");
                node.resource.ram = rs.getInt("RAM");
                node.resource.ip = rs.getString("IP");
                node.resource.login = rs.getString("LOGIN");
                node.resource.password = rs.getString("PASSWORD");
                node.resource.guest_user = rs.getString("GUEST_USER");
                node.resource.guest_password = rs.getString("GUEST_PASSWORD");
                node.resource.api = rs.getString("API");
                node.resource.nm_port = rs.getInt("NM_PORT");
                node.resource.nm_cmd = rs.getString("NM_CMD");
                node.rmi = "service:jmx:rmi:///jndi/rmi://" + node.resource.ip + ":" + node.resource.nm_port + "/jmxrmi";
                nodes.add(node);
            }
            for (Node node : nodes)
                try {
                    if(node.resource.listener!=null)
                        node.resource.listener.timer.cancel();
                    kernel.invoke(kernel.onNodeManager, node.rmi, "stop", null, null);
                } catch (Exception e) {
//                    kernel.writeLog("[RESOURCE : ERROR] ", e);
                }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        finally {
            closeStatement(st, rs);
        }
    }

    @Override
    public void openNodeManagers() {
        Statement st = null;
        ResultSet rs = null;
        try {
            List<Node> nodes = new ArrayList<>();
            st = kernel.ds.conn.createStatement();
            rs = st.executeQuery("select RESOURCES.* from RESOURCES ");
            while (rs.next()) {
                Node node = new Node();
                node.resource = new ResourceMapping(kernel);
                node.resource.name = rs.getString("NAME");
                node.resource.cpu = rs.getInt("CPU");
                node.resource.ram = rs.getInt("RAM");
                node.resource.ip = rs.getString("IP");
                node.resource.login = rs.getString("LOGIN");
                node.resource.password = rs.getString("PASSWORD");
                node.resource.guest_user = rs.getString("GUEST_USER");
                node.resource.guest_password = rs.getString("GUEST_PASSWORD");
                node.resource.api = rs.getString("API");
                node.resource.nm_port = rs.getInt("NM_PORT");
                node.resource.nm_cmd = rs.getString("NM_CMD");
                node.rmi = "service:jmx:rmi:///jndi/rmi://" + node.resource.ip + ":" + node.resource.nm_port + "/jmxrmi";
                nodes.add(node);
            }
            for (Node node : nodes)
                try {
                    node.resource.quickTest();
                    node.resource.listenStatus();
                } catch (Exception e) {
//                    kernel.writeLog("[RESOURCE : ERROR] ", e);
                }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        finally {
            closeStatement(st, rs);
        }
    }

    public void startVM(VM vm) {
        if(!kernel.supervisor.resourceManagement) {
            vm.remove();
            return;
        }
        kernel.writeLog("[RESOURCE : INFO] " + "Begin start procedure for " + vm.vmName);
        try {
            kernel.writeLog("[RESOURCE : INFO] " + "Starting "+vm.node.resource.cmd);
//            String args = "-vm="+vm.vmName + " -port=" + vm.port + " -db=\"" + kernel.dsStr + "\"";
            String args = vm.vmName + " " + vm.port + " " + "\"" + kernel.dsStr + "\"";
            kernel.invoke(kernel.onNodeManager, vm.node.rmi,"startNode",
                    new Object[]{vm.node.resource.cmd+" "+args, vm.vmName},
                    new String[]{"java.lang.String","java.lang.String"});
            Thread.sleep(kernel.DELAY);
            vm.listenStatus();
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : WARNING] " + "Start procedure for "+vm.vmName+" failed");
            kernel.writeLog("[RESOURCE : ERROR] ", e);
            vm.setHealthRemote(VM.SHUTDOWN);
            vm.listenStatus();
        }
        kernel.writeLog("[RESOURCE : INFO] " + "Start procedure for " + vm.vmName + " complete");
    }

    @Override
    public void setLoadCheckInterval(int interval) {
        MaxLookUpSec=interval;
    }

    @Override
    public int getLoadCheckInterval() {
        return MaxLookUpSec;
    }

    @Override
    public void setOnetimeLaunchUnitMax(int size) {
        OnetimeLaunchUnitMax = size;
    }

    @Override
    public int getOnetimeLaunchUnitMax() {
        return OnetimeLaunchUnitMax;
    }

    @Override
    public void setStartTimeOut(int interval) {
        MaxTimeOutMin=interval;
    }

    @Override
    public int getStartTimeOut() {
        return MaxTimeOutMin;
    }

    @Override
    public int queryNode(String rName, int priority) {
        synchronized (kernel.currentVM.VMs) {
            for(VM vm : kernel.currentVM.VMs)
                if(vm.vmResource.equals(rName)
                        &&vm.vmHealth()==VM.RUN
                        &&vm.vmPriority<priority) {
                    kernel.writeLog("[RESOURCE : DEBUG] " + "Query node for "+rName+" success, id="+vm.id);
                    return vm.id;
                }
        }
        return -1;
    }

    @Override
    public boolean getReleaseResources() {
        return ReleaseResources;
    }

    @Override
    public void setReleaseResources(boolean flag) {
        ReleaseResources=flag;
    }

    @Override
    public void setResourceManagementType(int type) {
        switch (type) {
            case 0: this.type=Type.PERCENTAGE_BIND; break;
            case 1: this.type=Type.QUANTITY_BIND; break;
        }
    }

    @Override
    public int getResourceManagementType() {
        switch (type) {
            case PERCENTAGE_BIND: return 0;
            case QUANTITY_BIND: return 1;
            default: return 0;
        }
    }

    @Override
    public String getResourceManagementTypeValue() {
        switch (type) {
            case PERCENTAGE_BIND: return "PERCENTAGE_BIND";
            case QUANTITY_BIND: return "QUANTITY_BIND";
            default: return "PERCENTAGE_BIND";
        }
    }

    @Override
    public int getCpuMultiplier() {
        return CpuMultiplier;
    }

    @Override
    public void setCpuMultiplier(int multiplier) {
        CpuMultiplier = multiplier;
    }

    @Override
    public List<String> getAgvLoad() {
        List<String> res = null;
        try {
            Vector<ResourceMapping> resources = resourceMapping.resources;
            if (resources != null) {
                resources.sort(resourceMapping.avg_load);
                res = new ArrayList<>();
                synchronized (resources) {
                    for (ResourceMapping rm : resources) {
                        int memPercent = 0;
                        if (rm.totalMem > 0)
                            memPercent = (int) (100 * rm.avgFreeMem / rm.totalMem);
                        String str = rm.getNodes() + "|" + rm.name + "|" + rm.getStatus() + "|" + rm.avgFreeCpu + "|" + memPercent;
                        boolean flag = false;
                        for (String s : res)
                            if (s.contains(rm.name)) {
                                flag = true;
                                break;
                            }
                        if (!flag)
                            res.add(str);
                    }
                }
            }
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] ", e);
        }
        return res;
    }

    public VM queryNodeLocal(ResourceMapping resource) {
        synchronized (kernel.currentVM.VMs) {
            for(VM vm : kernel.currentVM.VMs)
                if(vm.vmResource.equals(resource.name)
                        &&vm.vmHealth()==VM.RUN
                        &&vm.vmPriority<resource.priority) {
                    kernel.writeLog("[RESOURCE : DEBUG] " + "Query local node for "+resource.name+" success, id="+vm.id);
                    return vm;
                }
        }
        return null;
    }

    @Override
    public void stopVM(String Name) {
        VM vm=null;
        synchronized (kernel.currentVM.VMs) {
            for (VM vmi : kernel.currentVM.VMs)
                if (vmi.vmName.equalsIgnoreCase(Name)) {
                    vm = vmi;
                    break;
                }
        }
        if(vm==null) return;
        stopVM(vm);
    }

    public void stopVM(VM vm) {
        try {
            kernel.writeLog("[RESOURCE : INFO] " + "Begin stop procedure for "+vm.vmName);
            vm.setHealthLocal(VM.SUSPENDING);
            vm.stopNode();
            kernel.writeLog("[RESOURCE : INFO] " + "Stop procedure for "+vm.vmName+" complete");
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE : ERROR] " + vm.vmName, e);
        }
    }
}