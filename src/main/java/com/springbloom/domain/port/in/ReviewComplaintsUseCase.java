package com.springbloom.domain.port.in;

import java.util.List;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintStatus;

/**
 * The admin side of a claim Florabelle filed. She may record one; only the
 * shop, through this, may take it into review or close it - see Complaint's
 * own javadoc.
 */
public interface ReviewComplaintsUseCase {

    List<Complaint> listAll();

    Complaint takeIntoReview(String complaintNumber);

    /** outcome must be RESOLVED or REJECTED; Complaint itself enforces that. */
    Complaint resolve(String complaintNumber, ComplaintStatus outcome, String resolution);
}
