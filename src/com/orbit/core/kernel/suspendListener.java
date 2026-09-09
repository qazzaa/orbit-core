package com.orbit.core.kernel;

import java.util.Timer;
import java.util.TimerTask;

public class suspendListener extends TimerTask {
    public Kernel kernel;
    public int seconds = 0;
    public Timer timer;

    public suspendListener(Kernel knl) {
        kernel = knl;
        timer = new Timer("Suspend timer", false);
    }

    @Override
    public void run() {
        try {
            if(kernel.manager.FORCE_STOP)
                stop();
            kernel.writeLog("[" + kernel.currentVM.vmName + " : SUSPEND LISTENER : SUSPEND] " + "Suspending...");
            seconds += 5;
            if (kernel.manager.class_timeout != null)
                if (kernel.manager.class_timeout.getOrDefault(kernel.currentVM.idClass, -1) > 0)
                    if (seconds > kernel.manager.class_timeout.get(kernel.currentVM.idClass)) {
                        kernel.writeLog("[SUSPEND LISTENER : SUSPEND] " + "VM class timeout");
                        kernel.currentVM.setHealthRemote(VM.WARNING);
                        stop();
                    }
        } catch (Exception e) {
            kernel.writeLog("[SUSPEND LISTENER : ERROR] ", e);
        }
    }

    public void stop() {
        try {
            kernel.writeLog("[" + kernel.currentVM.vmName + " : SUSPEND LISTENER : STOP NODE] " + "Turning off...");
            timer.cancel();
            if (kernel.supervisor != null)
                kernel.supervisor.resourceManagement = false;
            kernel.stopManager();
            if (kernel.supervisor.resource != null)
                kernel.supervisor.resource.stop();
            kernel.currentVM.setHealthRemote(VM.SHUTDOWN);
            kernel.writeLog("[" + kernel.currentVM.vmName + " : SUSPEND LISTENER : STOP NODE] " + "Close data source...");
            kernel.ds.Close();
        } catch (Exception e) {
            kernel.writeLog("[SUSPEND LISTENER : ERROR] ", e);
        }
    }
}