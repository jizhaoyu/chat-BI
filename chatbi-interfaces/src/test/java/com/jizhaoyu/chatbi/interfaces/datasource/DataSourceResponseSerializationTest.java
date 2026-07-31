package com.jizhaoyu.chatbi.interfaces.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceResponseSerializationTest {
    @Test
    void responseOmitsConnectionAndCredentialFields() throws Exception {
        String sentinel = "credential-sentinel-do-not-leak";
        DataSourceView view = new DataSourceView(UUID.randomUUID(), "sales", "db.internal.test", 3306,
                "sample_sales", "secret-user", sentinel, DataSourceDialect.MYSQL, DataSourceStatus.DRAFT, 1000, 30);

        String json = new ObjectMapper().writeValueAsString(DataSourceController.DataSourceResponse.from(view));

        assertThat(json)
                .contains("sales", "MYSQL", "DRAFT")
                .doesNotContain(sentinel, "secret-user", "db.internal.test", "sample_sales");
    }
}
