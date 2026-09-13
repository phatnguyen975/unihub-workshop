package com.unihub.workshop.shared.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcWorkshopAuthorizationAdapterTest {

    @Mock
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @Captor
    private ArgumentCaptor<Map<String, Object>> paramCaptor;

    private JdbcWorkshopAuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JdbcWorkshopAuthorizationAdapter(namedJdbcTemplate);
    }

    @Test
    @DisplayName(
        "findAuthorizedUserIds should return empty set immediately when workshopId is null"
    )
    void findAuthorizedUserIds_shouldReturnEmptyWhenWorkshopIdNull() {
        assertThat(adapter.findAuthorizedUserIds(null)).isEmpty();
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @DisplayName(
        "findAuthorizedUserIds should query database with workshopId and return matching authorized user IDs"
    )
    void findAuthorizedUserIds_shouldQueryDatabaseAndReturnAuthorizedIds() {
        UUID workshopId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                anyMap(),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(user1, user2));

        Set<UUID> result = adapter.findAuthorizedUserIds(workshopId);

        assertThat(result).containsExactlyInAnyOrder(user1, user2);

        verify(namedJdbcTemplate).query(
            anyString(),
            paramCaptor.capture(),
            ArgumentMatchers.<RowMapper<UUID>>any()
        );

        Map<String, Object> params = paramCaptor.getValue();
        assertThat(params.get("workshopId")).isEqualTo(workshopId);
        assertThat(params).doesNotContainKey("userIds");
    }

    @Test
    @DisplayName(
        "findAuthorizedUserIds should fail-closed and return empty set when database throws exception"
    )
    void findAuthorizedUserIds_shouldFailClosedOnDatabaseException() {
        UUID workshopId = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                anyMap(),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenThrow(new DataAccessException("Database connection timeout") {});

        Set<UUID> result = adapter.findAuthorizedUserIds(workshopId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
        "filterAuthorizedUserIds default method should delegate to findAuthorizedUserIds and retain intersection"
    )
    void filterAuthorizedUserIds_shouldDelegateAndRetainIntersection() {
        UUID workshopId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                anyMap(),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(user1, user2));

        Set<UUID> result = adapter.filterAuthorizedUserIds(
            Set.of(user1, user3),
            workshopId
        );

        assertThat(result).containsExactly(user1);
    }

    @Test
    @DisplayName(
        "isUserAuthorizedForWorkshop default method should delegate to findAuthorizedUserIds"
    )
    void isUserAuthorizedForWorkshop_shouldDelegateToFindAuthorizedUserIds() {
        UUID workshopId = UUID.randomUUID();
        UUID authorizedUser = UUID.randomUUID();
        UUID unauthorizedUser = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                anyMap(),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        )
            .thenReturn(List.of(authorizedUser))
            .thenReturn(Collections.emptyList());

        assertThat(
            adapter.isUserAuthorizedForWorkshop(authorizedUser, workshopId)
        ).isTrue();
        assertThat(
            adapter.isUserAuthorizedForWorkshop(unauthorizedUser, workshopId)
        ).isFalse();
        assertThat(
            adapter.isUserAuthorizedForWorkshop(null, workshopId)
        ).isFalse();
        assertThat(
            adapter.isUserAuthorizedForWorkshop(authorizedUser, null)
        ).isFalse();
    }
}
