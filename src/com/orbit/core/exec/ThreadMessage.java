package com.orbit.core.exec;

public class ThreadMessage {//extends Thread
    private ThreadRunner ThreadExec = null;
    private long maxTime;
    private Integer retcode = null;
    private String run_string = "";
    private Run run = null;

    public ThreadMessage() {}

    public ThreadMessage(ThreadRunner _ThreadExec, long _maxTime, String _run_string) {
        super();
        ThreadExec = _ThreadExec;
        maxTime = _maxTime;
        run_string = _run_string;
    }

    public ThreadMessage(ThreadRunner _ThreadExec, long _maxTime, String _run_string, Run _run) {
        super();
        ThreadExec = _ThreadExec;
        maxTime = _maxTime;
        run_string = _run_string;
        run = _run;
    }

    public void run() {
        if(ThreadExec != null) {
            Bin();
            return;
        }
    }

    private void Bin() {
        int counter=0;
        int thrcount = 0;
        if(maxTime>0) {
            for (thrcount = 0; thrcount < maxTime; thrcount++) {
                if (ThreadExec.isAlive()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        ThreadExec.kernel.writeLog("[EXECUTE : ERROR] ", ex);
                        return;
                    }
                } else {
                    ThreadExec.kernel.writeLogWO("[EXECUTE : DEBUG] " + "Main thread already exit, so exitting...");
                    return;
                }
                if (counter == 10) {
                    ThreadExec.kernel.writeLogWO("[EXECUTE : DEBUG] " + "Timeout in " + (maxTime - thrcount) + " sec's");
                    counter = 0;
                }
                counter++;
            }
            ThreadExec.kernel.writeLogWO("[EXECUTE : DEBUG] " + "Main thread is going to destroy");
            retcode = 1;
            if(run == null)
                ThreadExec.getProcess().destroy();
            else
                run.destroy_();
            ThreadExec.kernel.writeLogWO("[EXECUTE : DEBUG] " + "Destroy completed");
        }
        else {
            while(ThreadExec.isAlive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    ThreadExec.kernel.writeLog("[EXECUTE : ERROR] ", e);
                }
                if (counter == 10) {
                    ThreadExec.kernel.writeLogWO("[EXECUTE : DEBUG] " + "Still running...");
                    counter = 0;
                }
                counter++;
            }
            ThreadExec.kernel.writeLogWO("[EXECUTE : DEBUG] " + "Main thread already exit, so exitting...");
        }
    }
}
