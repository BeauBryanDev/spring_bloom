package com.springbloom.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.FlowerOrderSummary;
import com.springbloom.domain.port.in.ListOrdersUseCase;
import com.springbloom.domain.port.out.FlowerOrderRepository;

@Service
public class ListOrdersService implements ListOrdersUseCase {

    private final FlowerOrderRepository orderRepository;

    public ListOrdersService(FlowerOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowerOrderSummary> listAll() {
        return orderRepository.findAllSummaries();
    }
}
