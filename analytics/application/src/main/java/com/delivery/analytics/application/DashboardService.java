package com.delivery.analytics.application;
import com.delivery.analytics.applicationapi.*;
import com.delivery.analytics.domain.DashboardValues;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
public final class DashboardService implements DashboardUseCase {
    private final DashboardReadPort read;
    private final Supplier<LocalDate> today;
    public DashboardService(DashboardReadPort read, Supplier<LocalDate> today) {this.read=read;this.today=today;}
    public Dashboard admin(String period, Integer year) { return dashboard(null,period,year,true); }
    public Dashboard restaurant(Long id, String period, Integer year) { return dashboard(id,period,year,false); }
    private Dashboard dashboard(Long id, String period, Integer year, boolean admin) {
        int targetYear=year!=null?year:today.get().getYear();
        var scope=new DashboardReadPort.Scope(admin,id);
        var overview=DashboardValues.overview(read.totals(scope));
        var series=switch(period!=null?period:"month") {
            case "quarter" -> DashboardValues.quarterly(read.monthly(scope,targetYear));
            case "year" -> DashboardValues.yearly(read.yearly(scope));
            default -> DashboardValues.monthly(read.monthly(scope,targetYear));
        };
        // Keep the second read: concurrent changes between overview and breakdown were observable.
        var statuses=DashboardValues.statuses(read.totals(scope));
        var top=admin?read.topRestaurants(10):List.<DashboardValues.TopRestaurant>of();
        return new Dashboard(overview,series,statuses,top);
    }
}
