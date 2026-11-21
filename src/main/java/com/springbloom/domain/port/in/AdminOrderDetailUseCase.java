package com.springbloom.domain.port.in;

import java.util.Optional;

import com.springbloom.domain.model.AdminOrderDetail;

public interface AdminOrderDetailUseCase {

    Optional<AdminOrderDetail> findByNumber(String orderNumber);
}
