package com.unihub.workshop.registrations.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkshopRegistrationCountAdapterTest {

    private EntityManager entityManager;
    private WorkshopRegistrationCountAdapter adapter;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        adapter = new WorkshopRegistrationCountAdapter(entityManager);
    }

    @Test
    @DisplayName(
        "Returns active registration count when query returns a number"
    )
    void shouldReturnActiveRegistrationCount() {
        UUID workshopId = UUID.randomUUID();
        Query query = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("workshopId", workshopId)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(42L);

        long count = adapter.countActiveRegistrations(workshopId);

        assertThat(count).isEqualTo(42L);
        verify(query).setParameter("workshopId", workshopId);
    }

    @Test
    @DisplayName("Returns 0 when workshopId is null")
    void shouldReturnZeroWhenWorkshopIdIsNull() {
        long count = adapter.countActiveRegistrations(null);

        assertThat(count).isZero();
    }
}
