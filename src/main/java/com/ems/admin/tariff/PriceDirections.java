package com.ems.admin.tariff;

import com.ems.admin.tariff.entity.EmsMarketPrice;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class PriceDirections {
    private PriceDirections() {
    }

    public static String preferred(String product, boolean buy) {
        if (EmsMarketPrice.REAL_TIME.equals(product)) {
            return buy ? EmsMarketPrice.DOWN : EmsMarketPrice.UP;
        }
        return EmsMarketPrice.SINGLE;
    }

    public static String choose(Collection<String> available, String preferred) {
        Set<String> present = new HashSet<>();
        if (available != null) {
            for (String direction : available) {
                present.add(normalize(direction));
            }
        }
        if (present.contains(preferred)) {
            return preferred;
        }
        if (present.size() == 1) {
            return present.iterator().next();
        }
        if (present.contains(EmsMarketPrice.SINGLE)) {
            return EmsMarketPrice.SINGLE;
        }
        return preferred;
    }

    public static String normalize(String direction) {
        return direction == null || direction.isBlank() ? EmsMarketPrice.SINGLE : direction;
    }
}
