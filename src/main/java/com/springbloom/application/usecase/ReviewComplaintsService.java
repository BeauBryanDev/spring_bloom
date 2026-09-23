package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintStatus;
import com.springbloom.domain.port.in.ReviewComplaintsUseCase;
import com.springbloom.domain.port.out.ComplaintRepository;

/** TIMESTAMPTZ keeps microseconds, so resolvedAt is truncated like every other document date. */
@Service
public class ReviewComplaintsService implements ReviewComplaintsUseCase {

    private final ComplaintRepository complaintRepository;
    private final Clock storeClock;

    public ReviewComplaintsService(ComplaintRepository complaintRepository, Clock storeClock) {
        this.complaintRepository = complaintRepository;
        this.storeClock = storeClock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> listAll() {
        return complaintRepository.findAll();
    }

    @Override
    @Transactional
    public Complaint takeIntoReview(String complaintNumber) {
        Complaint complaint = find(complaintNumber);
        return complaintRepository.save(complaint.takenIntoReview());
    }

    @Override
    @Transactional
    public Complaint resolve(String complaintNumber, ComplaintStatus outcome, String resolution) {
        Complaint complaint = find(complaintNumber);
        Complaint closed = complaint.closedAs(
                outcome, resolution, storeClock.instant().truncatedTo(ChronoUnit.MICROS));
        return complaintRepository.save(closed);
    }

    private Complaint find(String complaintNumber) {
        return complaintRepository.findByNumber(complaintNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No complaint with number " + complaintNumber));
    }
}
