package com.jizhaoyu.chatbi.application.datasource;

public interface ExternalDataSourceConnectionProbe {
    ConnectionProbeResult probe(ExternalDataSourceConnectionSpec connectionSpec);
}
