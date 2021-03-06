package org.onebusaway.nyc.presentation.comparator;

import java.util.Comparator;

import org.onebusaway.transit_data.model.RouteBean;

public class RouteComparator implements Comparator<RouteBean> {
	
	private Comparator<String> alphaNumComparator = new AlphanumComparator();

    @Override
    public int compare(RouteBean t, RouteBean t1) {
        if (t.getShortName() != null && t1.getShortName() != null) {
        	return alphaNumComparator.compare(t.getShortName(), t1.getShortName());
        } else {
            return t.getId().compareTo(t1.getId());
        }
    }
}