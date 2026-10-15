package com.unihub.workshop.payments.infrastructure.persistence.mapper;

import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.infrastructure.persistence.PaymentJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentPersistenceMapper {
    Payment toDomain(PaymentJpaEntity entity);

    PaymentJpaEntity toEntity(Payment domain);

    List<Payment> toDomainList(List<PaymentJpaEntity> entities);
}
