package com.orbit.core.supervisor;

import com.orbit.core.kernel.Kernel;
import com.orbit.core.kernel.VM;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ResourceListener extends TimerTask {
    public Kernel kernel;
    ResourceMapping resourceMapping;
    public Timer timer;
    public Timestamp checkErrors = new Timestamp(0);
    public Timestamp resetErrors = new Timestamp(0);
    public Timestamp lookUpUnavailable = new Timestamp(0);
    public boolean failedNodes = false;
    public boolean unavailable = false;
    public int attempt = 3;

    public ResourceListener(ResourceMapping rm, Kernel knl) {
        kernel = knl;
        resourceMapping = rm;
        timer = new Timer(resourceMapping.name+"-"+resourceMapping.clName, false);
    }

    @Override
    public void run() {
        try {
            long mTs=new Date().getTime()-checkErrors.getTime();
            if(mTs>kernel.supervisor.resource.MaxLookUpSec*1000) {
                checkErrors=new Timestamp(System.currentTimeMillis());
                if(!failedNodes)
                    if(!unavailable) {
                        if (resourceMapping.quickTest()) {
                            if (resourceMapping.status == 0) {
                                resourceMapping.status = 1;
                                unavailable = false;
                                kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " status active");
                            }
                        } else {
                            if (resourceMapping.status == 1) {
                                resourceMapping.status = 0;
                                unavailable = true;
                                lookUpUnavailable = new Timestamp(System.currentTimeMillis());
                                kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " status inactive");
                            }
                        }
                    } else {
                        mTs = new Date().getTime() - lookUpUnavailable.getTime();
                        if(mTs>kernel.supervisor.MaxLookUpMin*60*1000
                            &&attempt>0) {
                            attempt--;
                            lookUpUnavailable = new Timestamp(System.currentTimeMillis());
                            if (resourceMapping.quickTest()) {
                                resourceMapping.status = 1;
                                unavailable = false;
                                attempt = 0;
                                kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " status active");
                            } else {
                                kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " still inactive");
                            }
                        }
                    }
                if(!unavailable)
                    if (resourceMapping.getErrorNodes()>resourceMapping.getSysAvailProcessors()*0.2) {
                        if(resourceMapping.status==1)
                            resetErrors=new Timestamp(System.currentTimeMillis());
                        kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " over 20% nodes failed");
                        resourceMapping.status=0;
                        failedNodes = true;
                        kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " local status set not active");
                        mTs=new Date().getTime()-resetErrors.getTime();
                        if(mTs>kernel.supervisor.resource.MaxTimeOutMin*60*1000) {
                            kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " try reset nodes");
                            resourceMapping.resetNodes();
                            kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " reset nodes complete");
                        }
                    } else {
                        if(resourceMapping.status==0
                                &&failedNodes) {
                            if (resourceMapping.getErrorNodes()<resourceMapping.getSysAvailProcessors()*0.2) {
                                kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " failed nodes count below 20%");
                                resourceMapping.status = 1;
                                failedNodes = false;
                                kernel.writeLog("[RESOURCE LISTENER : WARNING] " + resourceMapping.name + " local status return to active");
                            }
                        }
                    }

                if(overLoadTest()) {
                    VM vm = kernel.supervisor.resource.getVMToStop(resourceMapping);
                    if (vm != null) {
                        vm.setHealth(VM.WARNING);
                        try {
                            kernel.supervisor.resource.stopVM(vm);
                        } catch (Exception e) {
                            kernel.writeLog("[RESOURCE LISTENER : ERROR] " + resourceMapping.name, e);
                        }
                    }
                }
            }

            if(!unavailable) {
                if (resourceMapping.CpuMetric.size() > 9)
                    resourceMapping.CpuMetric.remove(resourceMapping.CpuMetric.get(0));
                resourceMapping.CpuMetric.add(resourceMapping.getCpuLoad());
                if (resourceMapping.MemMetric.size() > 9)
                    resourceMapping.MemMetric.remove(resourceMapping.MemMetric.get(0));
                resourceMapping.MemMetric.add(resourceMapping.getFreeMem());
                int prevCpu = resourceMapping.avgFreeCpu;
                resourceMapping.avgFreeCpu = 0;
                int cnt = 0;
                synchronized (resourceMapping.CpuMetric) {
                    for (String cpu : resourceMapping.CpuMetric)
                        if (!cpu.trim().equals("0.0")) {
                            resourceMapping.avgFreeCpu += (int) (Double.parseDouble(cpu) * 100);
                            cnt++;
                        }
                }
                if (cnt > 0)
                    resourceMapping.avgFreeCpu = 100 - Math.abs(resourceMapping.avgFreeCpu / cnt);
                else
                    resourceMapping.avgFreeCpu = prevCpu;
                resourceMapping.avgFreeMem = 0;
                synchronized (resourceMapping.MemMetric) {
                    for (String mem : resourceMapping.MemMetric)
                        resourceMapping.avgFreeMem += Long.parseLong(mem);
                }
                resourceMapping.avgFreeMem = resourceMapping.avgFreeMem / resourceMapping.MemMetric.size();
            }

            resourceMapping.lookUpTime=new Timestamp(System.currentTimeMillis());
        } catch (Exception e) {
            kernel.writeLog("[RESOURCE LISTENER : ERROR] " + resourceMapping.name, e);
            timer.cancel();
            resourceMapping.avgFreeMem = 0;
            resourceMapping.avgFreeCpu = 0;
            resourceMapping.quickTest();
            resourceMapping.listenStatus();
        }
    }

    public boolean overLoadTest() {
        if (resourceMapping.avgFreeCpu < 20) {
            kernel.writeLog("[RESOURCE LISTENER : INFO] " + resourceMapping.name + " free cpu under 20%");
            return true;
        }
        if(resourceMapping.avgFreeMem < resourceMapping.totalMem / 20) {
            kernel.writeLog("[RESOURCE LISTENER : INFO] " + resourceMapping.name + " free mem under 20%");
            return true;
        }
        return false;
    }
}
