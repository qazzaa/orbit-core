package com.orbit.core.kernel;

import com.orbit.core.supervisor.Resource;
import com.orbit.core.utils.ds;
import com.orbit.core.utils.utils;

import javax.management.*;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.function.Predicate;


public class VM implements VMMBean {
    public final static int OFF = -1;
    public final static int RUN = 0;
    public final static int SHUTDOWN  = 1;
    public final static int FAILED  = 2;
    public final static int WARNING  = 3;
    public final static int STARTING  = 4;
    public final static int SUSPENDING  = 5;
    public final static int NOTRESPONDING  = 6;
    public final static String CONTROL  = "CONTROL";
    public final static String NODE  = "NODE";
    public final static String DEFAULT   = "DEFAULT";

    public String getHealth() {
        String health=null;
        switch (Health) {
            case -1 : health="OFF"; break;
            case 0 : health="RUN"; break;
            case 1 : health="SHUTDOWN"; break;
            case 2 : health="FAILED"; break;
            case 3 : health="WARNING"; break;
            case 4 : health="STARTING"; break;
            case 5 : health="SUSPENDING"; break;
            case 6 : health="NOTRESPONDING"; break;
            default : health=null;
        }
        return health;
    }

    public static String sHealth(int _Health) {
        String health=null;
        switch (_Health) {
            case -1 : health="OFF"; break;
            case 0 : health="RUN"; break;
            case 1 : health="SHUTDOWN"; break;
            case 2 : health="FAILED"; break;
            case 3 : health="WARNING"; break;
            case 4 : health="STARTING"; break;
            case 5 : health="SUSPENDING"; break;
            case 6 : health="NOTRESPONDING"; break;
            default : health=null;
        }
        return health;
    }

    public static int iHealth(String _Health) {
        int health;
        switch (_Health) {
            case "OFF" : health=-1; break;
            case "RUN" : health=0; break;
            case "SHUTDOWN" : health=1; break;
            case "FAILED" : health=2; break;
            case "WARNING" : health=3; break;
            case "STARTING" : health=4; break;
            case "SUSPENDING" : health=5; break;
            case "NOTRESPONDING" : health=6; break;
            default : health=-1;
        }
        return health;
    }

    public int id;
    public int idClass;
    public int core;
    public int memory;
    public String vmType = VM.DEFAULT;
    public String vmName;
    public String ipAdr;
    public int port;
    public int vmPriority;
    public String rmi;
    public Vector<VM> VMs = new Vector<>();
    public VM master = null;
    public Timestamp lookUpTime;
    private int Health=VM.OFF;
    public Kernel kernel;
    public int vmNetwork;
    public ObjectName onServer;
    public String vmResource;
    public Resource.Node node;
    public vmStatusListener listener = null;

    public VM(Kernel knl) {
        kernel=knl;
    }

    public VM(String Name, Kernel knl) throws SQLException {
        vmName=Name;
        kernel=knl;
        try {
            String str="select ACTIVE_VM.*, CLASS.THRESHOLD from ACTIVE_VM " +
                    " inner join CLASS " +
                    " on CLASS.ID = ACTIVE_VM.CLASS_ID " +
                    " where VM_NAME='"+vmName+"'";
            Statement st=kernel.ds.conn.createStatement();
            ResultSet rs=st.executeQuery(str);
            boolean flag=false;
            while(rs.next()) {
                id=rs.getInt("ID");
                ipAdr=rs.getString("IP");
                port=rs.getInt("PORT");
                vmPriority=rs.getInt("PRIORITY");
                vmType=rs.getString("VM_TYPE");
                vmNetwork=rs.getInt("NETWORK_ID");
                idClass=rs.getInt("CLASS_ID");
                core=rs.getInt("CORE");
                memory=rs.getInt("MEMORY");
                vmResource=rs.getString("NAME");
                if(rs.getInt("THRESHOLD")!=0)
                    kernel.MsgMultiplier=rs.getInt("THRESHOLD");
                flag=true;
                break;
            }
            rs.close();
            st.close();
            if(!flag) {
                System.out.println(vmName+" not configured in DB");
                kernel.writeLog("[VM : ERROR] "+vmName+" not configured in DB");
                return;
            }
            str="select * from ACTIVE_VM where VM_TYPE='"+VM.CONTROL+"' " +
                    "and STATE='"+VM.sHealth(VM.RUN)+"' "+
                    "and NETWORK_ID="+vmNetwork+" " +
                    "and VM_NAME<>'"+vmName+"' "+
                    "order by PRIORITY desc";
            st=kernel.ds.conn.createStatement();
            rs=st.executeQuery(str);
            while(rs.next()) {
                master = new VM(kernel);
                master.idClass=rs.getInt("CLASS_ID");
                master.vmNetwork=rs.getInt("NETWORK_ID");
                master.id=rs.getInt("ID");
                master.ipAdr=rs.getString("IP");
                master.port=rs.getInt("PORT");
                master.vmType=rs.getString("VM_TYPE");
                master.vmPriority=rs.getInt("PRIORITY");
                master.vmName=rs.getString("VM_NAME");
                master.core=rs.getInt("CORE");
                master.memory=rs.getInt("MEMORY");
                master.Health=iHealth(rs.getString("STATE"));
                master.vmResource=rs.getString("NAME");
                master.rmi="service:jmx:rmi:///jndi/rmi://"+master.ipAdr+":"+master.port+"/jmxrmi";
                master.lookUpTime=new Timestamp(System.currentTimeMillis());
                break;
            }
            rs.close();
            st.close();
            if(isControl()) {
                if(master != null) {
                    try {
                        if(master.call() != VM.SHUTDOWN
                                || master.call() != VM.OFF) {
                            kernel.writeLog("[KERNEL : DEBUG] " + "VM master " + master.vmName + " already running");
                            master.terminate();
                            master.exit();
                        }
                    } catch (Exception e) {
                        kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
                    }
                    finally {
                        master.setHealthRemote(VM.SHUTDOWN);
                        kernel.writeLog("[KERNEL : DEBUG] " + "Stop master " + master.vmName + " complete");
                    }
                }
            }
            lookUpTime=new Timestamp(System.currentTimeMillis());
            rmi="service:jmx:rmi:///jndi/rmi://"+ipAdr+":"+port+"/jmxrmi";
            register();
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void listenStatus() {
        if(listener != null)
            listener.timer.cancel();
        listener = new vmStatusListener(this, kernel);
        listener.timer.schedule(listener, 0, kernel.DELAY/2);
    }

    public boolean vmState() {
        if(Health == VM.RUN
                || Health == VM.STARTING
                || Health == VM.SUSPENDING)
            return true;
        return false;
    }

    @Override
    public int getMaxRunsCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onKernel, rmi, "MaxRunsCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public void setMaxRunsCount(int count) {
        try {
            if(vmState())
                kernel.setAttribute(kernel.onKernel, rmi, "MaxRunsCount", count);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public int getMaxMsgCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onKernel, rmi, "MaxMsgCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public void setMaxMsgCount(int count) {
        try {
            if(vmState())
                kernel.setAttribute(kernel.onKernel, rmi, "MaxMsgCount", count);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public int getSysAvailProcessors() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onKernel, rmi, "SysAvailProcessors");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public long getSysFreeMemory() {
        try {
            if(vmState())
                return (long) kernel.getAttribute(kernel.onKernel, rmi, "SysFreeMemory");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public long getSysMaxMemory() {
        try {
            if(vmState())
                return (long) kernel.getAttribute(kernel.onKernel, rmi, "SysMaxMemory");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public String getConnStr() {
        try {
            if(vmState())
                return (String) kernel.getAttribute(kernel.onKernel, rmi, "ConnStr");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public void setConnStr(String connStr) {
        try {
            if(vmState())
                kernel.setAttribute(kernel.onKernel, rmi, "ConnStr", connStr);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public String getLogPath() {
        try {
            if(vmState())
                return (String) kernel.getAttribute(kernel.onKernel, rmi, "LogPath");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public void setLogPath(String Path) {
        try {
            if(vmState())
                kernel.setAttribute(kernel.onKernel, rmi, "LogPath", Path);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void setmaxExceptions(int count) {
        try {
            if(vmState())
                kernel.setAttribute(kernel.onKernel, rmi, "maxExceptions", count);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public int getmaxExceptions() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onKernel, rmi, "maxExceptions");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public void saveConfiguration() {
        try {
            if(vmState())
                kernel.invoke(kernel.onKernel, rmi, "saveConfiguration", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void loadConfiguration() {
        try {
            if(vmState())
                kernel.invoke(kernel.onKernel, rmi, "loadConfiguration", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public List<String> getExceptions() {
        try {
            if(vmState())
                return (List<String>) kernel.getAttribute(kernel.onKernel, rmi, "Exceptions");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public List<String> getNodes() {
        try {
            if(vmState())
                return (List<String>) kernel.getAttribute(kernel.onKernel, rmi, "Nodes");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public int getNodesCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onKernel, rmi, "NodesCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return 0;
    }

    @Override
    public void startSupervisor() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "startSupervisor", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void stopSupervisor() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "stopSupervisor", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void startManager() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "startManager", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void stopManager() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "stopManager", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void restartSchedule() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "restartSchedule", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void initVMs() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "initVMs", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    public void getVMs() throws SQLException {
        synchronized (VMs) {
            VMs = new Vector<>();
        }
        master = null;
        String str="select ID, IP, PORT, PRIORITY, VM_NAME, NETWORK_ID, CLASS_ID, VM_TYPE, " +
                "CORE, MEMORY, STATE, NAME " +
                "from ACTIVE_VM where NETWORK_ID=" + vmNetwork +
                " and STATE not in ('"+VM.sHealth(VM.OFF)+"') and ID<>"+id+
                " order by PRIORITY desc";
        Statement st=kernel.ds.conn.createStatement();
        ResultSet rs=st.executeQuery(str);
        while(rs.next()) {
            VM vm=new VM(kernel);
            vm.idClass=rs.getInt("CLASS_ID");
            vm.vmNetwork=rs.getInt("NETWORK_ID");
            vm.id=rs.getInt("ID");
            vm.ipAdr=rs.getString("IP");
            vm.port=rs.getInt("PORT");
            vm.vmType=rs.getString("VM_TYPE");
            vm.vmPriority=rs.getInt("PRIORITY");
            vm.vmName=rs.getString("VM_NAME");
            vm.core=rs.getInt("CORE");
            vm.memory=rs.getInt("MEMORY");
            vm.Health=iHealth(rs.getString("STATE"));
            vm.vmResource=rs.getString("NAME");
            vm.rmi="service:jmx:rmi:///jndi/rmi://"+vm.ipAdr+":"+vm.port+"/jmxrmi";
            vm.lookUpTime=new Timestamp(System.currentTimeMillis());
            if (vm.isControl())
                master = vm;
            synchronized (VMs) {
                VMs.add(vm);
            }
        }
        rs.close();
        st.close();

        if(master==null && !isControl()) {
            master=new VM(kernel);
            master.idClass=0;
            master.id=0;
            master.ipAdr="";
            master.port=0;
            master.vmType=VM.CONTROL;
            master.vmPriority=0;
            master.vmName="Noname";
            master.rmi="";
        }

        if(isControl())
            synchronized (VMs) {
                for (VM vm : VMs) {
                    vm.register();
                    vm.listenStatus();
                    try {
                        if (vm.vmHealth() != VM.SHUTDOWN
                                && vm.vmHealth() != VM.SUSPENDING)
                            if (!vm.getVersion().equals(kernel.getVersion()))
                                vm.stopNode();
                    } catch (Exception e) {
                        kernel.writeLog("[KERNEL : ERROR]", e);
                    }
                }
            }
    }

    public VM getVM(int idVm) throws SQLException {
        VM vm = null;
        String str = "select * from ACTIVE_VM where ID=" + idVm;
        Statement st = kernel.ds.conn.createStatement();
        ResultSet rs = st.executeQuery(str);
        while (rs.next()) {
            vm = new VM(kernel);
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
            vm.Health = iHealth(rs.getString("STATE"));
            vm.vmResource = rs.getString("NAME");
            vm.rmi = "service:jmx:rmi:///jndi/rmi://" + vm.ipAdr + ":" + vm.port + "/jmxrmi";
            vm.lookUpTime = new Timestamp(System.currentTimeMillis());
        }
        rs.close();
        st.close();

        return vm;
    }

    public void register() {
        try {
            unregister();
            onServer = new ObjectName(Kernel.packageName + ":type=nodes,resource=" + vmResource + ",name=" + vmName.toLowerCase());
            if (kernel.mBeanServer.isRegistered(onServer))
                kernel.mBeanServer.unregisterMBean(onServer);
            kernel.mBeanServer.registerMBean(this, onServer);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR]", e);
        }
    }

    public void unregister() {
        try {
            onServer = new ObjectName(Kernel.packageName + ":type=nodes,resource=" + vmResource + ",name=" + vmName.toLowerCase());
            if (kernel.mBeanServer.isRegistered(onServer))
                kernel.mBeanServer.unregisterMBean(onServer);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR]", e);
        }
    }

    @Override
    public void terminate() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "terminate", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void suspend() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "suspend", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void initialize() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "initialize", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void bat(String bat) {
        try {
            kernel.invoke(kernel.onKernel, rmi, "bat", new Object[]{bat}, new String[]{"java.lang.String"});
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void stopNode() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "stopNode", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void exit() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "exit", null, null);
        } catch (Exception e) {
//            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void clrExceptions() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "clrExceptions", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void resetDS() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "resetDS", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void closeDS() {
        try {
            kernel.invoke(kernel.onKernel, rmi, "closeDS", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public String getVersion() {
        try {
            if(vmState())
                return (String) kernel.getAttribute(kernel.onKernel, rmi, "Version");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public String getState() {
        return getHealth();
    }

    @Override
    public int response() throws ReflectionException, MBeanException,
            InstanceNotFoundException, IOException, MalformedObjectNameException {
        try {
            onServer = new ObjectName(Kernel.packageName + ":type=nodes,resource=" + kernel.currentVM.vmResource +
                    ",name=" + kernel.currentVM.vmName.toLowerCase());
            return (int) kernel.invoke(onServer, rmi, "response",
                    new Object[]{kernel.currentVM.Health,kernel.manager.getFreeQueueCount(),kernel.getMaxMsgCount()},
                    new String[]{"int","int","int"});
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return VM.NOTRESPONDING;
    }

    @Override
    public int response(int state, int fQueueCnt, int maxMsgCnt) {
        setHealthLocal(state);
        free_queue_cnt=fQueueCnt;
        max_msg_cnt=maxMsgCnt;
        return kernel.currentVM.vmHealth();
    }

    @Override
    public int call() throws ReflectionException, MBeanException, InstanceNotFoundException, IOException {
        return (int) kernel.invoke(kernel.onKernel, rmi, "call",  null, null);
    }

    @Override
    public int call(int id) throws ReflectionException, MBeanException, InstanceNotFoundException, IOException {
        return (int) kernel.invoke(kernel.onKernel, rmi, "call", new Object[]{id}, new String[]{"int"});
    }

    @Override
    public void addStaticVM(int idVm) {
        try {
            kernel.invoke(kernel.onKernel, rmi, "addStaticVM", new Object[]{idVm}, new String[]{"int"});
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    public void update_state(ds ds) throws SQLException {
        Statement st=ds.conn.createStatement();
        st.executeUpdate("update ACTIVE_VM set STATE='" + getHealth() + "' where ID=" + id);
        st.close();
    }

    public static void update_state(ds ds, String vmName, String state) throws SQLException {
        Statement st=ds.conn.createStatement();
        st.executeUpdate("update ACTIVE_VM set STATE='" + state + "' where VM_NAME='" + vmName+"'");
        st.close();
    }

    public void update_type() throws SQLException {
        Statement st=kernel.ds.conn.createStatement();
        st.executeUpdate("update ACTIVE_VM set VM_TYPE='" + vmType + "' where ID=" + id);
        st.close();
    }

    public void getVMType() throws SQLException {
        boolean flag=false;
        if(kernel.selectVmTypeMode == 1) {
            synchronized (VMs) {
                for (VM vm : VMs) if (vm.isControl()) flag = true;
            }
            if (flag) {
                vmType = VM.NODE;
            } else if (isControl())
                vmType = VM.CONTROL;
            else {
                flag = false;
                synchronized (VMs) {
                    for (VM vm : VMs) if (vm.vmPriority < vmPriority) flag = true;
                }
                if (!flag)
                    vmType = VM.CONTROL;
                else
                    vmType = VM.NODE;
            }
            update_type();
        }
        if (isControl())
            synchronized (VMs) {
                VMs = new Vector<>();
            }
    }

    public boolean isControl() {
        return vmType.equalsIgnoreCase(VM.CONTROL);
    }

    public void setHealthRemote(int health) {
        setHealthLocal(health);
        try {
            if(!kernel.ds.conn.isClosed())
                update_state(kernel.ds);
        } catch (Exception e) {
            kernel.writeLog("[VM : ERROR] ", e);
        }
    }

    public void setHealthLocal(int health) {
        Timestamp curTimestamp=new Timestamp(System.currentTimeMillis());
        lookUpTime=curTimestamp;
        Health=health;
    }

    public void setHealth(int health) {
        Health=health;
    }

    public int vmHealth() {
        return Health;
    }

    public boolean healthCheck(int MaxLookUpMin) {
        long mTs = new Date().getTime() - lookUpTime.getTime();
        if(mTs>MaxLookUpMin*60*1000) return false;
        return true;
    }

    public void Rollback(ds ds) throws SQLException {
        Statement st=ds.conn.createStatement();
        st.executeUpdate("update QUEUE set STATE=1 where MACHINE_ID=" + id);
        st.close();
    }

    @Override
    public int getInputQueueCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onManager, rmi, "InputQueueCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public int getReceiveQueueCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onManager, rmi, "ReceiveQueueCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public int getFreeQueueCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onManager, rmi, "FreeQueueCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public int getRunsCount() {
        try {
            if(vmState())
                return (int) kernel.getAttribute(kernel.onManager, rmi, "RunsCount");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public String listInputQueue() throws InterruptedException {
        try {
            if(vmState())
                return (String) kernel.invoke(kernel.onManager, rmi, "listInputQueue", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public String listReceiveQueue() throws InterruptedException {
        try {
            if(vmState())
                return (String) kernel.invoke(kernel.onManager, rmi, "listReceiveQueue", null, null);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public void rollbackMessage(String GUID) throws InterruptedException {
        try {
            kernel.invoke(kernel.onManager, rmi, "rollbackMessage", new Object[]{GUID}, new String[]{"java.lang.String"});
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public int messageState(String GUID) {
        try {
            return (int)kernel.invoke(kernel.onManager, rmi, "messageState", new Object[]{GUID}, new String[]{"java.lang.String"});
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return 1;
    }

    @Override
    public String getNodeClass() {
        try {
            if(vmState())
                return (String) kernel.getAttribute(kernel.onKernel, rmi, "NodeClass");
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return null;
    }

    @Override
    public void allCommand(String command) throws ReflectionException, MBeanException, InstanceNotFoundException, IOException {
        synchronized (VMs) {
            for (VM vm : VMs)
                kernel.invoke(vm.kernel.onKernel, vm.rmi, command, null, null);
        }
    }

    @Override
    public void startNode() {
        try {
            kernel.writeLog("[" + kernel.currentVM.vmName + " : KERNEL : REMOTE START] " + "Turn on message pocessing on " + vmName);
            kernel.supervisor.resource.startVM(vmName);
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public String getLastResponding() {
        return utils.set_date(lookUpTime);
    }

    @Override
    public int queryNode(String rName, int priority) {
        try {
            return (int) kernel.invoke(kernel.onResourcePool, rmi, "queryNode",
                    new Object[]{rName,priority},
                    new String[]{"java.lang.String","int"});
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
        return -1;
    }

    @Override
    public void install() throws SQLException {
        Statement st = kernel.ds.conn.createStatement();
        String str = "insert into ACTIVE_VM(ID,CLASS_ID,VM_NAME,IP,PORT,PRIORITY,CORE,MEMORY," +
                "STATE,VM_TYPE,NETWORK_ID,NAME) " +
                "values (" +
                id + "," +
                idClass + ",'" +
                vmName + "','" +
                ipAdr + "'," +
                port + "," +
                vmPriority + "," +
                core + "," +
                memory + ",'" +
                sHealth(VM.WARNING) + "','" +
                VM.NODE + "'," +
                vmNetwork + ",'" +
                vmResource + "'" +
                ")";
        try {
            st.executeUpdate(str);
            register();
        } catch (SQLException e) {
            kernel.writeLog("[KERNEL : WARNING] " + vmName + " Sql:" + str);
            throw e;
        } finally {
            st.close();
        }
    }

    @Override
    public boolean remove() {
        try {
            String str = "delete from ACTIVE_VM where LOCKED=0 and ID=" + id;
            Statement st = kernel.ds.conn.createStatement();
            st.executeUpdate(str);
            st.close();
            unregister();
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
            return false;
        }
        return true;
    }

    @Override
    public void update() {
        try {
            String str = "update ACTIVE_VM set " +
                    "   CLASS_ID =" + idClass +
                    ",  VM_NAME ='" + vmName +
                    "', IP ='" + ipAdr +
                    "', PORT =" + port +
                    ",  PRIORITY =" + vmPriority +
                    ",  CORE =" + core +
                    ",  MEMORY =" + memory +
                    ",  STATE ='" + getHealth() +
                    "', VM_TYPE ='" + vmType +
                    "', NETWORK_ID =" + vmNetwork +
                    ",  NAME ='" + vmResource +"'" +
                    " where LOCKED=0 and ID=" + id;
            Statement st = kernel.ds.conn.createStatement();
            st.executeUpdate(str);
            st.close();
        } catch (Exception e) {
            kernel.writeLog("[KERNEL : ERROR] " + vmName, e);
        }
    }

    @Override
    public void migrate(int idMaster, int classId, int priority) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VM vmMaster=getVM(idMaster);
                    if(vmMaster!=null) {
                        kernel.writeLog("[KERNEL : INFO] " + "VM "+vmName+" migrate to "+vmMaster.vmName);
                        listener.timer.cancel();
                        suspend();
                        terminate();
                        unregister();
                        vmNetwork = vmMaster.vmNetwork;
                        idClass = classId;
                        vmPriority = priority;
                        update();
                        initialize();
                        vmMaster.addStaticVM(id);
                        kernel.writeLog("[KERNEL : INFO] " + "VM "+vmName+" migrate to "+vmMaster.vmName+" complete");
                        setHealth(VM.SHUTDOWN);
                    }
                } catch (Exception e) {
                    kernel.writeLog("[KERNEL : ERROR] ", e);
                }
            }
        }).start();
    }

    @Override
    public String getAnnotation() {
        return vmName+":"+vmResource;
    }

    public static Predicate<? super VM> shutdown=new Predicate<VM>() {
        @Override
        public boolean test(VM vm) {
            if(vm.listener!=null)
                if(vm.vmHealth()==VM.SHUTDOWN
                        && vm.listener.SHUTDOWN)
                    return true;
            if(vm.listener==null)
                if(vm.vmHealth()==VM.SHUTDOWN)
                    return true;
            return false;
        }
    };
}
