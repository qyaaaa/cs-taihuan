package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.Outcome;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.List;
import java.util.Locale;

public final class Renderer {
    private Renderer() {
    }

    public static String renderPlans(List<TradeUpPlan> plans) {
        if (plans.isEmpty()) {
            return "No valid trade-up plans found. Check your inventory and catalog coverage.";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < plans.size(); i++) {
            TradeUpPlan plan = plans.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append('[').append(i + 1).append("] rarity=").append(plan.getRarity())
                .append(" profit=").append(format2(plan.getExpectedProfit()))
                .append(" roi=").append(String.format(Locale.US, "%.2f%%", plan.getRoi() * 100.0d))
                .append('\n');
            builder.append("    input_cost=").append(format2(plan.getInputCost()))
                .append(" expected_output=").append(format2(plan.getExpectedOutputValue()))
                .append(" avg_float=").append(String.format(Locale.US, "%.6f", plan.getAverageInputFloat()))
                .append('\n');
            builder.append("    inputs=");
            for (int j = 0; j < plan.getInputs().size(); j++) {
                BuffItem item = plan.getInputs().get(j);
                if (j > 0) {
                    builder.append(", ");
                }
                builder.append(item.getName()).append('(').append(format2(item.getPrice())).append(')');
            }
            builder.append('\n');
            builder.append("    outcomes=");
            for (int j = 0; j < plan.getOutcomes().size(); j++) {
                Outcome row = plan.getOutcomes().get(j);
                if (j > 0) {
                    builder.append(", ");
                }
                builder.append(row.getSkin().getName())
                    .append(" prob=").append(String.format(Locale.US, "%.2f%%", row.getProbability() * 100.0d))
                    .append(" sale=").append(format2(row.getEstimatedSalePrice()))
                    .append(" float=").append(String.format(Locale.US, "%.6f", row.getEstimatedFloat()));
            }
        }
        return builder.toString();
    }

    private static String format2(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}

