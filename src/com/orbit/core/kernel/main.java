package com.orbit.core.kernel;

import com.orbit.core.utils.Ini;
import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: ehot
 * Date: 17.02.17
 * Time: 10:27
 * To change this template use File | Settings | File Templates.
 */
public class main {

    public static void main(String[] args)
            throws Exception {
        int port=6969;
        String vmHost=null;
        String dbConn=null;
        String Cfg=null;
        try {
            if(args.length>0) {
                for(String arg:args) {
                    if(arg.startsWith("-port")) port = Integer.parseInt(arg.substring(arg.indexOf("=")+1));
                    if(arg.startsWith("-vm")) vmHost = arg.substring(arg.indexOf("=")+1);
                    if(arg.startsWith("-cfg")) Cfg = arg.substring(arg.indexOf("=")+1);
                    if(arg.startsWith("-db")) dbConn = arg.substring(arg.indexOf("=")+1);
                }
            }
            System.out.println("Port="+port);
            System.out.println("db="+dbConn);
        } catch (Exception e) {
            System.out.println("Default port=6969");
        }
        Ini ini=null;
        if(Cfg!=null)
            if(new File(Cfg).exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(Cfg));
                ini=new Ini(reader);
                reader.close();
            }
        if(Cfg==null && dbConn!=null) {
                ini=new Ini();
                Map<String, String> properties=new HashMap<>();
                properties.put("main", dbConn);
                ini.set("datasources", properties);
            }

        LocateRegistry.createRegistry(port);
        System.out.println("JMXServiceURL=service:jmx:rmi://localhost/jndi/rmi://localhost:"+port+"/jmxrmi");
        JMXServiceURL url=new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:"+port+"/jmxrmi");

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        JMXConnectorServer srv = JMXConnectorServerFactory.newJMXConnectorServer(url,null,mbs);
        System.out.println("JMXConnectorServer start");
        srv.start();

        Kernel kernel=null;
        if(ini==null) {
            System.out.println("cfg.ini not set");
            kernel = new Kernel(mbs, srv, vmHost);
        }
        else
            kernel = new Kernel(mbs, srv, ini, vmHost);
        System.out.println(kernel.onKernel.toString());
        mbs.registerMBean(kernel, kernel.onKernel);
        if(kernel.init()==0) {
            while (!kernel.exit && srv.isActive())
                Thread.sleep(kernel.DELAY/2);
        }
        Thread.sleep(kernel.DELAY/2);
        if(srv.isActive()) srv.stop();
        System.exit(0);
    }
}
