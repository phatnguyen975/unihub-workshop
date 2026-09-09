package com.unihub.workshop.shared.realtime.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JDBC Workshop Authorization Adapter Unit Tests")
class JdbcWorkshopAuthorizationAdapterTest {

    @Mock
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    private JdbcWorkshopAuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JdbcWorkshopAuthorizationAdapter(namedJdbcTemplate);
    }

    @Test
    @DisplayName("filterAuthorizedUserIds should return empty set immediately when userIds is null or empty")
    void filterAuthorizedUserIds_shouldReturnEmptyWhenUserIdsEmpty() {
        UUID workshopId = UUID.randomUUID();

        assertThat(adapter.filterAuthorizedUserIds(null, workshopId)).isEmpty();
        assertThat(adapter.filterAuthorizedUserIds(Collections.emptySet(), workshopId)).isEmpty();
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @DisplayName("filterAuthorizedUserIds should return empty set immediately when workshopId is null")
    void filterAuthorizedUserIds_shouldReturnEmptyWhenWorkshopIdNull() {
        UUID userId = UUID.randomUUID();

        assertThat(adapter.filterAuthorizedUserIds(Set.of(userId), null)).isEmpty();
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @DisplayName("filterAuthorizedUserIds should query database and return matching authorized user IDs")
    @SuppressWarnings("unchecked")
    void filterAuthorizedUserIds_shouldQueryDatabaseAndReturnAuthorizedIds() {
        UUID workshopId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();

        when(namedJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(user1, user2));

        Set<UUID> result = adapter.filterAuthorizedUserIds(Set.of(user1, user2, user3), workshopId);

        assertThat(result).containsExactlyInAnyOrder(user1, user2);

        ArgumentCaptor<Map<String, Object>> paramCaptor = ArgumentCaptor.forClass(Map.class);
        verify(namedJdbcTemplate).query(anyString(), paramCaptor.capture(), any(RowMapper.class));

        Map<String, Object> params = paramCaptor.getValue();
        assertThat(params.get("workshopId")).isEqualTo(workshopId);
        assertThat((Set<UUID>) params.get("userIds")).containsExactlyInAnyOrder(user1, user2, user3);
    }

    @Test
    @DisplayName("filterAuthorizedUserIds should fail-closed and return empty set when database throws exception")
    @SuppressWarnings("unchecked")
    void filterAuthorizedUserIds_shouldFailClosedOnDatabaseException() {
        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(namedJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenThrow(new DataAccessException("Database connection timeout") {
                });

        Set<UUID> result = adapter.filterAuthorizedUserIds(Set.of(userId), workshopId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isUserAuthorizedForWorkshop default method should delegate to filterAuthorizedUserIds")
    @SuppressWarnings("unchecked")
    void isUserAuthorizedForWorkshop_shouldDelegateToBatchFilter() {
        UUID workshopId = UUID.randomUUID();
        UUID authorizedUser = UUID.randomUUID();
        UUID unauthorizedUser = UUID.randomUUID();

        when(namedJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(authorizedUser))
                .thenReturn(Collections.emptyList());

        assertThat(adapter.isUserAuthorizedForWorkshop(authorizedUser, workshopId)).isTrue();
        assertThat(adapter.isUserAuthorizedForWorkshop(unauthorizedUser, workshopId)).isFalse();
        assertThat(adapter.isUserAuthorizedForWorkshop(null, workshopId)).isFalse();
        assertThat(adapter.isUserAuthorizedForWorkshop(authorizedUser, null)).isFalse();
    }
}
