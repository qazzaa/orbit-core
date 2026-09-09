package com.orbit.core.kernel;

import com.orbit.core.manager.Manager;
import com.orbit.core.supervisor.Supervisor;
import com.orbit.core.schedule.RunTimer;
import com.orbit.core.utils.Ini;
import com.orbit.core.utils.ds;
import com.orbit.core.utils.log;
import com.orbit.core.utils.utils;
import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import java.io.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: ehot
 * Date: 17.02.17
 * Time: 10:12
 * To change this template use File | Settings | File Templates.
 */

public class Kernel implements KernelMBean {

    public static final String version="0.0.0.1.b01";
    public static final String packageName = "com.orbit.core";

    public int selectVmTypeMode=0;

    public int DELAY=2000;
    public int maxExceptions = 5000;
    public int MaxRuns = 0;
    private int MaxMsg = 0;
    public int MsgMultiplier = 1;
    public String logPath = "";
    public VM currentVM;
    public Supervisor supervisor = null;
    public Manager manager = null;
    public RunTimer timer = null;
    public com.orbit.core.utils.ds ds = null;
    public log Log;
    public String dsStr = null;
    private Map<String,String> data_sources = null;
    public MBeanServer mBeanServer;
    public JMXConnectorServer jmxConnectorServer;
    public Map<ObjectName, Timestamp> health;
    public List<String> Exceptions;
    public Ini ini = null;
    public boolean exit = false;
    public String Name = null;

    public ObjectName onSupervisor = new ObjectName(Kernel.packageName + ":name=supervisor");
    public ObjectName onKernel = new ObjectName(Kernel.packageName + ":name=kernel");
    public ObjectName onManager = new ObjectName(Kernel.packageName + ":name=manager");
    public ObjectName onTimer = new ObjectName(Kernel.packageName + ":name=schedule");
    public ObjectName onResourcePool = new ObjectName(Kernel.packageName + ":name=resource");
    public static String sNM = Kernel.packageName + ":type=node manager";
    public ObjectName onNodeManager = new ObjectName(sNM);

    public Kernel(String name) throws MalformedObjectNameException {
        Name = name;
    }

    public Kernel(MBeanServer mbs, JMXConnectorServer srv, String name) throws MalformedObjectNameException {
        Exceptions = new ArrayList<>();
        mBeanServer = mbs;
        jmxConnectorServer = srv;
        Name = name;
        setMaxRunsCount();
    }

    public Kernel(MBeanServer mbs, JMXConnectorServer srv, Ini cfg, String name) throws MalformedObjectNameException {
        Exceptions = new ArrayList<>();
        mBeanServer = mbs;
        jmxConnectorServer = srv;
        ini = cfg;
        Name = name;
    }

    @Override
    public int getMaxRunsCount() {
        return MaxRuns;
    }

    @Override
    public void setMaxRunsCount(int count) {
        MaxRuns = count;
    }

    @Override
    public int getMaxMsgCount() {
        return MaxMsg;
    }

    @Override
    public void setMaxMsgCount(int count) {
        MaxMsg = count;
    }

    public void setMaxRunsCount() {
        MaxRuns = getSysAvailProcessors();
    }

    @Override
    public int getSysAvailProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public long getSysFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    @Override
    public long getSysMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @Override
    public String getConnStr() {
        return dsStr;
    }

    @Override
    public void setConnStr(String connStr) {
        dsStr = connStr;
    }

    @Override
    public String getLogPath() {
        return logPath;
    }

    @Override
    public void setLogPath(String Path) {
        logPath = Path;
    }

    @Override
    public void initVMs() {
        try {
            currentVM.getVMs();
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void initialize() {
        try {
            writeLog("[" + currentVM.vmName + " : KERNEL : START] ");
            init();
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void resetDS() {
        try {
            ds.Close();
            writeLog("[KERNEL : START] " + "DS "+data_sources.get("main"));
            dsStr = data_sources.get("main");
            ds.Init(dsStr);
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
            try {
                writeLog("[KERNEL : START] " + "DS "+data_sources.get("standby"));
                dsStr = data_sources.get("standby");
                ds.Init(dsStr);
            } catch (Exception ex) {
                writeLog("[KERNEL : ERROR] ", ex);
                if (ds != null) ds.Close();
                return;
            }
        }
    }

    @Override
    public void closeDS() {
        ds.Close();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getState() {
        return currentVM.getHealth();
    }

//    @Override
//    public void stopNode() {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    suspend();
//                    terminate();
//                } catch (Exception e) {
//                    writeLog("[KERNEL : ERROR] ", e);
//                }
//            }
//        }).start();
//    }

//    @Override
//    public void stopNode() {
//        Thread thr = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    writeLog("[" + currentVM.vmName + " : KERNEL : STOP NODE] ");
//                    currentVM.setHealthRemote(VM.SUSPENDING);
//                    if(!currentVM.isMaster())
//                        if(currentVM.master!=null)
//                            currentVM.master.response();
//                    if (timer != null) timer.stopScheduledObjects();
//                    procFinWait();
//                    writeLog("[" + currentVM.vmName + " : KERNEL : STOP NODE] " + "Turning off...");
//                    if(supervisor != null)
//                        supervisor.resourceManagement = false;
//                    stopManager();
//                    if(supervisor.resource != null)
//                        supervisor.resource.stop();
//                    currentVM.setHealthRemote(VM.SHUTDOWN);
//                    writeLog("[" + currentVM.vmName + " : KERNEL : STOP NODE] " + "Close data source...");
//                    ds.Close();
//                } catch (Exception e) {
//                    writeLog("[KERNEL : ERROR] ", e);
//                }
//            }
//        });
//        thr.setPriority(Thread.MAX_PRIORITY);
//        thr.start();
//    }

    @Override
    public void stopNode() {
        Kernel knl = this;
        Thread thr = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    writeLog("[" + currentVM.vmName + " : KERNEL : STOP NODE] ");
                    currentVM.setHealthRemote(VM.SUSPENDING);
                    if(!currentVM.isControl())
                        if(currentVM.master!=null)
                            currentVM.master.response();
                    if (timer != null) timer.stopScheduledObjects();
                    suspendListener listener = new suspendListener(knl);
                    listener.timer.schedule(listener, 0, 5000);
                } catch (Exception e) {
                    writeLog("[KERNEL : ERROR] ", e);
                }
            }
        });
        thr.setPriority(Thread.MAX_PRIORITY);
        thr.start();
    }

//    public void procFinWait() {
//        try {
//            int sec = 0;
//            while (!manager.FORCE_STOP) {
//                Thread.sleep(5000);
//                writeLog("[" + currentVM.vmName + " : KERNEL : SUSPEND] " + "Suspending...");
//                sec += 5;
//                if (manager.class_timeout != null)
//                    if (manager.class_timeout.getOrDefault(currentVM.idClass, -1) > 0)
//                        if (sec > manager.class_timeout.get(currentVM.idClass)) {
//                            writeLog("[KERNEL : SUSPEND] " + "VM class timeout");
//                            currentVM.setHealthRemote(VM.WARNING);
//                            break;
//                        }
//            }
//        } catch (Exception e) {
//            writeLog("[KERNEL : ERROR] ", e);
//        }
//    }

    @Override
    public void exit() {
        writeLog("[" + currentVM.vmName + " : KERNEL : EXIT] " + "exit");
        stopSupervisor();
        exit = true;
        try {
            jmxConnectorServer.stop();
            System.out.println("JMXConnectorServer stop");
            System.exit(0);
        } catch (IOException e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void clrExceptions() {
        synchronized (Exceptions) {
            Exceptions.clear();
        }
    }

    @Override
    public void terminate() {
        try {
            writeLog("[" + currentVM.vmName + " : KERNEL : STOP] ");
            currentVM.setHealthRemote(VM.SUSPENDING);
            if(supervisor != null)
                supervisor.resourceManagement = false;
            if (timer != null) timer.stopScheduledObjects();
            stopManager();
            if(supervisor.resource != null)
                supervisor.resource.stop();
            currentVM.setHealthRemote(VM.SHUTDOWN);
            ds.Close();
        } catch (Exception e) {
            try {
                writeLog("[KERNEL : ERROR] ", e);
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    PrintStream ps = new PrintStream("./error/" + Name + ".error.txt");
                    ex.printStackTrace(ps);
                    ps.close();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    @Override
    public void  suspend() {
        writeLog("[" + currentVM.vmName + " : KERNEL : SUSPEND] ");
        currentVM.setHealthRemote(VM.SUSPENDING);
        try {
            if (timer != null) timer.stopScheduledObjects();
            if(!currentVM.isControl())
                if(currentVM.master!=null)
                    currentVM.master.response();
            int sec = 0;
            while (!manager.FORCE_STOP) {
                Thread.sleep(5000);
                writeLog("[" + currentVM.vmName + " : KERNEL : SUSPEND] " + "Suspending...");
                sec += 5;
                if (manager.class_timeout != null)
                    if (manager.class_timeout.getOrDefault(currentVM.idClass, -1) > 0)
                        if (sec > manager.class_timeout.get(currentVM.idClass)) {
                            writeLog("[KERNEL : SUSPEND] " + "VM class timeout");
                            currentVM.setHealthRemote(VM.WARNING);
                            break;
                        }
            }
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void startSupervisor() {
        try {
            if (supervisor != null) stopSupervisor();
            if (mBeanServer.isRegistered(onSupervisor))
                mBeanServer.unregisterMBean(onSupervisor);
            supervisor = new Supervisor(this);
            supervisor.start();
            mBeanServer.registerMBean(supervisor, onSupervisor);
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void startManager() {
        try {
            if (manager != null) stopManager();
            if (mBeanServer.isRegistered(onManager))
                mBeanServer.unregisterMBean(onManager);
            manager = new Manager(this);
            manager.start();
            mBeanServer.registerMBean(manager, onManager);
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void stopManager() {
        try {
            if (manager != null)
                manager.stop();
            if (mBeanServer.isRegistered(onManager))
                mBeanServer.unregisterMBean(onManager);
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void restartSchedule() {
        try {
            if(currentVM.isControl()) {
                writeLog("[" + currentVM.vmName + " : KERNEL : DEBUG] " + "Reset schedule");
                if (mBeanServer.isRegistered(onTimer))
                    mBeanServer.unregisterMBean(onTimer);
                if (timer != null)
                    timer.stopScheduledObjects();

                timer = new RunTimer(this);
                mBeanServer.registerMBean(timer, onTimer);
            }
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void stopSupervisor() {
        try {
            if (supervisor != null)
                supervisor.stop();
            if (mBeanServer.isRegistered(onSupervisor))
                mBeanServer.unregisterMBean(onSupervisor);
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    public int init() {
        try {
            if (ini != null) {
                if (ini.value("default") != null)
                    loadConfiguration(ini.value("default"));
                if (ini.value("datasources") != null) {
                    data_sources = new HashMap<>();
                    data_sources.put("main", ini.value("datasources").getOrDefault("main", dsStr));
                    data_sources.put("standby", ini.value("datasources").getOrDefault("standby", dsStr));
                }
            }
            loadConfiguration();
            if (logPath.isEmpty())
                logPath = "./logs";
            File logDir = new File(logPath);
            logDir.mkdirs();
            Log = new log(logDir.getPath());
            writeLog("[KERNEL : START] " + "LOG");
            try {
                if (ds != null)
                    ds.Close();
            } catch (Exception e) {
                writeLog("[KERNEL : ERROR] ", e);
            }
            ds = new ds(this);
            health = new HashMap<>();
            try {
                writeLog("[KERNEL : START] " + "DS " + data_sources.get("main"));
                dsStr = data_sources.get("main");
                ds.Init(dsStr);
            } catch (Exception e) {
                writeLog("[KERNEL : ERROR] ", e);
                try {
                    writeLog("[KERNEL : START] " + "DS " + data_sources.get("standby"));
                    dsStr = data_sources.get("standby");
                    ds.Init(dsStr);
                } catch (Exception ex) {
                    writeLog("[KERNEL : ERROR] ", ex);
                    if (ds != null) ds.Close();
                    return 1;
                }
            }
            try {
                currentVM = new VM(Name, this);
                currentVM.setHealthRemote(VM.STARTING);
                if (MaxRuns == 0) MaxRuns = currentVM.core;
                if (MaxMsg == 0) {
                    MaxMsg = MaxRuns * MsgMultiplier;
                }
                writeLog("[KERNEL : INFO] " + "RMI=" + currentVM.rmi);
                writeLog("[KERNEL : INFO] " + "TYPE=" + currentVM.vmType);
                writeLog("[KERNEL : INFO] " + "PRIORITY=" + currentVM.vmPriority);
                writeLog("[KERNEL : INFO] " + "CLASS_ID=" + currentVM.idClass);
                writeLog("[KERNEL : INFO] " + "CLASS=" + ds.getClassName(currentVM.idClass));
                writeLog("[KERNEL : INFO] " + "NETWORK_ID=" + currentVM.vmNetwork);
                writeLog("[KERNEL : INFO] " + "NETWORK=" + ds.getNetworkName(currentVM.vmNetwork));
                writeLog("[KERNEL : INFO] " + "CORE=" + currentVM.core);
                writeLog("[KERNEL : INFO] " + "MEMORY=" + currentVM.memory);
                startManager();
                try {
                    if (currentVM.isControl()) {
                        currentVM.getVMs();
                    } else {
                        if (currentVM.master == null) {
                            writeLog("[KERNEL : START] " + "Master not set");
                            currentVM.remove();
                            terminate();
                            exit();
                        }
                    }
                } catch (Exception e) {
                    writeLog("[KERNEL : ERROR] ", e);
                }
                loadConfiguration();
            } catch (SQLException e) {
                writeLog("[KERNEL : ERROR] ", e);
                if (ds != null) ds.Close();
                return 1;
            } catch (Exception e) {
                writeLog("[KERNEL : ERROR] ", e);
                return 1;
            }
            currentVM.setHealthRemote(VM.RUN);
            startSupervisor();
            loadConfiguration();
            writeLog("[KERNEL : START] " + "INITIATE VM FINISH. VM HEALTH " + currentVM.getHealth());
            restartSchedule();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                if(!new File("./error").exists())
                    new File("./error").mkdirs();
                PrintStream ps = new PrintStream("./error/" + Name + ".error.txt");
                e.printStackTrace(ps);
                ps.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return 1;
        }

        return 0;
    }

    public Object invoke(ObjectName objectName, String rmiUrl, String Operation, Object[] Params, String[] paramsType) throws IOException {
        JMXServiceURL url = null;
        JMXConnector jmxConnector = null;
        try {
            url = new JMXServiceURL(rmiUrl);
            jmxConnector = JMXConnectorFactory.connect(url);
            MBeanServerConnection serverConnection = jmxConnector.getMBeanServerConnection();
            Object Result = serverConnection.invoke(objectName, Operation, Params, paramsType);
            return Result;
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("[KERNEL : DEBUG] " + e.getMessage());
        } finally {
            if(jmxConnector!=null)
                jmxConnector.close();
        }
        throw new IOException("invoke error");
    }

    public Object getAttribute(ObjectName objectName, String rmiUrl, String Attribute) throws IOException {
        JMXServiceURL url = null;
        JMXConnector jmxConnector = null;
        try {
            url = new JMXServiceURL(rmiUrl);
            jmxConnector = JMXConnectorFactory.connect(url);
            MBeanServerConnection serverConnection = jmxConnector.getMBeanServerConnection();
            Object Result = serverConnection.getAttribute(objectName, Attribute);
            return Result;
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("[KERNEL : DEBUG] " + e.getMessage());
        } finally {
            if(jmxConnector!=null)
                jmxConnector.close();
        }
        throw new IOException("getAttribute error");
    }

    public void setAttribute(ObjectName objectName, String rmiUrl, String Attribute, Object Value) throws IOException {
        JMXServiceURL url = null;
        JMXConnector jmxConnector = null;
        try {
            url = new JMXServiceURL(rmiUrl);
            jmxConnector = JMXConnectorFactory.connect(url);
            MBeanServerConnection serverConnection = jmxConnector.getMBeanServerConnection();
            Attribute attribute=new Attribute(Attribute, Value);
            serverConnection.setAttribute(objectName, attribute);
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("[KERNEL : DEBUG] " + e.getMessage());
        } finally {
            if(jmxConnector!=null)
                jmxConnector.close();
        }
        throw new IOException("setAttribute error");
    }

    public void healthSet(ObjectName subSystem) {
        Timestamp curTimestamp = new Timestamp(System.currentTimeMillis());
        health.put(subSystem, curTimestamp);
    }

    @Override
    public void setmaxExceptions(int count) {
        maxExceptions = count;
    }

    @Override
    public int getmaxExceptions() {
        return maxExceptions;
    }

    @Override
    public void saveConfiguration() {
        try {
            if (ini.value("default") == null) ini.set("default", new HashMap<>());
            ini.value("default").put("maxExceptions", String.valueOf(maxExceptions));
            ini.value("default").put("MaxRuns", String.valueOf(MaxRuns));
            ini.value("default").put("MaxMsg", String.valueOf(MaxMsg));
            ini.value("default").put("logPath", logPath);
            ini.value("default").put("manager.Xmx", String.valueOf(manager.Xmx));
            ini.value("default").put("manager.DELAY", String.valueOf(manager.DELAY));
            ini.value("default").put("supervisor.DELAY", String.valueOf(DELAY));
            ini.value("default").put("supervisor.MaxLookUpMin", String.valueOf(supervisor.MaxLookUpMin));
            ini.value("default").put("supervisor.resourceManagement", String.valueOf(supervisor.resourceManagement));
            ini.value("default").put("selectVmTypeMode", String.valueOf(selectVmTypeMode));
            ini.value("default").put("supervisor.resource.ReleaseResources", String.valueOf(supervisor.resource.ReleaseResources));
            ini.value("default").put("supervisor.resource.type", String.valueOf(supervisor.resource.getResourceManagementType()));
            ini.value("default").put("manager.type", String.valueOf(manager.getType()));
            ini.value("default").put("supervisor.resource.CpuMultiplier", String.valueOf(supervisor.resource.CpuMultiplier));
            ini.value("default").put("supervisor.resource.OnetimeLaunchUnitMax", String.valueOf(supervisor.resource.OnetimeLaunchUnitMax));
            Writer writer = new FileWriter("cfg.props");
            ini.save(writer);
            writer.close();
        } catch (FileNotFoundException e) {
            writeLog("[KERNEL : ERROR] ", e);
        } catch (IOException e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public void loadConfiguration() {
        if (!new File("cfg.props").exists()) return;
        try {
            BufferedReader reader = new BufferedReader(new FileReader("cfg.props"));
            ini = new Ini(reader);
            reader.close();
            loadConfiguration(ini.value("default"));
        } catch (FileNotFoundException e) {
            writeLog("[KERNEL : ERROR] ", e);
        } catch (IOException e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    public void loadConfiguration(Map<String, String> config) {
        maxExceptions = Integer.parseInt(config.getOrDefault("maxExceptions", String.valueOf(maxExceptions)));
        MaxRuns = Integer.parseInt(config.getOrDefault("MaxRuns", String.valueOf(MaxRuns)));
        MaxMsg = Integer.parseInt(config.getOrDefault("MaxMsg", String.valueOf(MaxMsg)));
        logPath = config.getOrDefault("logPath", logPath);
        selectVmTypeMode = Integer.parseInt(config.getOrDefault("selectVmTypeMode", String.valueOf(selectVmTypeMode)));
        if (manager != null) {
            manager.Xmx = Integer.parseInt(config.getOrDefault("manager.Xmx", String.valueOf(manager.Xmx)));
            manager.DELAY = Integer.parseInt(config.getOrDefault("manager.DELAY", String.valueOf(manager.DELAY)));
            manager.setType(Integer.parseInt(config.getOrDefault("manager.type", String.valueOf(manager.getType()))));
        }
        if (supervisor != null) {
            DELAY = Integer.parseInt(config.getOrDefault("supervisor.DELAY", String.valueOf(DELAY)));
            supervisor.MaxLookUpMin = Integer.parseInt(config.getOrDefault("supervisor.MaxLookUp", String.valueOf(supervisor.MaxLookUpMin)));
            supervisor.resourceManagement = Boolean.parseBoolean(config.getOrDefault("supervisor.resourceManagement", String.valueOf(supervisor.resourceManagement)));
            if(supervisor.resource != null) {
                supervisor.resource.ReleaseResources = Boolean.parseBoolean(config.getOrDefault("supervisor.resource.ReleaseResources", String.valueOf(supervisor.resource.ReleaseResources)));
                supervisor.resource.setResourceManagementType(Integer.parseInt(config.getOrDefault("supervisor.resource.type", String.valueOf(supervisor.resource.getResourceManagementType()))));
                supervisor.resource.CpuMultiplier = (Integer.parseInt(config.getOrDefault("supervisor.resource.CpuMultiplier", String.valueOf(supervisor.resource.CpuMultiplier))));
                supervisor.resource.OnetimeLaunchUnitMax = (Integer.parseInt(config.getOrDefault("supervisor.resource.OnetimeLaunchUnitMax", String.valueOf(supervisor.resource.OnetimeLaunchUnitMax))));
            }
        }
    }

    @Override
    public List<String> getNodes() {
        if (currentVM.isControl()) {
            List<String> net = new ArrayList<>();
            net.add(currentVM.vmName + ":" + ds.getClassName(currentVM.idClass) + ":" + currentVM.getHealth());
            synchronized (currentVM.VMs) {
                for (VM vm : currentVM.VMs)
                    net.add(vm.vmName + ":" + ds.getClassName(vm.idClass) + ":" + vm.getHealth());
            }
            return net;
        }
        return null;
    }

    @Override
    public int getNodesCount() {
        if(getNodes() != null)
            return getNodes().size();
        return 0;
    }

    @Override
    public String getNodeClass() {
        return ds.getClassName(currentVM.idClass);
    }

    @Override
    public void allCommand(String command) throws ReflectionException, MBeanException, InstanceNotFoundException, IOException {
        synchronized (currentVM.VMs) {
            for (VM vm : currentVM.VMs)
                invoke(vm.kernel.onKernel, vm.rmi, command, null, null);
        }
    }

    @Override
    public List<String> getExceptions() {
        return Exceptions;
    }

    public void writeLogWO(String logString) {
        Log.append(logString);
        if (logString.contains("WARNING]")) {
            synchronized (Exceptions) {
                if (Exceptions.size() > maxExceptions)
                    Exceptions.remove(Exceptions.get(0));
                Exceptions.add(utils.set_date() + logString);
            }
        }
    }

    public void writeLog(String logString) {
        System.out.println(utils.set_date() + logString);
        Log.append(logString);
        if (logString.contains("WARNING]")) {
            synchronized (Exceptions) {
                if (Exceptions.size() > maxExceptions)
                    Exceptions.remove(Exceptions.get(0));
                Exceptions.add(utils.set_date() + logString);
            }
        }
    }

    public void writeLog(String logString, Exception e) {
        String Trace = "";
        for (StackTraceElement el : e.getStackTrace())
            Trace += el.toString();
        writeLog(logString + " " + e.toString());
        writeLog(Trace);
        synchronized (Exceptions) {
            if (Exceptions.size() > maxExceptions)
                Exceptions.remove(Exceptions.get(0));
            Exceptions.add(utils.set_date() + logString + " " + e.toString() + "\n" + Trace);
        }
        e.printStackTrace();
    }

    @Override
    public void bat(String bat) {
        try {
            utils.ExecNoThread(new String[]{bat}, ".", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int call(int id) {
        boolean flag=false;
        if(currentVM.master!=null)
            if(currentVM.master.id==id)
                flag=true;
        if(!flag)
            try {
                VM vm = currentVM.getVM(id);
                if(vm != null)
                    currentVM.master=vm;
            } catch (Exception e) {
                writeLog("[SUPERVISOR : ERROR] ", e);
            }
        return call();
    }

    @Override
    public void addStaticVM(int idVm) {
        try {
            VM vm = currentVM.getVM(idVm);
            if (vm != null) {
                boolean flag=false;
                synchronized (currentVM.VMs) {
                    for (VM v : currentVM.VMs)
                        if(vm.id==v.id)
                            flag=true;
                    if(!flag)
                        currentVM.VMs.add(vm);
                }
                if(!flag) {
                    vm.register();
                    vm.listenStatus();
                    writeLog("[KERNEL : DEBUG] " + "Add static VM " + vm.vmName);
                }
            }
        } catch (Exception e) {
            writeLog("[KERNEL : ERROR] ", e);
        }
    }

    @Override
    public int call() {
        return currentVM.vmHealth();
    }
}