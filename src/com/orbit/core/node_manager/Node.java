package com.orbit.core.node_manager;


import com.orbit.core.utils.utils;
import java.sql.Timestamp;

public class Node implements NodeMBean {
    private Instance instance;
    private String Name;
    public Timestamp time;
    public String cmd;

    public Node(Instance n, String name) {
        instance=n;
        Name=name;
    }

    @Override
    public String getName() {
        return Name;
    }

    @Override
    public String getStarting() {
        return utils.set_date(time);
    }

}
