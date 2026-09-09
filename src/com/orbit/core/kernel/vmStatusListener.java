package com.orbit.core.kernel;

import com.orbit.core.manager.Message;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class vmStatusListener extends TimerTask {
    private VM vm;
    public Kernel kernel;
    public boolean SHUTDOWN = false;
    public boolean FAILED = false;
    public int failedRestartMin = 60;
    public Timer timer;
    public Timestamp lookUpFailed = new Timestamp(0);
    public Timestamp suspendLookUp = null;

    public vmStatusListener(VM v, Kernel knl) {
        kernel = knl;
        vm = v;
        timer = new Timer(vm.vmName, false);
    }

    @Override
    public void run() {
        try {
            if(vm.vmHealth()!=VM.RUN) {
                synchronized (vm.queue) {
                    if(vm.queue.size()>0) {
                        for (Message mes : vm.queue)
                            if (mes.STATE == Message.PREPARE)
                                mes.STATE = Message.WAITING;
                        vm.queue.clear();
                        kernel.writeLog("[LISTENER : DEBUG] " + "Release messages from " + vm.vmName);
                    }
                }
                if(vm.vmHealth()==VM.SUSPENDING) {
                    if(suspendLookUp==null)
                        suspendLookUp = new Timestamp(System.currentTimeMillis());
                    if(suspendLookUp!=null)
                        if(vm.free_queue_cnt==vm.max_msg_cnt) {
                            long mTs = new Date().getTime() - suspendLookUp.getTime();
                            if(mTs>24*60*60*1000) {
                                kernel.writeLog("[LISTENER : WARNING] " + "Try to stop node VM " + vm.vmName);
                                vm.stopNode();
                                suspendLookUp = null;
                            }
                        }
                }
            }
            if(kernel.exit)
                cancel();
            if (SHUTDOWN) {
                kernel.writeLog("[LISTENER : DEBUG] " + "Shutdown VM " + vm.vmName);
                if(vm.remove())
                    cancel();
            }
            if (vm.vmHealth() == VM.SHUTDOWN && !SHUTDOWN) {
                vm.exit();
                SHUTDOWN = true;
            }
            if (!vm.isControl()
                    && vm.vmHealth() != VM.FAILED
                    && vm.vmHealth() != VM.SHUTDOWN
                    && vm.vmHealth() != VM.OFF
                    && kernel.currentVM.vmHealth() == VM.RUN) {
                if (vm.lookUpTime != null)
                    if(kernel.supervisor != null)
                        if (!vm.healthCheck(kernel.supervisor.MaxLookUpMin)) {
                            kernel.writeLog("[LISTENER : WARNING] " + "VM " + vm.vmName + " health unavailable");
                            vm.setHealthRemote(VM.FAILED);
                            vm.Rollback(kernel.ds);
                            RestartProcedure(vm);
                        }
            }
            if (!vm.isControl() && !FAILED
                    && vm.vmHealth() == VM.FAILED
                    && kernel.currentVM.vmHealth() == VM.RUN) {
                if (vm.lookUpTime != null)
                    if(kernel.supervisor != null)
                        if (!vm.healthCheck(failedRestartMin)) {
                            long mTs = new Date().getTime() - lookUpFailed.getTime();
                            if(mTs>failedRestartMin*60*1000) {
                                lookUpFailed = new Timestamp(System.currentTimeMillis());
                                FAILED = true;
                                kernel.writeLog("[LISTENER : WARNING] " + "VM " + vm.vmName + " in FAILED state " + failedRestartMin + " min");
                                RestartProcedure(vm);
                            }
                        }
            }
        } catch (Exception e) {
            kernel.writeLog("[LISTENER : ERROR]", e);
        }
    }

    public void RestartProcedure(VM vm) {
        try {
            kernel.writeLog("[LISTENER : DEBUG] " + "Begin restart procedure for " + vm.vmName);
            vm.terminate();
            vm.exit();
            vm.startNode();
            int sec = 0;
            while (vm.vmHealth() != VM.RUN
                    && !SHUTDOWN) {
                Thread.sleep(1000);
                sec += 1;
                if (sec > kernel.supervisor.MaxLookUpMin * 60) {
                    vm.setHealthRemote(VM.FAILED);
                    break;
                }
            }
        }
        catch (Exception e) {
            kernel.writeLog("[LISTENER : ERROR] ", e);
        }
    }
}
