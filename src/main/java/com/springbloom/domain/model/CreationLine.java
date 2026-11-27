package com.springbloom.domain.model;

import com.springbloom.domain.model.vo.Money;

/**
 * The arrangement half of a quotation or order line, snapshotted at quote time
 * like species prices are, so an old document keeps its name and price.
 */
public record CreationLine(Long creationId, 
                            String nameSnapshot, 
                            int units, 
                            Money unitPriceSnapshot) {

    public CreationLine {
        if (creationId == null) {
            throw new IllegalArgumentException("creationId is required");
        }
        if (nameSnapshot == null || nameSnapshot.isBlank()) {
            throw new IllegalArgumentException("nameSnapshot is required");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive: " + units);
        }
        if (unitPriceSnapshot == null) {
            throw new IllegalArgumentException("unitPriceSnapshot is required");
        }
    }

    public static CreationLine of(Creation creation, int units) {
        if (creation == null) {
            throw new IllegalArgumentException("creation is required");
        }
        return new CreationLine(creation.id(), 
                                creation.name(), 
                                units, 
                                creation.price());
    }

    public Money total() {
        return unitPriceSnapshot.multiply(units);
    }
}
