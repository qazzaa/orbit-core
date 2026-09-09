package com.orbit.core.supervisor;

import com.orbit.core.kernel.Kernel;
import com.orbit.core.kernel.VM;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class Supervisor extends TimerTask implements SupervisorMBean {

    public boolean resourceManagement = true;

    public int MaxLookUpMin=15;
    public Kernel kernel;
    public Resource resource = null;
    private Timer timer;

    public Supervisor(Kernel knl) {
        kernel=knl;
        timer = new Timer("Supervisor", false);
    }

    public void start() {
        stop();
        if(kernel.currentVM.isControl()) {
            try {
                if(kernel.mBeanServer.isRegistered(kernel.onResourcePool))
                    kernel.mBeanServer.unregisterMBean(kernel.onResourcePool);
                resource = new Resource(kernel);
                kernel.mBeanServer.registerMBean(resource, kernel.onResourcePool);
                resource.start();
            } catch (Exception e) {
                kernel.writeLog("[SUPERVISOR : ERROR] ", e);
            }
        }
        timer = new Timer("Supervisor", false);
        timer.schedule(this, 0, kernel.DELAY);
        kernel.writeLog("[SUPERVISOR : DEBUG] " + "Start supervisor system");
    }

    public void stop() {
        if(resource != null)
            resource.stop();
        timer.cancel();
        kernel.writeLog("[SUPERVISOR : DEBUG] " + "Stop supervisor system");
    }

    @Override
    public void resetNodesStates() {
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs) {
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
                        kernel.writeLog("[SUPERVISOR : ERROR] ", e);
                        vm.setHealthRemote(VM.SHUTDOWN);
                        vm.listenStatus();
                    }
                }
            }
        }
    }

    @Override
    public void resetResources() {
        Thread thr = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    kernel.supervisor.resourceManagement=false;
                    Set<ResourceMapping> resourceMappings = new HashSet<>();
                    synchronized (kernel.supervisor.resource.resourceMapping.resources) {
                        for (ResourceMapping resource : kernel.supervisor.resource.resourceMapping.resources) {
                            boolean flag=false;
                            for (ResourceMapping mapping : resourceMappings)
                                if(resource.name.equals(mapping.name))
                                    flag=true;
                            if(!flag)
                                resourceMappings.add(resource);
                        }
                    }
                    kernel.supervisor.resourceManagement=true;
                } catch (Exception e) {
                    kernel.writeLog("[SUPERVISOR : ERROR] ", e);
                }
            }
        });
        thr.setPriority(Thread.MAX_PRIORITY);
        thr.start();
    }

    @Override
    public void run() {
        kernel.writeLog("[SUPERVISOR : DEBUG] " + "Supervisor health normal");
        try {
            if(kernel.manager.timer!=null)
                kernel.manager.timer.purge();
            if (kernel.currentVM.isControl()) {
                if (resource.timer != null)
                    resource.timer.purge();
                synchronized (kernel.currentVM.VMs) {
                    for (VM vm : kernel.currentVM.VMs)
                        if (vm.listener != null)
                            if (vm.listener.timer != null)
                                vm.listener.timer.purge();
                }
            }
            if(kernel.currentVM.vmHealth() == VM.RUN) {
                if (!kernel.ds.test()) {
                    kernel.writeLog("[SUPERVISOR : WARNING] " + "DataSource connection warning");
                    kernel.ds.Close();
                    kernel.currentVM.setHealthRemote(VM.WARNING);
                }
                if(!kernel.ds.exist()) {
                    kernel.writeLog("[SUPERVISOR : WARNING] " + "VM does not exist");
                    kernel.suspend();
                    kernel.terminate();
                    kernel.exit();
                }
            } else {
                if (kernel.currentVM.vmHealth() == VM.WARNING) {
                    boolean isHealthy=true;
                    if (!kernel.ds.test()) {
                        if (kernel.ds.lookUpCheck()) {
                            kernel.writeLog("[SUPERVISOR : WARNING] " +
                                    "DataSource try to reset connection");
                            kernel.resetDS();
                            kernel.ds.lookUp();
                        }
                        isHealthy=false;
                    }
                    if (isHealthy) kernel.currentVM.setHealthRemote(VM.RUN);
                }
            }
//            kernel.currentVM.setHealthRemote(kernel.currentVM.vmHealth());
        } catch (Exception e) {
            kernel.writeLog("[SUPERVISOR : ERROR] ", e);
        }
        if (kernel.currentVM.isControl()) {
            try {
                synchronized (kernel.currentVM.VMs) {
                    kernel.currentVM.VMs.removeIf(VM.shutdown);
                }
                reMaxMsg();
                synchronized (kernel.supervisor.resource.resourceMapping.resources) {
                    for(ResourceMapping rm : kernel.supervisor.resource.resourceMapping.resources)
                        if(rm.listener.timer!=null) {
                            rm.listener.timer.purge();
                            if(!rm.check())
                                if(rm.quickTest())
                                    rm.listenStatus();
                        }
                }
            } catch (Exception e) {
                kernel.writeLog("[SUPERVISOR : ERROR] ", e);
            }
        } else {
            try {
                kernel.writeLog("[SUPERVISOR : DEBUG] " + "Master "+kernel.currentVM.master.vmName+" perform response");
                kernel.currentVM.master.setHealth(kernel.currentVM.master.response());
            } catch (Exception e) {
                kernel.writeLog("[SUPERVISOR : ERROR] ", e);
            }
        }
    }

    public void reMaxMsg() {
        int MaxMsg = kernel.MaxRuns * kernel.MsgMultiplier;
        synchronized (kernel.currentVM.VMs) {
            for (VM vm : kernel.currentVM.VMs)
                if (vm.vmHealth() == VM.RUN)
                    try {
                        MaxMsg += vm.max_msg_cnt;
                    } catch (Exception e) {
                        kernel.writeLog("[SUPERVISOR : ERROR] ", e);
                    }
        }
        if (kernel.getMaxMsgCount() < MaxMsg) kernel.setMaxMsgCount(MaxMsg);
    }

    @Override
    public void setdelaymsec(int delay) {
        kernel.DELAY=delay;
    }

    @Override
    public int getdelaymsec() {
        return kernel.DELAY;
    }

    @Override
    public void setMaxLookUpMin(int min) {
        MaxLookUpMin=min;
    }

    @Override
    public int getMaxLookUpMin() {
        return MaxLookUpMin;
    }

    @Override
    public void setResourceManagement(boolean mode) {
        resourceManagement=mode;
    }

    @Override
    public boolean getResourceManagement() {
        return resourceManagement;
    }
}
