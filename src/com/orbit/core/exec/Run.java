package com.orbit.core.exec;

import com.orbit.core.kernel.Kernel;
import com.orbit.core.manager.Message;

import java.util.HashMap;
import java.util.Map;

public class Run extends Thread {
    public ThreadRunner threadRunner;
    public ThreadMessage threadMessage;
    public Message message;
    public Integer exit_code = null;
    Kernel kernel;

    String run_str;
    String work_dir;
    long timeout;
    Map<String, String> env_props;

    public Run() {}

    public Run(Kernel knl, Message mes) {
        kernel=knl;
        message=mes;
        message.STATE = Message.IN_PROGRESS;
        message.change();
        String OPERATION=message.OPERATION;
        if(kernel.ds.getOperationProfile(message.OPERATION, message.CLASS_ID) != null)
            OPERATION=kernel.ds.getOperationProfile(message.OPERATION, message.CLASS_ID);
        Map<String, String> msgOperParams = kernel.ds.getOperationParams(OPERATION,
                kernel.ds.getClassName(message.CLASS_ID));
        if (msgOperParams.size() > 0) {
            if (msgOperParams.getOrDefault("RUN_STRING", null) == null) {
                message.STATE = Message.ROLLBACK;
                kernel.writeLog("[ENGINE : ROLLBACK] " +
                        "RUN_STRING not found in params");
                return;
            }
            run_str = msgOperParams.get("RUN_STRING") +
                    " " + message.REG_NUM +
                    " " + message.GUID +
                    " " + kernel.currentVM.rmi;
            work_dir = msgOperParams.getOrDefault("WORK_DIR", ".");
            timeout =  kernel.ds.getClassTimeout(kernel.currentVM.idClass);
            env_props = new HashMap<>();
            start();
//            kernel.writeLog("[ENGINE : DEBUG] " + "Engine " + message.GUID);
        } else {
            kernel.writeLog("[ENGINE : WARNING] " + "Operation "+OPERATION+" params not found " + message.GUID);
            message.STATE = Message.REMOVE;
        }
    }

    public int execute(String _run_str, String _work_dir, long _timeout, Map<String, String> _env_props) {
        threadRunner = new ThreadRunner(kernel, _run_str, _work_dir, _env_props);
        threadMessage = new ThreadMessage(threadRunner, _timeout, _run_str, this);
        threadRunner.start();
        threadMessage.run();
        if(threadRunner.getExitValue() != null)
            return threadRunner.getExitValue();
        else
            return 1;
    }

    public void destroy_() {
        if(threadRunner != null) {
            try {
                Thread.sleep(kernel.manager.DELAY);
            } catch (InterruptedException e) {
                kernel.writeLog("[ENGINE : ERROR]", e);
            }
            if (threadRunner.getProcess().isAlive())
                threadRunner.getProcess().destroy();
        }
        exit_code=1;
    }

    public void run() {
        try {
            try {
                exit_code = execute(run_str, work_dir, timeout, env_props);
            } catch (Exception e) {
                exit_code = 1;
                kernel.writeLog("[ENGINE : ERROR]", e);
                throw e;
            }
        } catch (Exception e) {
            exit_code = 1;
            kernel.writeLog("[ENGINE : ERROR]", e);
            throw e;
        }
    }

    public void ping() {
        if (exit_code != null) {
            if (exit_code < 0) {
                message.STATE = Message.ROLLBACK;
                kernel.writeLog("[ENGINE : ROLLBACK] " +
                        "Rollback message " + message.GUID +
                        ". Error when execute operation " + message.OPERATION);
                return;
            }
            if (exit_code == 6) {
                message.STATE = Message.ROLLBACK;
                kernel.writeLog("[ENGINE : ROLLBACK] " +
                        "Rollback message " + message.GUID +
                        ". Error 6 when execute operation " + message.OPERATION);
                return;
            }
            if (exit_code == 1) {
                message.STATE = Message.ROLLBACK;
                kernel.writeLog("[ENGINE : ROLLBACK] " +
                        "Rollback message " + message.GUID +
                        ". Error 1 when execute operation " + message.OPERATION);
                return;
            }
            if (exit_code != 1) {
                kernel.writeLog("[ENGINE : DEBUG] " +
                        "Work complete " + message.GUID);
                message.STATE = Message.REMOVE;
            }
        }
    }
}
