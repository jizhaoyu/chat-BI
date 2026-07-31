package com.jizhaoyu.chatbi.domain.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceStateMachineTest {
    @Test
    void allowsOnlyDocumentedTransitions() {
        assertThat(DataSourceStateMachine.canTransition(DataSourceStatus.DRAFT, DataSourceStatus.TESTING)).isTrue();
        assertThat(DataSourceStateMachine.canTransition(DataSourceStatus.TESTING, DataSourceStatus.READY)).isTrue();
        assertThat(DataSourceStateMachine.canTransition(DataSourceStatus.READY, DataSourceStatus.DISABLED)).isTrue();
        assertThat(DataSourceStateMachine.canTransition(DataSourceStatus.DRAFT, DataSourceStatus.READY)).isFalse();
    }

    @Test
    void rejectsInvalidTransition() {
        assertThatThrownBy(() -> DataSourceStateMachine.transition(DataSourceStatus.DRAFT, DataSourceStatus.READY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DATA_SOURCE_STATE_TRANSITION_NOT_ALLOWED");
    }
}
