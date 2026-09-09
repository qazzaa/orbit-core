package com.orbit.core.node_manager;


import com.sun.management.OperatingSystemMXBean;
import com.orbit.core.kernel.Kernel;
import com.orbit.core.utils.utils;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: ehot
 * Date: 17.02.17
 * Time: 10:27
 * To change this template use File | Settings | File Templates.
 */
public class Instance implements InstanceMBean {

    public static final String version = "0.1.3";
    public MBeanServer mBeanServer;
    public String objectName;
    public List<Node> nodes;
    public JMXConnectorServer connectorServer;

    public static void main(String[] args)
            throws Exception {
        Instance instance = new Instance();
        int port=8555;
        String node= "";
        try {
            if(args.length>0) {
                for(String arg:args) {
                    if(arg.startsWith("-port")) port = Integer.parseInt(arg.split("=")[1]);
                    if(arg.startsWith("-node")) node = arg.substring(arg.indexOf("=")+1);
                }
            }
            System.out.println("Port="+port);
            System.out.println("Node="+node);
        } catch (Exception e) {
            System.out.println("Default port=8555");
        }
        LocateRegistry.createRegistry(port);
        System.out.println("JMXServiceURL=service:jmx:rmi://localhost/jndi/rmi://localhost:"+port+"/jmxrmi");
        JMXServiceURL url=new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:"+port+"/jmxrmi");

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        JMXConnectorServer srv = JMXConnectorServerFactory.newJMXConnectorServer(url,null, mbs);
        System.out.println("JMXConnectorServer start");
        srv.start();

        ObjectName name=new ObjectName(Kernel.sNM);
        instance.connectorServer=srv;
        instance.mBeanServer=mbs;
        instance.objectName=Kernel.sNM+",category=nodes,name=";
        instance.nodes=new ArrayList<>();

        mbs.registerMBean(instance, name);

        if(!node.isEmpty())
            instance.startNode(node, "default");

        System.out.println("Enter 'stop' to exit...");
        byte[] x = new byte[4];
        while(x[0]!='s'||x[1]!='t'||x[2]!='o'||x[3]!='p')
            System.in.read(x);

        srv.stop();
        System.out.println("JMXConnectorServer stop");
    }

    @Override
    public void startNode(String cmd, String nName) {
        System.out.println("Start node - "+cmd);
        Instance instance=this;
        Thread thr = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Node node = new Node(instance, nName);
                    node.cmd = cmd;
                    node.time = new Timestamp(System.currentTimeMillis());
                    synchronized (instance.nodes) {
                        instance.nodes.add(node);
                    }
                    utils.ExecNoThread(cmd.split(" "), ".", nName);
                    synchronized (instance.nodes) {
                        instance.nodes.remove(node);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thr.setPriority(Thread.MAX_PRIORITY);
        thr.start();
        System.out.println("OK");
    }

    @Override
    public String getState() {
        return "OK";
    }

    @Override
    public List<String> getNodes() {
        List<String> ns = new ArrayList<>();
        synchronized (nodes) {
            for (Node node : nodes)
                ns.add(node.getName()+" : "+node.getStarting());
        }
        return ns;
    }

    @Override
    public int getSize() {
        return nodes.size();
    }

    @Override
    public void stop() {
        try {
            connectorServer.stop();
            System.out.println("JMXConnectorServer stop");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    @Override
    public String getCpuLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return String.valueOf(osBean.getSystemCpuLoad());
    }

    @Override
    public String getRamLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return String.valueOf(osBean.getTotalPhysicalMemorySize()-osBean.getFreePhysicalMemorySize());
    }

    @Override
    public String getFreeMem() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return String.valueOf(osBean.getFreePhysicalMemorySize());
    }

    @Override
    public String getTotalMem() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return String.valueOf(osBean.getTotalPhysicalMemorySize());
    }

    @Override
    public String getLoadAvg() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return String.valueOf(osBean.getSystemLoadAverage());
    }

    @Override
    public int getSysAvailProcessors() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return osBean.getAvailableProcessors();
    }

    @Override
    public String getVersion() {
        return version;
    }
}
